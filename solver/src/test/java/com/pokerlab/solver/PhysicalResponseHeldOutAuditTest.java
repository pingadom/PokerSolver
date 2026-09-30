package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

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
}
