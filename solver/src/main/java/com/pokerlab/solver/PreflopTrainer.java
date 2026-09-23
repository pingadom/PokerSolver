package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Builds and grades bounded preflop drills from an already generated solution pack. */
public final class PreflopTrainer {
    public enum Action {
        SHOVE,
        FOLD
    }

    /** Public state and the hero's exact cards; no opponent cards or answer data. */
    public record Question(
            String spotId,
            String spotHash,
            String publicationStatus,
            PreflopAllInSpot.Seat heroSeat,
            PreflopAllInSpot.Seat opponentSeat,
            String heroCombo,
            double effectiveStackBb,
            double smallBlindBb,
            String rakeModel,
            String payoffMethod,
            double estimatedGameGapBb,
            double maximumCalledPayoffStandardErrorBb,
            double potBb,
            double heroCommittedBb,
            double opponentCommittedBb,
            List<PreflopAllInSpot.Action> priorActions,
            List<Action> legalActions) {
        public Question {
            priorActions = List.copyOf(priorActions);
            legalActions = List.copyOf(legalActions);
        }
    }

    /** EVs are conditional on the displayed hero combo and the pack's opponent strategy. */
    public record Feedback(
            String spotHash,
            String heroCombo,
            Action selectedAction,
            double selectedEvBb,
            double bestEvBb,
            double evLossBb,
            double shoveFrequency,
            double foldFrequency,
            double shoveEvBb,
            double foldEvBb) {}

    private final PreflopSolutionPack pack;
    private final Map<String, Double> heroDealProbabilities;
    private final Map<String, PreflopSolutionPack.HeroDecision> decisions;

    public PreflopTrainer(PreflopSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        this.heroDealProbabilities = marginalHeroDealProbabilities(pack.spot());
        Map<String, PreflopSolutionPack.HeroDecision> byCombo = new LinkedHashMap<>();
        for (var decision : pack.heroDecisions()) byCombo.put(decision.combo(), decision);
        this.decisions = Map.copyOf(byCombo);
    }

    public Question question(long seed) {
        double draw = new SplittableRandom(seed).nextDouble();
        double cumulative = 0;
        String selected = null;
        for (var entry : heroDealProbabilities.entrySet()) {
            cumulative += entry.getValue();
            selected = entry.getKey();
            if (draw < cumulative) break;
        }
        PreflopAllInSpot spot = pack.spot();
        double heroCommitted = spot.firstCommittedBb();
        double opponentCommitted = spot.secondCommittedBb();
        return new Question(
                spot.id(),
                pack.spotHash(),
                pack.publicationStatus(),
                spot.firstSeat(),
                spot.secondSeat(),
                selected,
                spot.effectiveStackBb(),
                spot.smallBlindBb(),
                "NO_RAKE",
                pack.payoffMethod(),
                pack.estimatedGameGapBb(),
                pack.maximumCalledPayoffStandardErrorBb(),
                heroCommitted + opponentCommitted + spot.deadMoneyBb(),
                heroCommitted,
                opponentCommitted,
                spot.priorActions(),
                List.of(Action.SHOVE, Action.FOLD));
    }

    public Feedback grade(Question question, Action action) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(action, "action");
        if (!pack.spotHash().equals(question.spotHash())
                || !pack.spot().id().equals(question.spotId()))
            throw new IllegalArgumentException("Question does not belong to this spot version");
        PreflopSolutionPack.HeroDecision decision = decisions.get(question.heroCombo());
        if (decision == null) throw new IllegalArgumentException("Unknown hero combo");
        double selectedEv = action == Action.SHOVE ? decision.shoveEvBb() : decision.foldEvBb();
        double bestEv = Math.max(decision.shoveEvBb(), decision.foldEvBb());
        return new Feedback(
                pack.spotHash(),
                decision.combo(),
                action,
                selectedEv,
                bestEv,
                Math.max(0, bestEv - selectedEv),
                decision.shoveFrequency(),
                decision.foldFrequency(),
                decision.shoveEvBb(),
                decision.foldEvBb());
    }

    /** Chance-weighted marginal over the hero's exact combos after impossible deals are removed. */
    Map<String, Double> heroDealProbabilities() {
        return heroDealProbabilities;
    }

    private static Map<String, Double> marginalHeroDealProbabilities(PreflopAllInSpot spot) {
        List<Double> masses = new ArrayList<>();
        double total = 0;
        for (WeightedCombo hero : spot.firstRange()) {
            double compatibleOpponentWeight = 0;
            for (WeightedCombo opponent : spot.secondRange()) {
                if (!hero.conflictsWith(opponent)) compatibleOpponentWeight += opponent.weight();
            }
            double mass = hero.weight() * compatibleOpponentWeight;
            masses.add(mass);
            total += mass;
        }
        if (!Double.isFinite(total) || total <= 0)
            throw new IllegalArgumentException("Hero deal weights are not finite and positive");
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (int index = 0; index < masses.size(); index++) {
            probabilities.put(spot.firstRange().get(index).key(), masses.get(index) / total);
        }
        return java.util.Collections.unmodifiableMap(probabilities);
    }
}
