package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPreflopContinuationFeedbackTest {
    @Test
    void updatesAllPreflopSupportPreservesPostflopAndReconditionsTheAudit() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var pre =
                new LinkedHashMap<>(
                        SixMaxConditionalPostflopRefinementTest.uniform(game.source()).strategy());
        for (var root : game.source().chanceOutcomes(game.source().initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            String choice = state.dealIndex() == 0 ? "fold" : "call";
            for (var action : game.source().legalActions(state))
                weights.put(action, action.equals(choice) ? 1.0 : 0.0);
            pre.put(
                    game.source().currentPlayer(state) + ":" + game.source().informationSet(state),
                    weights);
        }
        var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, new CfrSolution(73, pre));
        var hash = SixMaxConnectedPostflopAudit.solutionHash(original);
        var result =
                SixMaxPreflopContinuationFeedback.solve(
                        game, original, 4, SixMaxContinuationStudyBudget.standard());
        var report = result.report();
        assertEquals(73, result.candidate().iterations());
        assertEquals(4, report.preflopIterations());
        assertEquals("CFR_PLUS", report.preflopAlgorithm());
        assertEquals("EXHAUSTIVE", report.preflopChanceTraversal());
        assertEquals(hash, report.originalSolutionHash());
        assertEquals(hash, SixMaxConnectedPostflopAudit.solutionHash(original));
        assertNotEquals(hash, report.candidateSolutionHash());
        assertEquals(original.strategy().keySet(), result.candidate().strategy().keySet());
        for (var row : original.strategy().entrySet())
            if (row.getKey().contains(":postflop:"))
                assertEquals(row.getValue(), result.candidate().strategy().get(row.getKey()));
        assertEquals(
                original.strategy().size(),
                report.replacedPreflopInformationSets()
                        + report.preservedPostflopInformationSets());
        assertTrue(report.maximumPreflopActionFrequencyChange() > 0);
        assertEquals(1, report.originalConditionalPostflop().getFirst().posteriorJointDeals());
        assertEquals(2, report.candidateConditionalPostflop().getFirst().posteriorJointDeals());
        assertNotEquals(
                report.originalReach().selectedHistoryProbability(),
                report.candidateReach().selectedHistoryProbability());
        assertEquals(
                SixMaxReachedContinuationStudy.reach(game, result.candidate()),
                report.candidateReach());
        assertEquals(
                SixMaxConnectedPreflopAudit.conditionalPostflop(game, result.candidate()),
                report.candidateConditionalPostflop());
        for (int seat = 0; seat < 6; seat++) {
            assertEquals(
                    report.originalParentQuality().profileUtilitiesBb().get(seat),
                    report.originalProjectedQuality().profileUtilitiesBb().get(seat),
                    1e-12);
            assertEquals(
                    report.candidateParentQuality().profileUtilitiesBb().get(seat),
                    report.candidateProjectedQuality().profileUtilitiesBb().get(seat),
                    1e-12);
            assertTrue(
                    report.candidateProjectedQuality().deviationGainsBb().get(seat)
                            <= report.candidateParentQuality().deviationGainsBb().get(seat)
                                    + 1e-12);
        }
        assertEquals(
                report.parentNashConvChangeBb() <= 1e-9, report.parentNashConvDidNotIncrease());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(game, result.candidate(), 2_000_000)
                        .addedInformationSets());
    }

    @Test
    void revivesUnreachedHistoriesUsingTheExplicitCounterfactualPostflopPolicy() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var rows =
                new LinkedHashMap<>(
                        MultiPlayerStrategyCompletion.uniformAtUnseen(
                                        game, new CfrSolution(1, Map.of()), 2_000_000)
                                .solution()
                                .strategy());
        for (var root : game.source().chanceOutcomes(game.source().initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            for (var action : game.source().legalActions(state))
                weights.put(action, action.equals("raise:100.0") ? 1.0 : 0.0);
            rows.put(
                    game.source().currentPlayer(state) + ":" + game.source().informationSet(state),
                    weights);
        }
        var original = new CfrSolution(1, rows);
        var result =
                SixMaxPreflopContinuationFeedback.solve(
                        game, original, 1, SixMaxContinuationStudyBudget.standard());
        assertEquals(0, result.report().originalReach().selectedHistoryProbability());
        assertTrue(result.report().originalConditionalPostflop().isEmpty());
        assertTrue(result.report().candidateReach().selectedHistoryProbability() > 0);
        assertEquals(1, result.report().candidateConditionalPostflop().size());
        assertEquals(2, result.report().frozenTerminalValues().size());
        for (var row : original.strategy().entrySet())
            if (row.getKey().contains(":postflop:"))
                assertEquals(row.getValue(), result.candidate().strategy().get(row.getKey()));
    }

    @Test
    void refusesInvalidBudgetsAndMissingOrForeignReplacementSupport() {
        var game = SixMaxConnectedPreflopGameTest.game();
        for (int iterations : new int[] {0, 3001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxPreflopContinuationFeedback.solve(
                                    game,
                                    new CfrSolution(1, Map.of()),
                                    iterations,
                                    SixMaxContinuationStudyBudget.standard()));
        var pre = SixMaxConditionalPostflopRefinementTest.uniform(game.source());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopContinuationFeedback.liftPreflop(
                                pre, new CfrSolution(1, Map.of())));
        var extra = new LinkedHashMap<>(pre.strategy());
        extra.put("0:foreign", Map.of("fold", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopContinuationFeedback.liftPreflop(
                                pre, new CfrSolution(1, extra)));
    }
}
