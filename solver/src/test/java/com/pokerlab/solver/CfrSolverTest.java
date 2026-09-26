package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CfrSolverTest {
    private static final List<String> FIRST_INFO_SETS =
            List.of("1:", "2:", "3:", "1:kb", "2:kb", "3:kb");
    private static final List<String> SECOND_INFO_SETS =
            List.of("1:k", "2:k", "3:k", "1:b", "2:b", "3:b");

    @Test
    void convergesTowardKuhnValueAndPureBestResponseBound() {
        KuhnPoker game = new KuhnPoker();
        CfrSolution solution = new CfrSolver<>(game).solve(30_000);
        double value = StrategyEvaluator.playerZeroUtility(game, solution);

        assertEquals(-1.0 / 18, value, 0.01);
        assertEquals(12, solution.strategy().size());
        for (Map<String, Double> actions : solution.strategy().values()) {
            assertEquals(1, actions.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
            assertTrue(actions.values().stream().allMatch(p -> p >= 0 && p <= 1));
        }

        double firstBest = Double.NEGATIVE_INFINITY;
        double secondBest = Double.POSITIVE_INFINITY;
        for (int mask = 0; mask < 64; mask++) {
            firstBest =
                    Math.max(
                            firstBest,
                            pureResponseValue(game, solution, 0, mask, game.initialState()));
            secondBest =
                    Math.min(
                            secondBest,
                            pureResponseValue(game, solution, 1, mask, game.initialState()));
        }
        double gap = firstBest - secondBest;
        assertTrue(gap < 0.02, () -> "Kuhn best-response gap: " + gap);
    }

    @Test
    void rejectsNonPositiveIterationCount() {
        CfrSolver<KuhnPoker.State> solver = new CfrSolver<>(new KuhnPoker());
        assertThrows(IllegalArgumentException.class, () -> solver.solve(0));
    }

    @Test
    void cfrPlusConvergesOnKuhnPoker() {
        KuhnPoker game = new KuhnPoker();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(3_000);
        double value = StrategyEvaluator.playerZeroUtility(game, solution);
        double gap = bestResponseGap(game, solution);
        assertEquals(-1.0 / 18, value, 0.01);
        assertTrue(gap < 0.001, () -> "Kuhn CFR+ best-response gap: " + gap);
        assertEquals(solution, new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(3_000));
    }

    @Test
    void seededChanceSamplingApproachesTheKuhnEquilibrium() {
        KuhnPoker game = new KuhnPoker();
        CfrSolver<KuhnPoker.State> solver =
                new CfrSolver<>(game, CfrSolver.Variant.VANILLA, CfrSolver.ChanceMode.SAMPLED, 42);
        CfrSolution solution = solver.solve(100_000);
        assertEquals(12, solution.strategy().size());
        assertEquals(-1.0 / 18, StrategyEvaluator.playerZeroUtility(game, solution), 0.01);
        assertTrue(bestResponseGap(game, solution) < 0.03);
        assertEquals(solution, solver.solve(100_000));
        assertNotEquals(
                solution,
                new CfrSolver<>(game, CfrSolver.Variant.VANILLA, CfrSolver.ChanceMode.SAMPLED, 7)
                        .solve(100_000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CfrSolver<>(
                                game,
                                CfrSolver.Variant.CFR_PLUS,
                                CfrSolver.ChanceMode.SAMPLED,
                                42));
    }

    @Test
    void samplingAfterRootMatchesExhaustiveKuhnWithNoLaterChance() {
        KuhnPoker game = new KuhnPoker();
        CfrSolution exhaustive = new CfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(3_000);
        CfrSolution afterRoot =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(3_000);
        assertEquals(exhaustive, afterRoot);
    }

    private static double bestResponseGap(KuhnPoker game, CfrSolution solution) {
        double firstBest = Double.NEGATIVE_INFINITY;
        double secondBest = Double.POSITIVE_INFINITY;
        for (int mask = 0; mask < 64; mask++) {
            firstBest =
                    Math.max(
                            firstBest,
                            pureResponseValue(game, solution, 0, mask, game.initialState()));
            secondBest =
                    Math.min(
                            secondBest,
                            pureResponseValue(game, solution, 1, mask, game.initialState()));
        }
        return firstBest - secondBest;
    }

    private static double pureResponseValue(
            KuhnPoker game,
            CfrSolution solution,
            int respondingPlayer,
            int mask,
            KuhnPoker.State state) {
        if (game.isTerminal(state)) return game.terminalUtility(state);
        int player = game.currentPlayer(state);
        if (player == -1) {
            double utility = 0;
            for (ChanceOutcome<KuhnPoker.State> outcome : game.chanceOutcomes(state)) {
                utility +=
                        outcome.probability()
                                * pureResponseValue(
                                        game, solution, respondingPlayer, mask, outcome.state());
            }
            return utility;
        }
        List<String> actions = game.legalActions(state);
        if (player == respondingPlayer) {
            List<String> sets = player == 0 ? FIRST_INFO_SETS : SECOND_INFO_SETS;
            int index = sets.indexOf(game.informationSet(state));
            assertTrue(index >= 0);
            String chosen = actions.get((mask >> index) & 1);
            return pureResponseValue(
                    game, solution, respondingPlayer, mask, game.afterAction(state, chosen));
        }
        double utility = 0;
        for (String action : actions) {
            utility +=
                    solution.at(player, game.informationSet(state)).get(action)
                            * pureResponseValue(
                                    game,
                                    solution,
                                    respondingPlayer,
                                    mask,
                                    game.afterAction(state, action));
        }
        return utility;
    }
}
