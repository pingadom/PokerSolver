package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Carries a completed, heads-up six-seat preflop pot into the bounded three-street game. The caller
 * supplies per-combo likelihoods for the observed preflop action history; this class does not infer
 * a strategy from showdown equity or claim those likelihoods are solved.
 */
public final class PreflopFlopTransition {
    private static final double FLOPS_PER_DEAL = 17_296; // Choose 3 from 48 undealt cards.
    private static final List<Seat> POSTFLOP_ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);

    /** Every prior combo must have a likelihood in [0, 1]; zero removes the combo. */
    public record ActionConditioning(
            Seat seat,
            String source,
            List<SixMaxPreflopBetting.Move> observedActions,
            List<WeightedCombo> prior,
            Map<String, Double> observedHistoryLikelihood) {
        public ActionConditioning {
            Objects.requireNonNull(seat, "seat");
            if (source == null || source.isBlank())
                throw new IllegalArgumentException("Action likelihoods need a named source");
            if (observedActions == null || observedActions.isEmpty())
                throw new IllegalArgumentException("Observed preflop actions are required");
            observedActions = List.copyOf(observedActions);
            if (observedActions.stream()
                    .anyMatch(
                            action ->
                                    action.seat() != seat
                                            || action.kind()
                                                    == SixMaxPreflopBetting.Kind.POST_SMALL_BLIND
                                            || action.kind()
                                                    == SixMaxPreflopBetting.Kind.POST_BIG_BLIND))
                throw new IllegalArgumentException("Only this seat's decisions may be conditioned");
            if (prior == null || prior.isEmpty())
                throw new IllegalArgumentException("A prior range is required");
            prior = List.copyOf(prior);
            observedHistoryLikelihood =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    observedHistoryLikelihood, "observedHistoryLikelihood"));
            Set<String> keys = new HashSet<>();
            for (WeightedCombo combo : prior)
                if (combo == null || !keys.add(combo.key()))
                    throw new IllegalArgumentException("Null or duplicate prior combo");
            if (!keys.equals(observedHistoryLikelihood.keySet()))
                throw new IllegalArgumentException("Supply one likelihood for every prior combo");
            for (double likelihood : observedHistoryLikelihood.values())
                if (!Double.isFinite(likelihood) || likelihood < 0 || likelihood > 1)
                    throw new IllegalArgumentException("Action likelihood must be in [0, 1]");
        }

        List<WeightedCombo> conditioned() {
            List<WeightedCombo> range = new ArrayList<>();
            for (WeightedCombo combo : prior) {
                double likelihood = observedHistoryLikelihood.get(combo.key());
                if (likelihood > 0) {
                    double weight = combo.weight() * likelihood;
                    if (!Double.isFinite(weight) || weight <= 0)
                        throw new IllegalArgumentException(
                                "Conditioned combo weight is not finite");
                    range.add(new WeightedCombo(combo.first(), combo.second(), weight));
                }
            }
            if (range.isEmpty())
                throw new IllegalArgumentException("Observed actions eliminate the entire range");
            return List.copyOf(range);
        }
    }

    public record FlopProjection(
            FlopTurnRiverSpot spot,
            double probability,
            List<SixMaxPreflopBetting.Move> preflopHistory,
            String firstActionSource,
            String secondActionSource) {}

    private final SixMaxPreflopBetting.State preflop;
    private final Seat firstSeat;
    private final Seat secondSeat;
    private final List<WeightedCombo> firstRange;
    private final List<WeightedCombo> secondRange;
    private final double remainingStackBb;
    private final double jointWeight;
    private final String firstActionSource;
    private final String secondActionSource;

    public PreflopFlopTransition(
            SixMaxPreflopBetting game,
            SixMaxPreflopBetting.State preflop,
            ActionConditioning first,
            ActionConditioning second) {
        Objects.requireNonNull(game, "game");
        this.preflop = Objects.requireNonNull(preflop, "preflop");
        game.legalActions(preflop); // Also rejects a state from another betting game.
        if (preflop.status() != SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                || preflop.liveSeats().size() != 2)
            throw new IllegalArgumentException("A completed heads-up non-all-in pot is required");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        List<Seat> live = preflop.liveSeats();
        firstSeat = POSTFLOP_ORDER.stream().filter(live::contains).findFirst().orElseThrow();
        secondSeat = live.get(0) == firstSeat ? live.get(1) : live.get(0);
        if (first.seat() != firstSeat || second.seat() != secondSeat)
            throw new IllegalArgumentException("Ranges must follow postflop action order");
        verifyObservedActions(preflop, first);
        verifyObservedActions(preflop, second);
        if (preflop.committedBb(firstSeat) != preflop.committedBb(secondSeat))
            throw new IllegalArgumentException("Unequal live commitments need side-pot accounting");
        remainingStackBb = game.rules().stackBb() - preflop.committedBb(firstSeat);
        if (remainingStackBb <= 0)
            throw new IllegalArgumentException("Both players must retain a stack");
        firstRange = first.conditioned();
        secondRange = second.conditioned();
        firstActionSource = first.source();
        secondActionSource = second.source();
        double weight = 0;
        for (WeightedCombo firstCombo : firstRange)
            for (WeightedCombo secondCombo : secondRange)
                if (!firstCombo.conflictsWith(secondCombo))
                    weight += firstCombo.weight() * secondCombo.weight();
        if (!Double.isFinite(weight) || weight <= 0)
            throw new IllegalArgumentException("Conditioned ranges have no legal joint deals");
        jointWeight = weight;
    }

    public Seat firstToAct() {
        return firstSeat;
    }

    public Seat secondToAct() {
        return secondSeat;
    }

    public double potBb() {
        return preflop.potBb();
    }

    public double remainingStackBb() {
        return remainingStackBb;
    }

    /** Probability of this unordered flop under the conditioned, blocker-aware joint deal. */
    public double flopProbability(List<Card> flop) {
        validateFlop(flop);
        double compatibleWeight = 0;
        for (WeightedCombo first : firstRange) {
            if (blocked(flop, first)) continue;
            for (WeightedCombo second : secondRange)
                if (!first.conflictsWith(second) && !blocked(flop, second))
                    compatibleWeight += first.weight() * second.weight();
        }
        return compatibleWeight / jointWeight / FLOPS_PER_DEAL;
    }

    /**
     * Builds a full-turn-deck flop game conditioned on this public flop. A zero-probability flop
     * cannot be projected. The returned spot remains a research model until the input action
     * likelihoods and continuation bet sizes are validated.
     */
    public FlopProjection project(
            List<Card> flop, double flopBetBb, double turnBetBb, double riverBetBb) {
        double probability = flopProbability(flop);
        if (probability <= 0)
            throw new IllegalArgumentException("Flop is impossible under the conditioned ranges");
        List<WeightedCombo> availableFirst =
                firstRange.stream().filter(combo -> !blocked(flop, combo)).toList();
        List<WeightedCombo> availableSecond =
                secondRange.stream().filter(combo -> !blocked(flop, combo)).toList();
        List<WeightedCombo> usableFirst =
                availableFirst.stream()
                        .filter(
                                first ->
                                        availableSecond.stream()
                                                .anyMatch(second -> !first.conflictsWith(second)))
                        .toList();
        List<WeightedCombo> usableSecond =
                availableSecond.stream()
                        .filter(
                                second ->
                                        usableFirst.stream()
                                                .anyMatch(first -> !first.conflictsWith(second)))
                        .toList();
        FlopTurnRiverSpot spot =
                new FlopTurnRiverSpot(
                        flop,
                        preflop.potBb(),
                        remainingStackBb,
                        flopBetBb,
                        turnBetBb,
                        riverBetBb,
                        usableFirst,
                        usableSecond,
                        fullTurnCandidates(flop));
        return new FlopProjection(
                spot, probability, preflop.history(), firstActionSource, secondActionSource);
    }

    private static void verifyObservedActions(
            SixMaxPreflopBetting.State state, ActionConditioning conditioning) {
        List<SixMaxPreflopBetting.Move> actual =
                state.history().stream()
                        .filter(
                                action ->
                                        action.seat() == conditioning.seat()
                                                && action.kind()
                                                        != SixMaxPreflopBetting.Kind
                                                                .POST_SMALL_BLIND
                                                && action.kind()
                                                        != SixMaxPreflopBetting.Kind.POST_BIG_BLIND)
                        .toList();
        if (!actual.equals(conditioning.observedActions()))
            throw new IllegalArgumentException(
                    "Conditioned actions do not match the completed preflop history");
    }

    private static List<Card> fullTurnCandidates(List<Card> flop) {
        Deck deck = new Deck();
        flop.forEach(deck::remove);
        return deck.cards();
    }

    private static boolean blocked(List<Card> flop, WeightedCombo combo) {
        return flop.contains(combo.first()) || flop.contains(combo.second());
    }

    private static void validateFlop(List<Card> flop) {
        if (flop == null
                || flop.size() != 3
                || flop.stream().anyMatch(Objects::isNull)
                || new HashSet<>(flop).size() != 3)
            throw new IllegalArgumentException("Flop needs three distinct cards");
    }
}
