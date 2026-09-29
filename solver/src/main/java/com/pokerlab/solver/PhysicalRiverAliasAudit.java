package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Finds river observations that hide opposite bet/check incentives in a fixed call-or-check
 * counterfactual. This is an information-loss witness, not an equilibrium or exploitability test.
 */
public final class PhysicalRiverAliasAudit {
    public record Report(
            ButtonBigBlindRangeValidationFixture.RangeProfile rangeProfile,
            long seed,
            int sampledBoards,
            int comparedBoards,
            int fineBuckets,
            int textureBuckets,
            int coarseBuckets,
            int equityBuckets,
            int fineConflictedBuckets,
            int textureConflictedBuckets,
            int coarseConflictedBuckets,
            int equityConflictedBuckets,
            int fineConflictedBoards,
            int textureConflictedBoards,
            int coarseConflictedBoards,
            int equityConflictedBoards,
            double fineObservationLossBb,
            double textureObservationLossBb,
            double coarseObservationLossBb,
            double equityObservationLossBb) {
        public double fineConflictedRate() {
            return comparedBoards == 0 ? 0 : (double) fineConflictedBoards / comparedBoards;
        }

        public double coarseConflictedRate() {
            return comparedBoards == 0 ? 0 : (double) coarseConflictedBoards / comparedBoards;
        }

        public double textureConflictedRate() {
            return comparedBoards == 0 ? 0 : (double) textureConflictedBoards / comparedBoards;
        }

        public double equityConflictedRate() {
            return comparedBoards == 0 ? 0 : (double) equityConflictedBoards / comparedBoards;
        }
    }

    private static final class Group {
        int boards;
        boolean positive;
        boolean negative;
        double marginSum;

        void add(double margin) {
            boards++;
            positive |= margin > 0;
            negative |= margin < 0;
            marginSum += margin;
        }

        boolean conflicted() {
            return positive && negative;
        }
    }

    private PhysicalRiverAliasAudit() {}

    private static final double FIXTURE_RIVER_BET_BB = 8;

    public static Report assess(int sampledBoards, long seed) {
        return assess(
                sampledBoards,
                seed,
                ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3);
    }

    public static Report assess(
            int sampledBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile) {
        if (sampledBoards < 2 || sampledBoards > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 sampled boards");
        var fine =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.BOARD_BUCKETS, profile);
        var texture =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.TEXTURE_BOARD_BUCKETS,
                        profile);
        var coarse =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var equity =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        profile);
        var deals = fine.chanceOutcomes(fine.initialState());
        Map<String, Group> fineGroups = new HashMap<>();
        Map<String, Group> textureGroups = new HashMap<>();
        Map<String, Group> coarseGroups = new HashMap<>();
        Map<String, Group> equityGroups = new HashMap<>();
        SplittableRandom random = new SplittableRandom(seed);
        int compared = 0;
        double perfectMargin = 0;
        for (int attempt = 0; attempt < sampledBoards; attempt++) {
            var state = sampleRiverState(fine, random);
            double margin = calledBetMargin(state, deals);
            if (margin == 0) continue;
            compared++;
            perfectMargin += Math.max(0, margin);
            fineGroups.computeIfAbsent(fine.informationSet(state), key -> new Group()).add(margin);
            textureGroups
                    .computeIfAbsent(texture.informationSet(state), key -> new Group())
                    .add(margin);
            coarseGroups
                    .computeIfAbsent(coarse.informationSet(state), key -> new Group())
                    .add(margin);
            equityGroups
                    .computeIfAbsent(equity.informationSet(state), key -> new Group())
                    .add(margin);
        }
        return new Report(
                profile,
                seed,
                sampledBoards,
                compared,
                fineGroups.size(),
                textureGroups.size(),
                coarseGroups.size(),
                equityGroups.size(),
                conflictedBuckets(fineGroups),
                conflictedBuckets(textureGroups),
                conflictedBuckets(coarseGroups),
                conflictedBuckets(equityGroups),
                conflictedBoards(fineGroups),
                conflictedBoards(textureGroups),
                conflictedBoards(coarseGroups),
                conflictedBoards(equityGroups),
                observationLoss(fineGroups, perfectMargin, compared),
                observationLoss(textureGroups, perfectMargin, compared),
                observationLoss(coarseGroups, perfectMargin, compared),
                observationLoss(equityGroups, perfectMargin, compared));
    }

    /** Deals one physical hand and checks both players through flop and turn. */
    static ButtonBigBlindPhysicalDeckGame.State sampleRiverState(
            ButtonBigBlindPhysicalDeckGame game, SplittableRandom random) {
        var state = game.sampleChanceOutcome(game.initialState(), random.nextDouble()).state();
        state = game.afterAction(game.afterAction(state, "open3"), "call");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        return game.sampleChanceOutcome(state, random.nextDouble()).state();
    }

    /**
     * The BB checks and BTN checks back, or the BB bets and BTN always calls. The BB's incremental
     * showdown value per unit of river bet is its conditional win probability minus loss
     * probability. Ties have zero margin. Conditioning removes BTN combos blocked by the board.
     */
    static double calledBetMargin(
            ButtonBigBlindPhysicalDeckGame.State state,
            List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> deals) {
        if (state.river() == null || !state.riverHistory().isEmpty())
            throw new IllegalArgumentException("Expected first BB river decision");
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        int bbScore =
                HandEvaluator.evaluateBestScore(
                        state.bigBlind().first(),
                        state.bigBlind().second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        board.get(4));
        double weight = 0;
        double weightedMargin = 0;
        for (var outcome : deals) {
            var candidate = outcome.state();
            if (!candidate.bigBlind().equals(state.bigBlind())) continue;
            var button = candidate.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            int btnScore =
                    HandEvaluator.evaluateBestScore(
                            button.first(),
                            button.second(),
                            board.get(0),
                            board.get(1),
                            board.get(2),
                            board.get(3),
                            board.get(4));
            weightedMargin += outcome.probability() * Integer.compare(bbScore, btnScore);
            weight += outcome.probability();
        }
        if (weight == 0) throw new IllegalStateException("No legal opponent on sampled board");
        return weightedMargin / weight;
    }

    private static int conflictedBuckets(Map<String, Group> groups) {
        return (int) groups.values().stream().filter(Group::conflicted).count();
    }

    private static int conflictedBoards(Map<String, Group> groups) {
        return groups.values().stream()
                .filter(Group::conflicted)
                .mapToInt(group -> group.boards)
                .sum();
    }

    private static double observationLoss(
            Map<String, Group> groups, double perfectMargin, int compared) {
        if (compared == 0) return 0;
        double observedMargin =
                groups.values().stream().mapToDouble(group -> Math.max(0, group.marginSum)).sum();
        return Math.max(0, FIXTURE_RIVER_BET_BB * (perfectMargin - observedMargin) / compared);
    }
}
