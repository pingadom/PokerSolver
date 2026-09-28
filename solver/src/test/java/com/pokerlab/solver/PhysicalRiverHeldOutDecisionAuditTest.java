package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PhysicalRiverHeldOutDecisionAuditTest {
    @Test
    void splitIsRepeatableAndUsesTheSameHeldOutBoardsForEveryMode() {
        var report = PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 42);
        assertEquals(report, PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 42));
        assertEquals(1_000, report.fine().heldOutBoards());
        assertEquals(report.fine().heldOutBoards(), report.texture().heldOutBoards());
        assertEquals(report.fine().heldOutBoards(), report.coarse().heldOutBoards());
        assertEquals(report.fine().physicalOracleGainBb(), report.texture().physicalOracleGainBb());
        assertEquals(report.fine().physicalOracleGainBb(), report.coarse().physicalOracleGainBb());
        assertTrue(report.fine().discoveredBuckets() >= report.texture().discoveredBuckets());
        assertTrue(report.texture().discoveredBuckets() >= report.coarse().discoveredBuckets());
        assertTrue(
                report.coarse().heldOutSupportedBoards()
                        >= report.texture().heldOutSupportedBoards());
        assertTrue(
                report.texture().heldOutSupportedBoards()
                        >= report.fine().heldOutSupportedBoards());
        assertTrue(report.fine().regretBb() >= -1e-9);
        assertTrue(report.texture().regretBb() >= -1e-9);
        assertTrue(report.coarse().regretBb() >= -1e-9);
    }

    @Test
    void rejectsInvalidSampleAndSupportSettings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalRiverHeldOutDecisionAudit.assess(3, 1, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalRiverHeldOutDecisionAudit.assess(20, 11, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalRiverHeldOutDecisionAudit.assess(20, 0, 42));
    }

    @Test
    void stressRangeUsesDifferentDealsAndTheSameHeldOutPhysicalBoards() {
        var base = PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 43);
        var stress =
                PhysicalRiverHeldOutDecisionAudit.assess(
                        2_000, 5, 43, ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5);
        assertNotEquals(base.fine().physicalOracleGainBb(), stress.fine().physicalOracleGainBb());
        assertEquals(stress.fine().physicalOracleGainBb(), stress.texture().physicalOracleGainBb());
        assertEquals(stress.fine().physicalOracleGainBb(), stress.coarse().physicalOracleGainBb());
        assertEquals(1_000, stress.fine().heldOutBoards());
    }
}
