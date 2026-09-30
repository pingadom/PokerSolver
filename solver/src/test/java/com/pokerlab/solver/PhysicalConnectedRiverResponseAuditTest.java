package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalConnectedRiverResponseAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame GAME =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void sameSolutionControlHasZeroResponseAndSelectionDifference() {
        var solution = solve(42);
        var report =
                PhysicalConnectedRiverResponseAudit.assess(GAME, solution, solution, 4_000, 2, 43);
        assertEquals(
                report,
                PhysicalConnectedRiverResponseAudit.assess(GAME, solution, solution, 4_000, 2, 43));
        assertEquals(
                report.attemptedDeals(),
                report.terminalBeforeRiver() + report.missingReachPolicy() + report.reachedRiver());
        assertTrue(report.reachedRiver() > 0);
        assertTrue(report.evaluatedStates() > 0);
        assertTrue(report.supportedHeldOutStates() > 0);
        assertTrue(report.supportedHeldOutStates() <= report.heldOutStates());
        assertTrue(report.heldOutStates() <= report.evaluatedStates());
        assertEquals(0, report.meanAbsoluteCallDifference(), 0);
        assertEquals(0, report.oppositeMajorityRate(), 0);
        assertEquals(0, report.meanBetUtilityDifferenceBb(), 0);
        assertEquals(0, report.alternateMinusPrimaryBb(), 0);
        assertEquals(report.primarySelectedGainBb(), report.alternateSelectedGainBb(), 0);
        assertTrue(report.supportedHeldOutRate() > 0);
        assertTrue(report.supportedHeldOutRate() <= 1);
    }

    @Test
    void independentResponseUsesIdenticalPrimaryReachAndHeldOutStates() {
        var primary = solve(42);
        var alternate = solve(99);
        var control =
                PhysicalConnectedRiverResponseAudit.assess(GAME, primary, primary, 4_000, 2, 43);
        var independent =
                PhysicalConnectedRiverResponseAudit.assess(GAME, primary, alternate, 4_000, 2, 43);
        assertEquals(control.terminalBeforeRiver(), independent.terminalBeforeRiver());
        assertEquals(control.missingReachPolicy(), independent.missingReachPolicy());
        assertEquals(control.reachedRiver(), independent.reachedRiver());
        assertEquals(
                control.missingBigBlindRiverPolicy(), independent.missingBigBlindRiverPolicy());
        assertTrue(independent.evaluatedStates() > 0);
        assertTrue(independent.meanAbsoluteCallDifference() > 0);
        assertTrue(Double.isFinite(independent.alternateMinusPrimaryStandardErrorBb()));
        assertEquals(
                independent.alternateSelectedGainBb() - independent.primarySelectedGainBb(),
                independent.alternateMinusPrimaryBb(),
                1e-9);
    }

    @Test
    void rejectsInvalidSamplingParameters() {
        var solution = solve(1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseAudit.assess(
                                GAME, solution, solution, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseAudit.assess(
                                GAME, solution, solution, 10, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseAudit.assess(
                                GAME, solution, solution, 10, 6, 1));
    }

    @Test
    void absentReachOrAlternateResponseIsCountedRatherThanFilledByFallback() {
        var primary = solve(42);
        var empty = new CfrSolution(1, Map.of());
        var noReach = PhysicalConnectedRiverResponseAudit.assess(GAME, empty, primary, 500, 1, 43);
        assertEquals(500, noReach.missingReachPolicy());
        assertEquals(0, noReach.reachedRiver());
        assertEquals(0, noReach.evaluatedStates());
        assertTrue(Double.isNaN(noReach.alternateMinusPrimaryBb()));

        var noResponse =
                PhysicalConnectedRiverResponseAudit.assess(GAME, primary, empty, 2_000, 1, 43);
        assertTrue(noResponse.reachedRiver() > 0);
        assertTrue(noResponse.missingAlternateResponse() > 0);
        assertEquals(0, noResponse.evaluatedStates());
        assertEquals(0, noResponse.supportedHeldOutStates());
    }

    private static CfrSolution solve(long seed) {
        return new CfrSolver<>(
                        GAME,
                        CfrSolver.Variant.VANILLA,
                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                        seed)
                .solve(300);
    }
}
