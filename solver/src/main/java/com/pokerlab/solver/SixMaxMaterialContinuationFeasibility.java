package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optimistic content bound for a frozen preflop policy. Enumerates all physical flops for the most
 * frequent histories, ignores solve costs and pair diversity, and credits all unexamined reach.
 * Passing this bound is neither a continuation menu nor a strategy-quality/publication gate.
 */
public final class SixMaxMaterialContinuationFeasibility {
    public static final int BOARDS_PER_HISTORY = 22_100;
    public static final double NUMERIC_MARGIN = 1e-12;

    public record Settings(
            int candidateLimit,
            int maximumHistories,
            SixMaxRetainedContinuationCoverage.Settings coverage) {
        public Settings {
            if (candidateLimit < 1 || candidateLimit > 20)
                throw new IllegalArgumentException("Candidate limit must be 1–20");
            if (maximumHistories < 1 || maximumHistories > 4)
                throw new IllegalArgumentException("Maximum histories must be 1–4");
            Objects.requireNonNull(coverage, "coverage");
        }

        public static Settings researchDefault() {
            return new Settings(
                    20, 4, SixMaxRetainedContinuationCoverage.Settings.researchDefault());
        }
    }

    public record History(
            int reachRank,
            List<PublicAction> history,
            double probability,
            Seat firstToAct,
            Seat secondToAct,
            int optimisticMaterialFlops,
            Integer minimumCompatibleCounterfactualDeals,
            List<String> exampleFlop,
            boolean eligibleForUpperBound) {
        public History {
            history = List.copyOf(history);
            exampleFlop = List.copyOf(exampleFlop);
        }
    }

    public record Report(
            Settings settings,
            String status,
            int rootPrivateDeals,
            long policyValidationStates,
            int reachedHeadsUpHistories,
            double headsUpProbability,
            double unexaminedHistoryProbability,
            double optimisticSelectedHistoryProbability,
            double optimisticHeadsUpFraction,
            boolean numericReachUnresolved,
            int boardsEnumeratedPerHistory,
            double numericMargin,
            List<History> histories) {
        public Report {
            histories = List.copyOf(histories);
        }
    }

    private record Leaf(List<PublicAction> history, double probability) {}

    private static final class Reach {
        final Map<String, Leaf> headsUp = new LinkedHashMap<>();
        double terminalMass;
        boolean unresolved;
    }

