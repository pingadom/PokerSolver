package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class BoundedLinearProgramTest {
    record Problem(
            String group,
            int id,
            double[] cost,
            double[][] matrix,
            double[] rhs,
            double expectedMaximum) {}

    record Controls(
            String schemaVersion,
            String oracle,
            String scipyVersion,
            String numpyVersion,
            long genericSeed,
            String sequenceCases,
            String physicalModel,
            String physicalPayoffHash,
            List<Problem> problems) {}

    private static void check(Problem problem, double[][] a, double[] b) throws Exception {
        var solved = BoundedLinearProgram.solve(a, b, problem.cost());
        assertEquals(
                problem.expectedMaximum(),
                solved.certificate().primalValue(),
                1e-8,
                problem.group() + ":" + problem.id());
        // Check the ORIGINAL inequalities and dual with independent literal loops in the test.
        var x = solved.point();
        var y = solved.dual();
        double primal = 0, dual = 0;
        for (int j = 0; j < x.size(); j++) {
            assertTrue(x.get(j) >= 0 && Double.isFinite(x.get(j)));
            primal += problem.cost()[j] * x.get(j);
            double constraint = 0;
            for (int i = 0; i < y.size(); i++) constraint += y.get(i) * a[i][j];
            assertTrue(constraint >= problem.cost()[j] - 1e-8);
        }
        for (int i = 0; i < y.size(); i++) {
            assertTrue(y.get(i) >= -1e-8 && Double.isFinite(y.get(i)));
            dual += b[i] * y.get(i);
            double constraint = 0;
            for (int j = 0; j < x.size(); j++) constraint += a[i][j] * x.get(j);
            assertTrue(constraint <= b[i] + 1e-8);
        }
        assertEquals(primal, dual, 1e-8);
        assertEquals(primal, solved.certificate().primalValue(), 1e-12);
        assertTrue(solved.certificate().work().pivots() <= BoundedLinearProgram.MAX_PIVOTS);
        assertTrue(
                solved.certificate().work().arithmeticWork()
                        <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    @Test
    void allIndependentOracleControlsSurviveReorderingAndPositiveRowScaling() throws Exception {
        Controls controls;
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                getClass()
                                        .getResourceAsStream(
                                                "/bounded-lp-oracle-controls.json.gz")))) {
            controls = SixMaxTexturePayoffTable.mapper().readValue(input, Controls.class);
        }
        assertEquals("independent-bounded-lp-controls/v1", controls.schemaVersion());
        assertEquals(292, controls.problems().size());
        assertEquals(
                52, controls.problems().stream().filter(p -> p.group().equals("sequence")).count());
        for (var p : controls.problems()) {
            check(p, p.matrix(), p.rhs());
            for (int trial = 0; trial < 3; trial++) {
                var random = new SplittableRandom(261L + p.id() * 17L + trial);
                var order = new ArrayList<Integer>();
                for (int i = 0; i < p.rhs().length; i++) order.add(i);
                if (trial < 2)
                    for (int i = order.size() - 1; i > 0; i--)
                        Collections.swap(order, i, random.nextInt(i + 1));
                var a = new double[order.size()][];
                var b = new double[order.size()];
                for (int i = 0; i < order.size(); i++) {
                    double scale = trial < 2 ? 1 : Math.scalb(1.0, random.nextInt(-4, 5));
                    a[i] = p.matrix()[order.get(i)].clone();
                    for (int j = 0; j < a[i].length; j++) a[i][j] *= scale;
                    b[i] = p.rhs()[order.get(i)] * scale;
                }
                check(p, a, b);
            }
        }
    }

    @Test
    void signedRhsEqualityDuplicatesAndZeroObjectiveHaveLiteralOptima() throws Exception {
        check(
                new Problem(
                        "analytic",
                        0,
                        new double[] {1, 1},
                        new double[][] {{1, 0}, {0, 1}, {1, 1}},
                        new double[] {2, 3, 4},
                        4),
                new double[][] {{1, 0}, {0, 1}, {1, 1}},
                new double[] {2, 3, 4});
        var p =
                new Problem(
                        "analytic",
                        1,
                        new double[] {-1},
                        new double[][] {{-1}, {1}, {-1}, {1}},
                        new double[] {-1, 1, -1, 3},
                        -1);
        check(p, p.matrix(), p.rhs());
        var zero =
                BoundedLinearProgram.solve(
                        new double[][] {{1}}, new double[] {3}, new double[] {0});
        assertEquals(0, zero.certificate().primalValue());
        assertEquals(0, zero.certificate().work().pivots());
    }

    @Test
    void degenerateCyclingControlFallsBackToBlandAndStillCertifiesTheOriginalProblem()
            throws Exception {
        var solved =
                BoundedLinearProgram.solve(
                        new double[][] {{.5, -5.5, -2.5, 9}, {.5, -1.5, -.5, 1}, {1, 0, 0, 0}},
                        new double[] {0, 0, 1},
                        new double[] {10, -57, -9, -24});
        assertEquals(1, solved.certificate().primalValue(), 1e-8);
        assertTrue(solved.certificate().work().blandFallback());
    }

    @Test
    void infeasibleAndUnboundedInputsCannotProduceACertifiedHandle() {
        var infeasible =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                BoundedLinearProgram.solve(
                                        new double[][] {{1}, {-1}},
                                        new double[] {0, -1},
                                        new double[] {1}));
        assertEquals(BoundedLinearProgram.Failure.NO_CERTIFIED_OPTIMUM, infeasible.reason());
        var unbounded =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                BoundedLinearProgram.solve(
                                        new double[][] {{-1}},
                                        new double[] {-1},
                                        new double[] {1}));
        assertEquals(BoundedLinearProgram.Failure.NO_CERTIFIED_OPTIMUM, unbounded.reason());
    }

    @Test
    void resolvablyPositiveCostsBelowTheStablePivotThresholdFailClosed() {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solve(
                                new double[][] {{1}},
                                new double[] {1_000_000},
                                new double[] {1e-11}));
        for (double bound : new double[] {0, 1_000_000}) {
            var failure =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () ->
                                    BoundedLinearProgram.solve(
                                            new double[][] {{-1}},
                                            new double[] {bound},
                                            new double[] {1e-11}));
            assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, failure.reason());
        }
        // Both costs are large; the profitable recession direction appears after cancellation.
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solve(
                                new double[][] {{1, -1}},
                                new double[] {0},
                                new double[] {1, -1 + 1e-11}));
    }

    @Test
    void invalidNumbersShapesAndDimensionsFailBeforeOptimization() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1_000_001}) {
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solve(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solve(
                                    new double[][] {{1}}, new double[] {value}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solve(
                                    new double[][] {{1}}, new double[] {1}, new double[] {value}));
        }
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> BoundedLinearProgram.solve(null, new double[] {1}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solve(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solve(
                                new double[513][1], new double[513], new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solve(
                                new double[1][513], new double[] {1}, new double[513]));
    }

    @Test
    void workLimitsFailClosedAndTheSolvedResultIsImmutableAndBoundToOriginalInputs()
            throws Exception {
        double[][] a = {{1}};
        double[] b = {2}, c = {1};
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () -> BoundedLinearProgram.solve(a, b, c, 0, 10000))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () -> BoundedLinearProgram.solve(a, b, c, 100, 0))
                        .reason());
        var solved = BoundedLinearProgram.solve(a, b, c);
        assertEquals(solved.certificate(), BoundedLinearProgram.solve(a, b, c).certificate());
        a[0][0] = 2;
        b[0] = 4;
        c[0] = 2;
        assertEquals(List.of(2.0), solved.point());
        assertNotEquals(
                solved.certificate().problemHash(),
                BoundedLinearProgram.solve(a, b, c).certificate().problemHash());
        assertThrows(UnsupportedOperationException.class, () -> solved.point().set(0, 4.0));
        assertThrows(UnsupportedOperationException.class, () -> solved.dual().clear());
        assertTrue(
                Arrays.stream(BoundedLinearProgram.Result.class.getDeclaredConstructors())
                        .allMatch(k -> java.lang.reflect.Modifier.isPrivate(k.getModifiers())));
    }
}
