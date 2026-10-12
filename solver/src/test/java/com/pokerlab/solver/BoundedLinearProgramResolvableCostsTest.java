package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class BoundedLinearProgramResolvableCostsTest {
    record OriginalProblem(double[][] matrix, double[] rhs, double[] cost) {}

    static void check(
            double[][] a,
            double[] b,
            double[] c,
            double expected,
            BoundedLinearProgram.Result result)
            throws Exception {
        var cert = result.certificate();
        assertEquals(BoundedLinearProgram.RESOLVABLE_COST_ALGORITHM, cert.algorithm());
        assertEquals(
                SixMaxHistoryPhysicalConditionalRefinement.hash(new OriginalProblem(a, b, c)),
                cert.problemHash());
        assertEquals(expected, cert.primalValue(), 1e-8);
        double primal = 0, dual = 0;
        for (int j = 0; j < c.length; j++) {
            double x = result.point().get(j), column = 0;
            assertTrue(Double.isFinite(x) && x >= 0);
            primal += c[j] * x;
            for (int i = 0; i < b.length; i++) column += a[i][j] * result.dual().get(i);
            assertTrue(column >= c[j] - 1e-8);
        }
        for (int i = 0; i < b.length; i++) {
            double y = result.dual().get(i), row = 0;
            assertTrue(Double.isFinite(y) && y >= -1e-8);
            dual += b[i] * y;
            for (int j = 0; j < c.length; j++) row += a[i][j] * result.point().get(j);
            assertTrue(row <= b[i] + 1e-8);
        }
        assertEquals(primal, dual, 1e-8);
        assertEquals(primal, cert.primalValue());
        assertEquals(dual, cert.dualValue());
        assertTrue(cert.work().arithmeticWork() <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    @Test
    void independentOptimaSurviveFourOriginalRowRepresentations() throws Exception {
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
        for (var p : controls.problems())
            for (int trial = 0; trial < 4; trial++) {
                var random = new SplittableRandom(261L + 17L * p.id() + trial);
                var order = new ArrayList<Integer>();
                for (int i = 0; i < p.rhs().length; i++) order.add(i);
                if (trial == 1 || trial == 2)
                    for (int i = order.size() - 1; i > 0; i--)
                        Collections.swap(order, i, random.nextInt(i + 1));
                double[][] a = new double[order.size()][];
                double[] b = new double[order.size()];
                for (int i = 0; i < order.size(); i++) {
                    double scale = trial == 3 ? Math.scalb(1.0, random.nextInt(-4, 5)) : 1;
                    a[i] = p.matrix()[order.get(i)].clone();
                    for (int j = 0; j < a[i].length; j++) a[i][j] *= scale;
                    b[i] = p.rhs()[order.get(i)] * scale;
                }
                check(
                        a,
                        b,
                        p.cost(),
                        p.expectedMaximum(),
                        BoundedLinearProgram.solveResolvableCosts(a, b, p.cost()));
            }
    }

    @Test
    void tinyBoundedObjectiveIsSolvedRatherThanDiscardedAndDefaultsStillReject() throws Exception {
        double[][] a = {{1}};
        double[] b = {1_000_000}, c = {1e-11};
        assertThrows(
                BoundedLinearProgram.Rejected.class, () -> BoundedLinearProgram.solve(a, b, c));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> BoundedLinearProgram.solveAscendingL1Rows(a, b, c));
        var solved = BoundedLinearProgram.solveResolvableCosts(a, b, c);
        check(a, b, c, 1e-5, solved);
        assertEquals(1_000_000, solved.point().getFirst());
        assertEquals(1e-11, solved.dual().getFirst());
        assertEquals(
                solved.certificate(),
                BoundedLinearProgram.solveResolvableCosts(a, b, c).certificate());
    }

    @Test
    void tinyAndCancellationUnboundedProblemsStillReturnNoHandle() {
        for (double bound : new double[] {0, 1_000_000})
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveResolvableCosts(
                                    new double[][] {{-1}},
                                    new double[] {bound},
                                    new double[] {1e-11}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableCosts(
                                new double[][] {{1, -1}},
                                new double[] {0},
                                new double[] {1, -1 + 1e-11}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableCosts(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
    }

    @Test
    void nearRatioTieChoosesActualSmallerStepAndMappedOriginalDual() throws Exception {
        double[][] a = {{1}, {1}};
        double[] b = {1, 1 - 1e-13}, c = {1};
        var solved = BoundedLinearProgram.solveResolvableCosts(a, b, c);
        check(a, b, c, b[1], solved);
        assertEquals(b[1], solved.point().getFirst());
        assertEquals(List.of(0.0, 1.0), solved.dual());
    }

    @Test
    void originalLimitsCopyingAndPrivateResultOwnershipRemainEnforced() throws Exception {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableCosts(
                                new double[][] {{1}},
                                new double[] {1},
                                new double[] {1},
                                0,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableCosts(
                                new double[][] {{1}}, new double[] {1}, new double[] {1}, 100, 0));
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1_000_001})
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveResolvableCosts(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
        double[][] a = {{10, 0}, {0, 1}, {1, 1}};
        double[] b = {20, 3, 4}, c = {3, 1};
        var solved = BoundedLinearProgram.solveResolvableCosts(a, b, c);
        check(a, b, c, 8, solved);
        a[0][0] = 999;
        b[0] = 999;
        c[0] = 999;
        assertEquals(8, solved.certificate().primalValue());
        assertThrows(UnsupportedOperationException.class, () -> solved.point().set(0, 7.0));
        assertTrue(
                Arrays.stream(BoundedLinearProgram.Result.class.getDeclaredConstructors())
                        .allMatch(k -> java.lang.reflect.Modifier.isPrivate(k.getModifiers())));
    }
}
