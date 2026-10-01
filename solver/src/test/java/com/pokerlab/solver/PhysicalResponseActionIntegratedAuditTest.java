package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalResponseActionIntegratedAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame GAME =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void integratesMixedActionsAndNonuniformPrivateDealWeightsExactly() {
        var deals = GAME.chanceOutcomes(GAME.initialState());
        var selectedButton = deals.get(0).state().button();
        Map<String, Map<String, Double>> baselinePolicies = new HashMap<>();
        for (var deal : deals) {
            baselinePolicies.put(
                    "1:P:BTN:" + deal.state().button().key(), Map.of("open3", 0.0, "fold", 1.0));
            baselinePolicies.put(
                    "0:P:BB:open3:" + deal.state().bigBlind().key(),
                    Map.of("call", 0.0, "fold", 1.0));
        }
        var responsePolicies = new HashMap<>(baselinePolicies);
        responsePolicies.put(
                "1:P:BTN:" + selectedButton.key(), Map.of("open3", 0.25, "fold", 0.75));
        var baseline = new CfrSolution(1, baselinePolicies);
        var response = new CfrSolution(1, responsePolicies);
        double exactGain = 0;
        for (var deal : deals) {
            if (!deal.state().button().equals(selectedButton)) continue;
            var folded = GAME.afterAction(deal.state(), "fold");
            var openedThenFolded =
                    GAME.afterAction(GAME.afterAction(deal.state(), "open3"), "fold");
            exactGain +=
                    0.25
                            * deal.probability()
                            * (GAME.terminalUtility(folded)
                                    - GAME.terminalUtility(openedThenFolded));
        }
        int traversals = deals.size() * 10;
        var integrated =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        response,
                        1,
                        traversals,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        assertEquals(exactGain, integrated.responseGainBb(), 1e-12);
        assertEquals(0, integrated.pairedStandardErrorBb(), 1e-12);
        assertEquals(10, integrated.independentBatches());
        assertEquals(0, integrated.baselineFallbackPathProbability(), 0);
        assertEquals(0, integrated.responseFallbackPathProbability(), 0);
        assertEquals(0, integrated.responseMissingPathProbability(), 0);
        assertEquals(integrated.responseGainBb(), integrated.completionGainLowerBb(), 1e-12);
        assertEquals(integrated.responseGainBb(), integrated.completionGainUpperBb(), 1e-12);
        assertEquals(
                integrated,
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        response,
                        1,
                        traversals,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT));
        var noOp =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        baseline,
                        1,
                        traversals,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT);
        assertEquals(0, noOp.responseGainBb(), 0);
        assertEquals(0, noOp.pairedStandardErrorBb(), 0);
    }

    @Test
    void rejectsIncompleteStratifiedBatches() {
        var empty = new CfrSolution(1, Map.of());
        int deals = GAME.chanceOutcomes(GAME.initialState()).size();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalResponseActionIntegratedAudit.assess(
                                GAME,
                                empty,
                                empty,
                                0,
                                2 * deals + 1,
                                1,
                                PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT));
    }

    @Test
    void missingResponseSupportBoundsEveryCompletionAtThatInformationSet() {
        var deals = GAME.chanceOutcomes(GAME.initialState());
        var selectedButton = deals.get(0).state().button();
        Map<String, Map<String, Double>> baselinePolicies = new HashMap<>();
        for (var deal : deals) {
            baselinePolicies.put(
                    "1:P:BTN:" + deal.state().button().key(), Map.of("open3", 0.0, "fold", 1.0));
            baselinePolicies.put(
                    "0:P:BB:open3:" + deal.state().bigBlind().key(),
                    Map.of("call", 0.0, "fold", 1.0));
        }
        var sparsePolicies = new HashMap<>(baselinePolicies);
        sparsePolicies.remove("1:P:BTN:" + selectedButton.key());
        var completedPolicies = new HashMap<>(sparsePolicies);
        completedPolicies.put("1:P:BTN:" + selectedButton.key(), Map.of("open3", 1.0, "fold", 0.0));
        var baseline = new CfrSolution(1, baselinePolicies);
        var sparse = new CfrSolution(1, sparsePolicies);
        var completed = new CfrSolution(1, completedPolicies);
        int traversals = deals.size() * 10;
        var fallback =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        sparse,
                        1,
                        traversals,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        var alternative =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        completed,
                        1,
                        traversals,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        double selectedMass =
                deals.stream()
                        .filter(deal -> deal.state().button().equals(selectedButton))
                        .mapToDouble(ChanceOutcome::probability)
                        .sum();
        assertEquals(selectedMass, fallback.responseMissingPathProbability(), 1e-12);
        assertEquals(0, fallback.responseGainBb(), 0);
        assertEquals(0, alternative.responseMissingPathProbability(), 0);
        assertTrue(alternative.responseGainBb() <= fallback.completionGainUpperBb());
        assertTrue(alternative.responseGainBb() >= fallback.completionGainLowerBb());
        assertTrue(fallback.boundedCompletionGainUpper95Bb() >= fallback.completionGainUpperBb());
        var moreBatches =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        sparse,
                        1,
                        deals.size() * 100,
                        73,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        assertTrue(
                moreBatches.boundedCompletionGainUpper95Bb()
                        < fallback.boundedCompletionGainUpper95Bb());
        assertEquals(
                2 * GAME.maximumAbsoluteTerminalUtilityBb(), fallback.terminalUtilitySpanBb(), 0);
    }

    @Test
    void fullRunoutEstimateAgreesWithIndependentActionRolloutWithinSamplingError() {
        var baseline =
                new CfrSolver<>(
                                GAME,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(300);
        var response =
                new FixedOpponentResponseCfr<>(GAME, baseline, 1, 43)
                        .solve(200)
                        .finalRegretPolicy();
        var sampled = PhysicalResponseHeldOutAudit.assess(GAME, baseline, response, 1, 2_000, 44);
        var integrated =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        baseline,
                        response,
                        1,
                        2_000,
                        45,
                        PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT);
        double combinedStandardError =
                Math.hypot(sampled.pairedStandardErrorBb(), integrated.pairedStandardErrorBb());
        assertTrue(
                Math.abs(sampled.responseGainBb() - integrated.responseGainBb())
                        < 5 * combinedStandardError);
        assertTrue(integrated.baselineFallbackPathProbability() >= 0);
        assertTrue(integrated.baselineFallbackPathProbability() <= 1);
        assertTrue(integrated.responseFallbackPathProbability() >= 0);
        assertTrue(integrated.responseFallbackPathProbability() <= 1);
    }

    @Test
    void opponentBaselineFallbackDoesNotCountAsMissingResponseSupport() {
        var deals = GAME.chanceOutcomes(GAME.initialState());
        Map<String, Map<String, Double>> baselinePolicies = new HashMap<>();
        Map<String, Map<String, Double>> responsePolicies = new HashMap<>();
        for (var deal : deals) {
            baselinePolicies.put(
                    "1:P:BTN:" + deal.state().button().key(), Map.of("open3", 1.0, "fold", 0.0));
            responsePolicies.put(
                    "0:P:BB:open3:" + deal.state().bigBlind().key(),
                    Map.of("call", 0.0, "fold", 1.0));
        }
        var report =
                PhysicalResponseActionIntegratedAudit.assess(
                        GAME,
                        new CfrSolution(1, baselinePolicies),
                        new CfrSolution(1, responsePolicies),
                        0,
                        deals.size() * 10,
                        74,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        assertEquals(1, report.baselineFallbackPathProbability(), 1e-12);
        assertEquals(0, report.responseFallbackPathProbability(), 1e-12);
        assertEquals(0, report.responseMissingPathProbability(), 1e-12);
        assertEquals(report.responseGainBb(), report.completionGainUpperBb(), 1e-12);
    }
}
