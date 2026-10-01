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
}
