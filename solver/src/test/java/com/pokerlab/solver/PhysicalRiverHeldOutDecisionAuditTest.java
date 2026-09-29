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
        assertEquals(report.fine().heldOutBoards(), report.equity().heldOutBoards());
        assertEquals(report.fine().physicalOracleGainBb(), report.texture().physicalOracleGainBb());
        assertEquals(report.fine().physicalOracleGainBb(), report.coarse().physicalOracleGainBb());
        assertEquals(report.fine().physicalOracleGainBb(), report.equity().physicalOracleGainBb());
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
        assertTrue(report.equity().regretBb() >= -1e-9);
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
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalRiverHeldOutDecisionAudit.assess(
                                20,
                                5,
                                42,
                                ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3,
                                1.1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalRiverHeldOutDecisionAudit.assess(
                                20,
                                5,
                                42,
                                ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3,
                                0.5,
                                PhysicalRiverHeldOutDecisionAudit.ResponseModel
                                        .PAIR_OR_BETTER_CALL));
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
        assertEquals(stress.fine().physicalOracleGainBb(), stress.equity().physicalOracleGainBb());
        assertEquals(1_000, stress.fine().heldOutBoards());
    }

    @Test
    void partialCallResponseChangesTheDecisionValuesWithoutChangingPhysicalReach() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var alwaysCalls = PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 43, profile, 1.0);
        var sometimesFolds = PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 43, profile, 0.8);
        assertEquals(alwaysCalls.equity().heldOutBoards(), sometimesFolds.equity().heldOutBoards());
        assertEquals(
                alwaysCalls.equity().discoveredBuckets(),
                sometimesFolds.equity().discoveredBuckets());
        assertNotEquals(
                alwaysCalls.equity().physicalOracleGainBb(),
                sometimesFolds.equity().physicalOracleGainBb());
    }

    @Test
    void fixedResponseIncrementAccountsForFoldWinsAndCalledShowdowns() {
        assertEquals(8, PhysicalRiverHeldOutDecisionAudit.betIncrement(1, 3.25, 8, 1), 1e-12);
        assertEquals(-8, PhysicalRiverHeldOutDecisionAudit.betIncrement(-1, 3.25, 8, 1), 1e-12);
        assertEquals(0, PhysicalRiverHeldOutDecisionAudit.betIncrement(0, 3.25, 8, 1), 1e-12);
        assertEquals(4, PhysicalRiverHeldOutDecisionAudit.betIncrement(1, 3.25, 8, 0.5), 1e-12);
        assertEquals(
                -0.75, PhysicalRiverHeldOutDecisionAudit.betIncrement(-1, 3.25, 8, 0.5), 1e-12);
        assertEquals(1.625, PhysicalRiverHeldOutDecisionAudit.betIncrement(0, 3.25, 8, 0.5), 1e-12);
    }

    @Test
    void handDependentResponseChangesValuesOnIdenticalHeldOutBoards() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var independent = PhysicalRiverHeldOutDecisionAudit.assess(2_000, 5, 42, profile, 1.0);
        var pairCalls =
                PhysicalRiverHeldOutDecisionAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        1.0,
                        PhysicalRiverHeldOutDecisionAudit.ResponseModel.PAIR_OR_BETTER_CALL);
        assertEquals(independent.equity().heldOutBoards(), pairCalls.equity().heldOutBoards());
        assertEquals(
                independent.equity().heldOutSupportedBoards(),
                pairCalls.equity().heldOutSupportedBoards());
        assertNotEquals(
                independent.equity().physicalOracleGainBb(),
                pairCalls.equity().physicalOracleGainBb());
    }
}