    private SixMaxMaterialContinuationFeasibility() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame game, CfrSolution policy, Settings settings) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(settings, "settings");
        if (game.chanceModel() != SixMaxPreflopCheckdownGame.ChanceModel.EXACT_RANGE_PRODUCT)
            throw new IllegalArgumentException("Feasibility requires exact private range support");
        var roots = game.chanceOutcomes(game.initialState());
        if (roots.size() > SixMaxConnectedPreflopGame.MAX_PRIVATE_DEALS)
            throw new IllegalArgumentException("Feasibility supports at most 12 private deals");
        if (game.rakeRule().fraction() > 0 && game.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Applied postflop rake is not supported");
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, policy, SixMaxPreflopCheckdownGame.MAX_DEAL_PUBLIC_STATES + 1L);
        if (complete.addedInformationSets() != 0)
            throw new IllegalArgumentException("Feasibility requires a complete legal policy");
        var reach = new Reach();
        long[] rootMasks = new long[roots.size()];
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            rootMasks[i] = handMask(game.dealtHands(root.state()));
            if (root.probability() < Double.MIN_NORMAL) reach.unresolved = true;
            walk(game, policy, root.state(), root.probability(), List.of(), reach);
        }
        if (Math.abs(reach.terminalMass - 1) > 1e-8)
            throw new IllegalArgumentException("Terminal reach must sum to one");
        double headsUp = reach.headsUp.values().stream().mapToDouble(Leaf::probability).sum();
        var ranked =
                reach.headsUp.entrySet().stream()
                        .sorted(
                                Comparator.<Map.Entry<String, Leaf>>comparingDouble(
                                                e -> e.getValue().probability())
                                        .reversed()
                                        .thenComparing(Map.Entry::getKey))
                        .limit(settings.candidateLimit())
                        .toList();
        var histories = new ArrayList<History>();
        double examined = 0;
        for (var entry : ranked) {
            var leaf = entry.getValue();
            examined += leaf.probability();
            var transition = new SixMaxPolicyFlopTransition(game, policy, leaf.history());
            if (Math.abs(transition.reachProbability() - leaf.probability()) > 1e-10)
                throw new IllegalStateException("Posterior reach disagrees with tree traversal");
            histories.add(
                    enumerate(
                            transition,
                            rootMasks,
                            histories.size() + 1,
                            leaf.probability(),
                            settings,
                            reach));
        }
        double tail = Math.max(0, headsUp - examined);
        double upper =
                Math.min(
                        headsUp,
                        tail
                                + histories.stream()
                                        .filter(History::eligibleForUpperBound)
                                        .mapToDouble(History::probability)
                                        .boxed()
                                        .sorted(Comparator.reverseOrder())
                                        .limit(settings.maximumHistories())
                                        .mapToDouble(Double::doubleValue)
                                        .sum());
        double fraction = headsUp == 0 ? 0 : Math.min(1, upper / headsUp);
        String status =
                reach.unresolved
                        ? "NUMERIC_REACH_UNRESOLVED"
                        : headsUp == 0
                                ? "NO_HEADS_UP_REACH"
                                : fraction + NUMERIC_MARGIN
                                                < settings.coverage().minimumHeadsUpFraction()
                                        ? "INFEASIBLE_UNDER_FIXED_POLICY"
                                        : "NOT_RULED_OUT";
        return new Report(
                settings,
                status,
                roots.size(),
                complete.visitedStates(),
                reach.headsUp.size(),
                headsUp,
                tail,
                upper,
                fraction,
                reach.unresolved,
                BOARDS_PER_HISTORY,
                NUMERIC_MARGIN,
                histories);
    }

    private static History enumerate(
            SixMaxPolicyFlopTransition transition,
            long[] roots,
            int rank,
            double probability,
            Settings settings,
            Reach reach) {
        var deals = transition.deals();
        int n = deals.size();
        long[] masks = new long[n];
        int[] first = new int[n], second = new int[n];
        Map<String, Integer> firstIds = new LinkedHashMap<>(), secondIds = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            var deal = deals.get(i);
            if (deal.probability() < Double.MIN_NORMAL) reach.unresolved = true;
            masks[i] = handMask(deal.hands());
            first[i] =
                    firstIds.computeIfAbsent(
                            deal.hands().get(transition.firstToAct().ordinal()).key(),
                            k -> firstIds.size());
            second[i] =
                    secondIds.computeIfAbsent(
                            deal.hands().get(transition.secondToAct().ordinal()).key(),
                            k -> secondIds.size());
        }
        double[] firstMass = new double[firstIds.size()], secondMass = new double[secondIds.size()];
        var cards = new Deck().cards();
        int viable = 0, minimum = Integer.MAX_VALUE;
        List<String> example = List.of();
        for (int a = 0; a < 50; a++)
            for (int b = a + 1; b < 51; b++)
                for (int c = b + 1; c < 52; c++) {
                    long board = bit(cards.get(a)) | bit(cards.get(b)) | bit(cards.get(c));
                    Arrays.fill(firstMass, 0);
                    Arrays.fill(secondMass, 0);
                    double total = 0;
                    for (int d = 0; d < n; d++)
                        if ((masks[d] & board) == 0) {
                            double mass = deals.get(d).probability();
                            total += mass;
                            firstMass[first[d]] += mass;
                            secondMass[second[d]] += mass;
                        }
                    if (total > 0
                            && material(firstMass, total, settings.coverage())
                            && material(secondMass, total, settings.coverage())) {
                        viable++;
                        int compatible = 0;
                        for (long mask : roots) if ((mask & board) == 0) compatible++;
                        if (compatible < minimum) {
                            minimum = compatible;
                            example =
                                    List.of(
                                            cards.get(a).compact(),
                                            cards.get(b).compact(),
                                            cards.get(c).compact());
                        }
                    }
                }
        return new History(
                rank,
                transition.history(),
                probability,
                transition.firstToAct(),
                transition.secondToAct(),
                viable,
                viable == 0 ? null : minimum,
                example,
                viable > 0
                        && probability + NUMERIC_MARGIN
                                >= settings.coverage().minimumHistoryProbability());
    }

    private static boolean material(
            double[] masses, double total, SixMaxRetainedContinuationCoverage.Settings settings) {
        int count = 0;
        for (double mass : masses)
            if (mass > 0 && mass / total + NUMERIC_MARGIN >= settings.minimumComboMass()) count++;
        return count >= settings.minimumMaterialCombosPerActiveSeat();
    }

    private static long bit(Card card) {
        return 1L << (card.rank().ordinal() * 4 + card.suit().ordinal());
    }

    private static long handMask(List<WeightedCombo> hands) {
        long mask = 0;
        for (var hand : hands) mask |= bit(hand.first()) | bit(hand.second());
        return mask;
    }

    private static void walk(
            SixMaxPreflopCheckdownGame game,
            CfrSolution policy,
            SixMaxPreflopCheckdownGame.State state,
            double probability,
            List<PublicAction> history,
            Reach reach) {
        if (probability == 0) return;
        if (game.isTerminal(state)) {
            reach.terminalMass += probability;
            var publicState = game.publicBettingState(state);
            if (publicState.status() == SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                    && publicState.liveSeats().size() == 2)
                reach.headsUp.merge(
                        state.publicHistory(),
                        new Leaf(history, probability),
                        (a, b) -> new Leaf(a.history(), a.probability() + b.probability()));
            return;
        }
        for (String action : game.legalActions(state)) {
            double weight = MultiPlayerStrategyEvaluator.probability(game, policy, state, action);
            if (weight == 0) continue;
            double nextProbability = probability * weight;
            if (nextProbability < Double.MIN_NORMAL) reach.unresolved = true;
            if (nextProbability == 0) {
                reach.unresolved = true;
                continue;
            }
            var next = new ArrayList<>(history);
            next.add(new PublicAction(Seat.values()[game.currentPlayer(state)], action));
            walk(
                    game,
                    policy,
                    game.afterAction(state, action),
                    nextProbability,
                    List.copyOf(next),
                    reach);
        }
    }
}
