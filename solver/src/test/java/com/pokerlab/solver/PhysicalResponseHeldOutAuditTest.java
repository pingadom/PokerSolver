package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalResponseHeldOutAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame GAME =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void samePolicyAndEmptyResponseAreExactPairedNoOpControls() {
        var baseline =
                new CfrSolver<>(
                                GAME,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(300);
        for (int player = 0; player <= 1; player++) {
            var same =
                    PhysicalResponseHeldOutAudit.assess(
                            GAME, baseline, baseline, player, 2_000, 43);
            assertEquals(0, same.responseGainBb(), 0);
            assertEquals(0, same.pairedStandardErrorBb(), 0);
            var empty =
                    PhysicalResponseHeldOutAudit.assess(
                            GAME, baseline, new CfrSolution(1, Map.of()), player, 2_000, 43);
            assertEquals(0, empty.responseGainBb(), 0);
            assertTrue(empty.responseFallbackToBaselineDecisions() > 0);
            assertTrue(
                    empty.responseFallbackToBaselineDecisions()
                            >= same.responseFallbackToBaselineDecisions());
            assertEquals(same.baselineTargetUtilityBb(), empty.baselineTargetUtilityBb(), 0);
            assertEquals(
                    empty,
                    PhysicalResponseHeldOutAudit.assess(
                            GAME, baseline, new CfrSolution(1, Map.of()), player, 2_000, 43));
        }
    }

    @Test
    void rejectsInvalidPlayerAndTrialCount() {
        var empty = new CfrSolution(1, Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalResponseHeldOutAudit.assess(GAME, empty, empty, 2, 10, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalResponseHeldOutAudit.assess(GAME, empty, empty, 0, 1, 42));
    }

    @Test
    void stratificationExactlyIntegratesUnequalPrivateDealWeights() {
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
        responsePolicies.put("1:P:BTN:" + selectedButton.key(), Map.of("open3", 1.0, "fold", 0.0));
        var baseline = new CfrSolution(1, baselinePolicies);
        var response = new CfrSolution(1, responsePolicies);
        double expected = 0;
        for (var deal : deals) {
            if (!deal.state().button().equals(selectedButton)) continue;
            var folded = GAME.afterAction(deal.state(), "fold");
            var openedThenFolded =
                    GAME.afterAction(GAME.afterAction(deal.state(), "open3"), "fold");
            expected +=
                    deal.probability()
                            * (GAME.terminalUtility(folded)
                                    - GAME.terminalUtility(openedThenFolded));
        }
        int trials = deals.size() * 100;
        var stratified =
                PhysicalResponseHeldOutAudit.assess(
                        GAME,
                        baseline,
                        response,
                        1,
                        trials,
                        93,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT);
        var sampled = PhysicalResponseHeldOutAudit.assess(GAME, baseline, response, 1, trials, 93);
        assertEquals(expected, stratified.responseGainBb(), 1e-12);
        assertEquals(0, stratified.pairedStandardErrorBb(), 1e-12);
        assertTrue(sampled.pairedStandardErrorBb() > 0);
        assertEquals(100, stratified.independentBatches());
        assertEquals(trials, sampled.independentBatches());
        assertEquals(trials, stratified.trials());
        assertEquals(
                stratified,
                PhysicalResponseHeldOutAudit.assess(
                        GAME,
                        baseline,
                        response,
                        1,
                        trials,
                        93,
                        PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalResponseHeldOutAudit.assess(
                                GAME,
                                baseline,
                                response,
                                1,
                                trials - 1,
                                93,
                                PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT));
    }
}
