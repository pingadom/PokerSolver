package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class AscendingRefinedDirectionsTest {
    record Problem(double[][] matrix, double[] rhs, double[] cost) {}

    static Problem pairedProblem() throws Exception {
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                AscendingRefinedDirectionsTest.class.getResourceAsStream(
                                        "/lp-parent-problem-paired.json.gz")))) {
            return SixMaxTexturePayoffTable.mapper().readValue(input, Problem.class);
        }
    }

    @Test
    void capturedParentProblemKeepsOldRejectionAndNewOriginalCertificate() throws Exception {
        var p = pairedProblem();
        var old =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                BoundedLinearProgram.solveAscendingL1Rows(
                                        p.matrix(), p.rhs(), p.cost()));
        assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, old.reason());
        assertEquals("Singular LP basis", old.getMessage());
        var solved =
                BoundedLinearProgram.solveAscendingRefinedDirections(p.matrix(), p.rhs(), p.cost());
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                solved.certificate().algorithm());
        assertEquals(79, solved.certificate().variables());
        assertEquals(81, solved.certificate().constraints());
        RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), solved);
        assertTrue(solved.certificate().absoluteDualityGap() < 1e-12);
        assertTrue(
                solved.certificate().work().arithmeticWork()
                        <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
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
                    BoundedLinearProgram.solveAscendingRefinedDirections(
                            p.matrix(), p.rhs(), p.cost());
            assertEquals(
                    p.expectedMaximum(),
                    solved.certificate().primalValue(),
                    1e-8,
                    p.group() + ":" + p.id());
            assertEquals(
                    BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                    solved.certificate().algorithm());
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), solved);
        }
    }

    @Test
    void fixedRefinementUsesTheOriginalBudgetAndCannotRaiseLimits() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var old = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        var refined = BoundedLinearProgram.solveAscendingRefinedDirections(a, b, c);
        assertEquals(BoundedLinearProgram.ASCENDING_L1_ALGORITHM, old.certificate().algorithm());
        assertEquals(2, BoundedLinearProgram.DIRECTION_REFINEMENT_PASSES);
        long spent = refined.certificate().work().arithmeticWork();
        assertTrue(spent > old.certificate().work().arithmeticWork());
        assertEquals(
                refined.certificate(),
                BoundedLinearProgram.solveAscendingRefinedDirections(a, b, c, 10000, spent)
                        .certificate());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                                a, b, c, 10000, spent - 1))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                                a, b, c, 0, 4_000_000_000L))
                        .reason());
        for (long work : new long[] {-1, 4_000_000_001L})
            assertEquals(
                    BoundedLinearProgram.Failure.INVALID_INPUT,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () ->
                                            BoundedLinearProgram.solveAscendingRefinedDirections(
                                                    a, b, c, 10000, work))
                            .reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                a, b, c, 10001, 4_000_000_000L));
    }

    @Test
    void infeasibleUnboundedUnresolvedAndInvalidInputsFailClosed() {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1e-11}));
        for (double value :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1_000_001
                }) {
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedDirections(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedDirections(
                                    new double[][] {{1}}, new double[] {value}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveAscendingRefinedDirections(
                                    new double[][] {{1}}, new double[] {1}, new double[] {value}));
        }
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingRefinedDirections(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
    }
}
