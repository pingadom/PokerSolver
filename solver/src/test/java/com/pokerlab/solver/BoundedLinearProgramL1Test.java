package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class BoundedLinearProgramL1Test {
    record OriginalProblem(double[][] matrix, double[] rhs, double[] cost) {}

    static void check(
            double[][] a,
            double[] b,
            double[] c,
            double optimum,
            BoundedLinearProgram.Result result)
            throws Exception {
        var certificate = result.certificate();
        assertEquals(BoundedLinearProgram.ASCENDING_L1_ALGORITHM, certificate.algorithm());
        assertEquals(
                SixMaxHistoryPhysicalConditionalRefinement.hash(new OriginalProblem(a, b, c)),
                certificate.problemHash());
        assertEquals(optimum, certificate.primalValue(), 1e-8);
        double primal = 0, dual = 0;
        for (int j = 0; j < c.length; j++) {
            double x = result.point().get(j), column = 0;
            assertTrue(Double.isFinite(x) && x >= 0);
            primal += c[j] * x;
            for (int i = 0; i < b.length; i++) column += a[i][j] * result.dual().get(i);
            assertTrue(column >= c[j] - 1e-8);
        }
        for (int i = 0; i < b.length; i++) {
            double y = result.dual().get(i), lhs = 0;
            assertTrue(Double.isFinite(y) && y >= -1e-8);
            dual += b[i] * y;
            for (int j = 0; j < c.length; j++) lhs += a[i][j] * result.point().get(j);
            assertTrue(lhs <= b[i] + 1e-8);
        }
        assertEquals(primal, dual, 1e-8);
        assertEquals(primal, certificate.primalValue());
        assertEquals(dual, certificate.dualValue());
        assertTrue(certificate.work().arithmeticWork() <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    @Test
    void all292IndependentOptimaHaveOriginalProblemHashesAndOriginalRowDuals() throws Exception {
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
        for (var problem : controls.problems()) {
            check(
                    problem.matrix(),
                    problem.rhs(),
                    problem.cost(),
                    problem.expectedMaximum(),
                    BoundedLinearProgram.solveAscendingL1Rows(
                            problem.matrix(), problem.rhs(), problem.cost()));
        }
    }

    @Test
    void inverseMappingDoesNotReturnTheSortedProblemCertificateOrDual() throws Exception {
        double[][] a = {{10, 0}, {0, 1}, {1, 1}};
        double[] b = {20, 3, 4}, c = {3, 1};
        var result = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        check(a, b, c, 8, result);
        assertEquals(.2, result.dual().get(0), 1e-10);
        assertEquals(0, result.dual().get(1), 1e-10);
        assertEquals(1, result.dual().get(2), 1e-10);
        var sorted =
                BoundedLinearProgram.solve(
                        new double[][] {{0, 1}, {1, 1}, {10, 0}}, new double[] {3, 4, 20}, c);
        assertNotEquals(sorted.certificate().problemHash(), result.certificate().problemHash());
        assertNotEquals(sorted.dual(), result.dual());
        var original = BoundedLinearProgram.solve(a, b, c);
        assertEquals(original.certificate().problemHash(), result.certificate().problemHash());
        assertEquals(BoundedLinearProgram.ALGORITHM, original.certificate().algorithm());
        assertThrows(UnsupportedOperationException.class, () -> result.dual().set(0, 0.0));
        assertThrows(UnsupportedOperationException.class, () -> result.point().set(0, 0.0));
    }

    @Test
    void stableTiesSignedBoundsAndDegeneracyAreDeterministicAndInputsAreCopied() throws Exception {
        double[][] a = {{-1, 0}, {1, 0}, {0, 1}, {1, 1}, {1, 1}};
        double[] b = {-1, 2, 3, 4, 4}, c = {1, 1};
        var result = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        check(a, b, c, 4, result);
        for (int i = 0; i < 3; i++) {
            var repeated = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
            assertEquals(result.certificate(), repeated.certificate());
            assertEquals(result.point(), repeated.point());
            assertEquals(result.dual(), repeated.dual());
        }
        var equalRows =
                BoundedLinearProgram.solveAscendingL1Rows(
                        new double[][] {{1}, {1}}, new double[] {1, 1}, new double[] {1});
        assertEquals(List.of(1.0, 0.0), equalRows.dual());
        var preservedCertificate = result.certificate();
        var preservedPoint = List.copyOf(result.point());
        a[0][0] = 999;
        b[0] = 999;
        c[0] = 999;
        assertEquals(preservedCertificate, result.certificate());
        assertEquals(preservedPoint, result.point());
        assertEquals(4, result.certificate().primalValue(), 1e-8);
        assertEquals(
                0,
                BoundedLinearProgram.solveAscendingL1Rows(
                                new double[][] {{1}}, new double[] {3}, new double[] {0})
                        .certificate()
                        .primalValue());
        assertTrue(
                Arrays.stream(BoundedLinearProgram.Result.class.getDeclaredConstructors())
                        .allMatch(k -> Modifier.isPrivate(k.getModifiers())));
    }

    @Test
    void totalBudgetIncludesOrderingAndOriginalCertificateAndCannotBeEnlarged() throws Exception {
        double[][] a = {{10, 0}, {0, 1}, {1, 1}};
        double[] b = {20, 3, 4}, c = {3, 1};
        var result = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        long required = result.certificate().work().arithmeticWork();
        var exact =
                BoundedLinearProgram.solveAscendingL1Rows(
                        a, b, c, result.certificate().work().pivots(), required);
        assertEquals(result.certificate(), exact.certificate());
        for (long cap : new long[] {0, 100, required - 1}) {
            var error =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () -> BoundedLinearProgram.solveAscendingL1Rows(a, b, c, 100, cap));
            assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, error.reason());
        }
        assertEquals(
                BoundedLinearProgram.Failure.INVALID_INPUT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingL1Rows(
                                                a,
                                                b,
                                                c,
                                                BoundedLinearProgram.MAX_PIVOTS + 1,
                                                required))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.INVALID_INPUT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingL1Rows(
                                                a,
                                                b,
                                                c,
                                                100,
                                                BoundedLinearProgram.MAX_ARITHMETIC_WORK + 1))
                        .reason());
    }

    @Test
    void invalidInfeasibleAndUnboundedProblemsCannotProduceOwnedResults() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1000001})
            assertEquals(
                    BoundedLinearProgram.Failure.INVALID_INPUT,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () ->
                                            BoundedLinearProgram.solveAscendingL1Rows(
                                                    new double[][] {{invalid}},
                                                    new double[] {1},
                                                    new double[] {1}))
                            .reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingL1Rows(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveAscendingL1Rows(
                                null, new double[] {1}, new double[] {1}));
        assertEquals(
                BoundedLinearProgram.Failure.NO_CERTIFIED_OPTIMUM,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingL1Rows(
                                                new double[][] {{1}, {-1}},
                                                new double[] {0, -1},
                                                new double[] {1}))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.NO_CERTIFIED_OPTIMUM,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveAscendingL1Rows(
                                                new double[][] {{-1}},
                                                new double[] {-1},
                                                new double[] {1}))
                        .reason());
    }
}
