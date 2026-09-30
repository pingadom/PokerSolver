package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalResponseStabilityAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame GAME =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void selectsOnlyOnValidationAndReusesCommonIndependentStreams() {
        var baseline =
                new CfrSolver<>(
                                GAME,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(300);
        var first =
                PhysicalResponseStabilityAudit.assess(
                        GAME, baseline, 1, List.of(50, 100), List.of(51L, 52L), 500, 500, 53, 54);
        var repeated =
                PhysicalResponseStabilityAudit.assess(
                        GAME, baseline, 1, List.of(50, 100), List.of(51L, 52L), 500, 500, 53, 54);
        assertEquals(first, repeated);
        assertEquals(4, first.candidates().size());
        for (var candidate : first.candidates()) {
            assertEquals(53, candidate.validation().seed());
            assertEquals(54, candidate.confirmation().seed());
            assertEquals(first.gameHash(), candidate.confirmation().gameHash());
            assertTrue(
                    candidate.validation().responseGainBb()
                            <= first.selected().validation().responseGainBb());
            assertEquals(
                    first.candidates().get(0).confirmation().baselineTargetUtilityBb(),
                    candidate.confirmation().baselineTargetUtilityBb(),
                    0);
        }
        var changedConfirmation =
                PhysicalResponseStabilityAudit.assess(
                        GAME, baseline, 1, List.of(50, 100), List.of(51L, 52L), 500, 500, 53, 55);
        assertEquals(first.selectedIndex(), changedConfirmation.selectedIndex());
        for (int index = 0; index < first.candidates().size(); index++)
            assertEquals(
                    first.candidates().get(index).validation(),
                    changedConfirmation.candidates().get(index).validation());

        var both =
                PhysicalResponseStabilityAudit.assess(
                        GAME,
                        baseline,
                        1,
                        List.of(50),
                        List.of(51L),
                        500,
                        500,
                        53,
                        54,
                        List.of(
                                PhysicalResponseStabilityAudit.PolicyVariant.AVERAGE,
                                PhysicalResponseStabilityAudit.PolicyVariant.FINAL_REGRET));
        assertEquals(2, both.candidates().size());
        assertEquals(
                PhysicalResponseStabilityAudit.PolicyVariant.AVERAGE,
                both.candidates().get(0).variant());
        assertEquals(
                PhysicalResponseStabilityAudit.PolicyVariant.FINAL_REGRET,
                both.candidates().get(1).variant());
        assertTrue(both.selectedIndex() == 0 || both.selectedIndex() == 1);
        var exactRoot =
                PhysicalResponseStabilityAudit.assess(
                        GAME,
                        baseline,
                        1,
                        List.of(50),
                        List.of(51L),
                        500,
                        500,
                        53,
                        54,
                        List.of(PhysicalResponseStabilityAudit.PolicyVariant.FINAL_REGRET),
                        FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT);
        assertEquals(FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT, exactRoot.chanceMode());
        assertEquals(
                FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT, exactRoot.selected().chanceMode());
        assertTrue(Double.isFinite(exactRoot.selected().confirmation().responseGainBb()));
    }

    @Test
    void rejectsOverlappingStreamsAndInvalidCandidateGrids() {
        var empty = new CfrSolution(1, Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalResponseStabilityAudit.assess(
                                GAME, empty, 0, List.of(10), List.of(51L), 10, 10, 53, 53));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalResponseStabilityAudit.assess(
                                GAME, empty, 0, List.of(10, 10), List.of(51L), 10, 10, 53, 54));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalResponseStabilityAudit.assess(
                                GAME, empty, 0, List.of(10), List.of(51L), 1, 10, 53, 54));
    }
}
