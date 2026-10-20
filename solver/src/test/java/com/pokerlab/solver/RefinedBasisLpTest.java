package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class RefinedBasisLpTest {
    record Case(
            String id,
            double[][] matrix,
            double[] rhs,
            double[] cost,
            double expectedMaximum,
            String originalRejection,
            List<Double> oraclePoint,
            List<Double> oracleDual) {}

    record Cases(String schemaVersion, String scope, boolean trainerAdmission, List<Case> cases) {}

    static Cases captured() throws Exception {
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                RefinedBasisLpTest.class.getResourceAsStream(
                                        "/lp-refined-basis-endpoints.json.gz")))) {
            return SixMaxTexturePayoffTable.mapper().readValue(input, Cases.class);
        }
    }

    @Test
    void distinctNearRatiosCannotTakeTheLargerInfeasibleStep() throws Exception {
        double[][] a = {{1}, {35491.780049572175}};
        double[] b = {1.0236165219481362e-12, 2.2032117954153633e-8}, c = {1};
        assertEquals(
                "LP lost primal feasibility",
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () -> BoundedLinearProgram.solveAscendingRefinedDirections(a, b, c))
                        .getMessage());
        var result = BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c);
        assertEquals(b[1] / a[1][0], result.point().getFirst(), 1e-25);
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                result.certificate().algorithm());
        RefinedLpDirectionsTest.checkOriginal(a, b, c, result);
    }

    @Test
    void threeCapturedEndpointProblemsRetainOldRejectionsAndNewOriginalCertificates()
            throws Exception {
        var controls = captured();
        assertFalse(controls.trainerAdmission());
        assertEquals(3, controls.cases().size());
        for (var p : controls.cases()) {
            var old =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () ->
                                    BoundedLinearProgram.solveAscendingRefinedDirections(
                                            p.matrix(), p.rhs(), p.cost()));
            assertEquals(p.originalRejection(), old.getMessage(), p.id());
            var solved =
                    BoundedLinearProgram.solveAscendingRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(p.expectedMaximum(), solved.certificate().primalValue(), 1e-8, p.id());
            assertEquals(81, solved.certificate().variables());
            assertEquals(86, solved.certificate().constraints());
            assertEquals(
                    BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                    solved.certificate().algorithm());
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), solved);
        }
    }

    @Test
    void all292IndependentOracleProblemsRetainOriginalPrimalDualChecks() throws Exception {
        BoundedLinearProgramTest.Controls controls;
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                getClass()
                                        .getResourceAsStream(
                                                "/bounded-lp-oracle-controls.json.gz")))) {
            controls =
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(input, BoundedLinearProgramTest.Controls.class);
        }
        assertEquals(292, controls.problems().size());
        for (var p : controls.problems()) {
            var solved =
                    BoundedLinearProgram.solveAscendingRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(
                    p.expectedMaximum(),
                    solved.certificate().primalValue(),
                    1e-8,
                    p.group() + ":" + p.id());
            assertEquals(
                    BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                    solved.certificate().algorithm());
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), solved);
        }
    }

    @Test
    void fixedRefinementUsesTheOriginalBudgetAndCannotRaiseLimits() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var old = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        var refined = BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c);
        assertEquals(BoundedLinearProgram.ASCENDING_L1_ALGORITHM, old.certificate().algorithm());
        assertEquals(2, BoundedLinearProgram.DIRECTION_REFINEMENT_PASSES);
        long spent = refined.certificate().work().arithmeticWork();
        assertTrue(spent > old.certificate().work().arithmeticWork());
        assertEquals(
                refined.certificate(),
                BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c, 10000, spent)
                        .certificate());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                                a, b, c, 10000, spent - 1))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                                a, b, c, 0, 4_000_000_000L))
                        .reason());
        for (long work : new long[] {-1, 4_000_000_001L})
            assertEquals(
                    BoundedLinearProgram.Failure.INVALID_INPUT,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () ->
                                            BoundedLinearProgram.solveAscendingRefinedBasis(
                                                    a, b, c, 10000, work))
                            .reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                a, b, c, 10001, 4_000_000_000L));
    }

    @Test
    void infeasibleUnboundedUnresolvedAndInvalidInputsFailClosed() {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1e-11}));
        for (double value :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1_000_001
                }) {
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedBasis(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedBasis(
                                    new double[][] {{1}}, new double[] {value}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedBasis(
                                    new double[][] {{1}}, new double[] {1}, new double[] {value}));
        }
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedBasis(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
    }

    @Test
    void successiveEndpointsShareTheSameCumulativeBudget() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var one = BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c);
        long work = one.certificate().work().arithmeticWork();
        int pivots = one.certificate().work().pivots();
        var budget =
                FiniteTwoPlayerRootActionIntervals.LpBudget.ascendingRefinedBasis(
                        2 * pivots, 2 * work);
        budget.solve(a, b, c);
        budget.solve(a, b, c);
        assertEquals(2, budget.solves);
        assertEquals(2 * work, budget.work);
        assertEquals(2 * pivots, budget.pivots);
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(BoundedLinearProgram.Rejected.class, () -> budget.solve(a, b, c))
                        .reason());
    }
}
