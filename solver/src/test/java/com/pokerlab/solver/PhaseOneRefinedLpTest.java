package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class PhaseOneRefinedLpTest {
    static JsonNode captured() throws Exception {
        try (var in =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                PhaseOneRefinedLpTest.class.getResourceAsStream(
                                        "/lp-big-blind-phase-one.json.gz")))) {
            return SixMaxTexturePayoffTable.mapper().readTree(in);
        }
    }

    @Test
    void originalPhaseOneFailureRemainsAndExplicitModeCertifiesTheUnchangedProblem()
            throws Exception {
        var p = captured();
        var m = SixMaxTexturePayoffTable.mapper();
        var a = m.treeToValue(p.path("matrix"), double[][].class);
        var b = m.treeToValue(p.path("rhs"), double[].class);
        var c = m.treeToValue(p.path("cost"), double[].class);
        var original =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () -> BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c));
        assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, original.reason());
        assertEquals("Singular LP basis", original.getMessage());
        assertEquals(21, p.path("failedPhaseOnePivots").intValue());
        assertEquals(0, p.path("failedPhaseTwoPivots").intValue());
        var result = BoundedLinearProgram.solvePhaseOneRefinedBasis(a, b, c);
        assertEquals(
                BoundedLinearProgram.PHASE_ONE_REFINED_BASIS_ALGORITHM,
                result.certificate().algorithm());
        assertEquals(
                p.path("oracle").path("maximum").doubleValue(),
                result.certificate().primalValue(),
                1e-8);
        assertEquals(83, result.certificate().constraints());
        assertEquals(82, result.certificate().variables());
        RefinedLpDirectionsTest.checkOriginal(a, b, c, result);
        // Distinct algorithms still bind the same original LP, without changing its rows.
        assertNotNull(result.certificate().problemHash());
        assertFalse(p.path("trainerAdmission").booleanValue());
    }

    @Test
    void all292IndependentOracleProblemsKeepOriginalPrimalDualCertificates() throws Exception {
        BoundedLinearProgramTest.Controls controls;
        try (var in =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                getClass()
                                        .getResourceAsStream(
                                                "/bounded-lp-oracle-controls.json.gz")))) {
            controls =
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(in, BoundedLinearProgramTest.Controls.class);
        }
        assertEquals(292, controls.problems().size());
        for (var p : controls.problems()) {
            var result =
                    BoundedLinearProgram.solvePhaseOneRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(
                    p.expectedMaximum(),
                    result.certificate().primalValue(),
                    1e-8,
                    p.group() + ":" + p.id());
            assertEquals(
                    BoundedLinearProgram.PHASE_ONE_REFINED_BASIS_ALGORITHM,
                    result.certificate().algorithm());
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), result);
        }
    }

    @Test
    void priorCapturedEndpointRegressionsAndStrictRatiosRemainCertified() throws Exception {
        for (var p : ResolvableRefinedLpTest.captured().cases()) {
            var result =
                    BoundedLinearProgram.solvePhaseOneRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(p.expectedMaximum(), result.certificate().primalValue(), 1e-8);
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), result);
        }
        double[][] a = {{1}, {35491.780049572175}};
        double[] b = {1.0236165219481362e-12, 2.2032117954153633e-8}, c = {1};
        var result = BoundedLinearProgram.solvePhaseOneRefinedBasis(a, b, c);
        assertEquals(b[1] / a[1][0], result.point().getFirst(), 1e-25);
        RefinedLpDirectionsTest.checkOriginal(a, b, c, result);
    }

    @Test
    void noCapOverrideAndOneCumulativeIntervalBudget() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () -> BoundedLinearProgram.solvePhaseOneRefinedBasis(a, b, c, 0, 0))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.INVALID_INPUT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solvePhaseOneRefinedBasis(
                                                a, b, c, 10001, 4000000000L))
                        .reason());
        var one = BoundedLinearProgram.solvePhaseOneRefinedBasis(a, b, c);
        var budget =
                FiniteTwoPlayerRootActionIntervals.LpBudget.phaseOneRefinedBasis(
                        2 * one.certificate().work().pivots(),
                        2 * one.certificate().work().arithmeticWork());
        budget.solve(a, b, c);
        budget.solve(a, b, c);
        assertEquals(2, budget.solves);
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(BoundedLinearProgram.Rejected.class, () -> budget.solve(a, b, c))
                        .reason());
    }
}
