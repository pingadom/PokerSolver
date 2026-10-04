package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiPlayerChanceSamplingTest {
    record State(int hidden, String action, int outcome) {}

    /** Safe pays 1; rare pays 100 with probability .001, giving actual EV .1. */
    static class RareGame implements MultiPlayerCfrGame<State> {
        private final boolean chanceBeforeDecision;

        RareGame(boolean chanceBeforeDecision) {
            this.chanceBeforeDecision = chanceBeforeDecision;
        }

        public int playerCount() {
            return 6;
        }

        public State initialState() {
            return new State(-1, "", -1);
        }

        public boolean isTerminal(State s) {
            return s.action().equals("safe") || s.action().equals("rare") && s.outcome() >= 0;
        }

        public double[] terminalUtilities(State s) {
            double value = s.action().equals("safe") ? 1 : s.outcome() == 1 ? 100 : 0;
            return new double[] {value, -value, 0, 0, 0, 0};
        }

        public int currentPlayer(State s) {
            return s.hidden() < 0
                            || s.outcome() < 0
                                    && (chanceBeforeDecision || s.action().equals("rare"))
                    ? -1
                    : 0;
        }

        public List<String> legalActions(State s) {
            return List.of("safe", "rare");
        }

        public String informationSet(State s) {
            return "hero";
        }

        public State afterAction(State s, String a) {
            return new State(s.hidden(), a, s.outcome());
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            if (s.hidden() < 0)
                return List.of(
                        new ChanceOutcome<>(new State(0, "", -1), .3),
                        new ChanceOutcome<>(new State(1, "", -1), .7));
            return List.of(
                    new ChanceOutcome<>(new State(s.hidden(), s.action(), 0), .999),
                    new ChanceOutcome<>(new State(s.hidden(), s.action(), 1), .001));
        }

        public double chanceBaselineUtility(State s, int player) {
            return s.action().equals("rare") ? player == 0 ? .1 : player == 1 ? -.1 : 0 : 0;
        }
    }

    @Test
    void proposalOversamplesRareOutcomesWithoutChangingExpectedValue() {
        var outcomes = List.of(new ChanceOutcome<>(0, .999), new ChanceOutcome<>(1, .001));
        double commonQ = .5 * .999 + .25;
        var common = MultiPlayerCfrSolver.sample(outcomes, commonQ / 2, .5);
        var rare = MultiPlayerCfrSolver.sample(outcomes, commonQ + (1 - commonQ) / 2, .5);
        assertEquals(0, common.state());
        assertEquals(1, rare.state());
        assertEquals(.001 * 100, (1 - commonQ) * rare.importanceRatio() * 100, 1e-12);
        assertEquals(.999, commonQ * common.importanceRatio(), 1e-12);
        assertTrue(rare.importanceRatio() < .005);
    }

    @Test
    void prefixAndSuffixImportanceRecoverCorrectSixPlayerDecision() {
        for (boolean prefix : List.of(false, true)) {
            var game = new RareGame(prefix);
            for (var mode :
                    List.of(
                            MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                            MultiPlayerCfrSolver.ChanceMode.SAMPLED_AFTER_ROOT)) {
                var solver =
                        new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.VANILLA, mode, 711, .5);
                var solution = solver.solve(4000);
                assertTrue(solution.at(0, "hero").get("safe") > .99, solution.toString());
                var quality = MultiPlayerInformationSetBestResponse.assess(game, solution);
                assertTrue(quality.nashConvBb() < .01);
                assertEquals(6, quality.profileUtilitiesBb().size());
                var statistics = solver.statistics();
                assertTrue(statistics.sampledChanceNodes() > 0);
                assertEquals(solution, solver.solve(4000));
                assertEquals(statistics, solver.statistics());
            }
        }
    }

    @Test
    void exhaustiveDefaultRemainsIdenticalAndSamplingReducesTraversal() {
        var game = new RareGame(true);
        var legacy = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        var explicit =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.CFR_PLUS,
                        MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                        999,
                        0);
        assertEquals(legacy.solve(20), explicit.solve(20));
        assertEquals(legacy.statistics(), explicit.statistics());
        var sampled =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                        711,
                        0);
        sampled.solve(20);
        assertTrue(sampled.statistics().visitedNodes() < legacy.statistics().visitedNodes());
        assertEquals(0, legacy.statistics().sampledChanceNodes());
    }

    @Test
    void rejectsInvalidProposalsAndUnsupportedSampledPlus() {
        var game = new RareGame(false);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiPlayerCfrSolver<>(
                                game,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                                1,
                                .5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiPlayerCfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                1,
                                .5));
        for (double mixture : new double[] {-1, .951, Double.NaN})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new MultiPlayerCfrSolver<>(
                                    game,
                                    CfrSolver.Variant.VANILLA,
                                    MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                                    1,
                                    mixture));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPlayerCfrSolver.sample(List.of(), .5, .5));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPlayerCfrSolver.sample(List.of(new ChanceOutcome<>(0, .8)), .5, .5));
        for (double q : new double[] {-1, 1, Double.NaN})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MultiPlayerCfrSolver.sample(List.of(new ChanceOutcome<>(0, 1)), q, .5));
    }

    @Test
    void controlVariatePreservesExpectedValueAndCanBeDisabled() {
        var outcomes = List.of(new ChanceOutcome<>(0, .999), new ChanceOutcome<>(1, .001));
        double expected = 0;
        for (int i = 0; i < 2; i++) {
            double p = outcomes.get(i).probability();
            double q = .5 * p + .25;
            double quantile = i == 0 ? q / 2 : (.5 * .999 + .25) + q / 2;
            var sampled = MultiPlayerCfrSolver.sample(outcomes, quantile, .5);
            double u = sampled.state() == 1 ? 100 : 0;
            expected += q * (.1 + sampled.importanceRatio() * (u - .1));
        }
        assertEquals(.1, expected, 1e-12);
        var game = new RareGame(false);
        for (boolean enabled : List.of(false, true)) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.VANILLA,
                            MultiPlayerCfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                            711,
                            .5,
                            enabled);
            var policy = solver.solve(100);
            assertTrue(policy.at(0, "hero").get("safe") > .99);
            assertEquals(enabled, solver.statistics().baselineCorrections() > 0);
        }
    }

    @Test
    void rejectsNonfiniteBaselineWhenEnabled() {
        var bad =
                new RareGame(false) {
                    public double chanceBaselineUtility(State state, int player) {
                        return Double.NaN;
                    }
                };
        var enabled =
                new MultiPlayerCfrSolver<>(
                        bad,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                        711,
                        .5,
                        true);
        assertThrows(IllegalArgumentException.class, () -> enabled.solve(1));
        assertDoesNotThrow(
                () ->
                        new MultiPlayerCfrSolver<>(
                                        bad,
                                        CfrSolver.Variant.VANILLA,
                                        MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                                        711,
                                        .5,
                                        false)
                                .solve(1));
    }

    @Test
    void linearWeightingDiscountsInitialMistakeWithoutClippingRegrets() {
        var game = new RareGame(false);
        var ordinary = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.VANILLA);
        var linear =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                        711,
                        0,
                        true,
                        true);
        var uniformPolicy = ordinary.solve(100);
        var weightedPolicy = linear.solve(100);
        assertEquals(.995, uniformPolicy.at(0, "hero").get("safe"), 1e-12);
        assertEquals(1 - .5 / 5050, weightedPolicy.at(0, "hero").get("safe"), 1e-12);
        assertTrue(
                MultiPlayerInformationSetBestResponse.assess(game, weightedPolicy).nashConvBb()
                        < MultiPlayerInformationSetBestResponse.assess(game, uniformPolicy)
                                        .nashConvBb()
                                / 40);
        assertEquals(weightedPolicy, linear.solve(100));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiPlayerCfrSolver<>(
                                game,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                1,
                                0,
                                true,
                                true));
    }

    @Test
    void linearRegretsMatchIndependentFiveRoundMatrixCalculation() {
        record MatrixState(int first, int second) {}
        var game =
                new MultiPlayerCfrGame<MatrixState>() {
                    public int playerCount() {
                        return 6;
                    }

                    public MatrixState initialState() {
                        return new MatrixState(-1, -1);
                    }

                    public boolean isTerminal(MatrixState s) {
                        return s.second() >= 0;
                    }

                    public int currentPlayer(MatrixState s) {
                        return s.first() < 0 ? 0 : 1;
                    }

                    public List<String> legalActions(MatrixState s) {
                        return List.of("a", "b");
                    }

                    // Player 1 cannot observe player 0's simultaneous matrix action.
                    public String informationSet(MatrixState s) {
                        return "matrix";
                    }

                    public MatrixState afterAction(MatrixState s, String a) {
                        int action = a.equals("a") ? 0 : 1;
                        return s.first() < 0
                                ? new MatrixState(action, -1)
                                : new MatrixState(s.first(), action);
                    }

                    public List<ChanceOutcome<MatrixState>> chanceOutcomes(MatrixState s) {
                        return List.of();
                    }

                    public double[] terminalUtilities(MatrixState s) {
                        double[][] matrix = {{3, -1}, {-2, 1}};
                        double value = matrix[s.first()][s.second()];
                        return new double[] {value, -value, 0, 0, 0, 0};
                    }
                };
        var linear =
                new MultiPlayerCfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                711,
                                0,
                                true,
                                true)
                        .solve(5);
        // Independent alternating matrix regret-matching calculation: both regret increments
        // and average contributions receive weights 1, 2, 3, 4, 5. Averaging alone differs.
        assertEquals(.3847655423724438, linear.at(0, "matrix").get("a"), 1e-12);
        assertEquals(.42803841103584495, linear.at(1, "matrix").get("a"), 1e-12);
    }

    @Test
    void runoutSamplingEnumeratesTheFirstTwoChanceLayers() {
        var game = new RareGame(true);
        var exact =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                        711,
                        0,
                        true,
                        true);
        var phase =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS,
                        711,
                        .5,
                        true,
                        true);
        assertEquals(exact.solve(20), phase.solve(20));
        assertEquals(exact.statistics(), phase.statistics());
        assertEquals(0, phase.statistics().sampledChanceNodes());
    }

    @Test
    void completionIsExplicitBudgetedAndRejectsForeignOrInvalidPolicies() {
        var game = new RareGame(false);
        var empty = new CfrSolution(1, Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPlayerStrategyCompletion.uniformAtUnseen(game, empty, 1));
        var completed = MultiPlayerStrategyCompletion.uniformAtUnseen(game, empty, 100);
        assertEquals(1, completed.addedInformationSets());
        assertEquals(.5, completed.solution().at(0, "hero").get("safe"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game,
                                new CfrSolution(1, Map.of("0:alien", Map.of("safe", 1.0))),
                                100));
        for (var weights :
                List.of(
                        Map.of("safe", .4, "rare", .4),
                        Map.of("safe", Double.NaN, "rare", 0.0),
                        Map.of("safe", 1.0)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            MultiPlayerStrategyCompletion.uniformAtUnseen(
                                    game, new CfrSolution(1, Map.of("0:hero", weights)), 100));
    }
}
