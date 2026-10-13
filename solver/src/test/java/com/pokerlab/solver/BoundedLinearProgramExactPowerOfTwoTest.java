package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class BoundedLinearProgramExactPowerOfTwoTest {
    record OriginalProblem(double[][] matrix, double[] rhs, double[] cost) {}

    record FlopProblem(
            int id, double[][] matrix, double[] rhs, double[] cost, double expectedMaximum) {}

    record FlopControls(List<FlopProblem> problems) {}

    private static void checkOriginal(
            double[][] a,
            double[] b,
            double[] c,
            double expected,
            BoundedLinearProgram.Result result)
            throws Exception {
        var certificate = result.certificate();
        assertEquals(BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM, certificate.algorithm());
        assertEquals(
                SixMaxHistoryPhysicalConditionalRefinement.hash(new OriginalProblem(a, b, c)),
                certificate.problemHash());
        assertEquals(expected, certificate.primalValue(), 1e-8);
        double primal = 0, dual = 0;
        for (int j = 0; j < c.length; j++) {
            double x = result.point().get(j), column = 0;
            assertTrue(Double.isFinite(x) && x >= -1e-8);
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
        assertEquals(primal, certificate.primalValue());
        assertEquals(dual, certificate.dualValue());
        assertTrue(certificate.work().pivots() <= BoundedLinearProgram.MAX_PIVOTS);
        assertTrue(certificate.work().arithmeticWork() <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    @Test
    void previouslyRejectedPhysicalFlopEndpointsNowHaveOriginalCertificates() throws Exception {
        FlopControls controls;
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                getClass()
                                        .getResourceAsStream(
                                                "/bounded-lp-flop-equilibration-controls.json.gz")))) {
            controls = SixMaxTexturePayoffTable.mapper().readValue(input, FlopControls.class);
        }
        assertEquals(
                List.of(3, 6, 11, 16), controls.problems().stream().map(FlopProblem::id).toList());
        for (var problem : controls.problems()) {
            var result =
                    BoundedLinearProgram.solveExactPowerOfTwoRows(
                            problem.matrix(), problem.rhs(), problem.cost());
            checkOriginal(
                    problem.matrix(),
                    problem.rhs(),
                    problem.cost(),
                    problem.expectedMaximum(),
                    result);
            if (problem.id() == 6) assertEquals(0, result.certificate().primalValue(), 1e-8);
        }
    }

    @Test
    void independentOraclesSurviveFourRepresentations() throws Exception {
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
                checkOriginal(
                        a,
                        b,
                        p.cost(),
                        p.expectedMaximum(),
                        BoundedLinearProgram.solveExactPowerOfTwoRows(a, b, p.cost()));
            }
    }

    @Test
    void scalingPreservesBitsOrReturnsNoResult() {
        for (double value : new double[] {-0.0, 0.0, Double.MIN_VALUE, Double.MIN_NORMAL, -1e-11}) {
            double scaled = BoundedLinearProgram.exactPowerOfTwoScale(value, 2);
            assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(scaled / 2));
        }
        assertEquals(
                BoundedLinearProgram.Failure.NUMERICAL_FAILURE,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.exactPowerOfTwoScale(
                                                Double.MIN_VALUE, 0.5))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.NUMERICAL_FAILURE,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.exactPowerOfTwoScale(
                                                1_000_000, Double.MAX_VALUE))
                        .reason());
        for (double factor : new double[] {0, -1, 3, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(
                    BoundedLinearProgram.Failure.NUMERICAL_FAILURE,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () -> BoundedLinearProgram.exactPowerOfTwoScale(1, factor))
                            .reason());
    }

    @Test
    void originalCapsOwnershipAndNonOptimaRemainEnforced() throws Exception {
        double[][] a = {{10, 0}, {0, 1}, {1, 1}};
        double[] b = {20, 3, 4}, c = {3, 1};
        var result = BoundedLinearProgram.solveExactPowerOfTwoRows(a, b, c);
        checkOriginal(a, b, c, 8, result);
        long exactWork = result.certificate().work().arithmeticWork();
        int exactPivots = result.certificate().work().pivots();
        assertEquals(
                result.certificate(),
                BoundedLinearProgram.solveExactPowerOfTwoRows(a, b, c, exactPivots, exactWork)
                        .certificate());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveExactPowerOfTwoRows(
                                                a, b, c, exactPivots, exactWork - 1))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.INVALID_INPUT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveExactPowerOfTwoRows(
                                                a,
                                                b,
                                                c,
                                                BoundedLinearProgram.MAX_PIVOTS + 1,
                                                exactWork))
                        .reason());
        a[0][0] = 999;
        b[0] = 999;
        c[0] = 999;
        assertEquals(8, result.certificate().primalValue());
        assertThrows(UnsupportedOperationException.class, () -> result.dual().set(0, 1.0));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1_000_001})
            assertEquals(
                    BoundedLinearProgram.Failure.INVALID_INPUT,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () ->
                                            BoundedLinearProgram.solveExactPowerOfTwoRows(
                                                    new double[][] {{invalid}},
                                                    new double[] {1},
                                                    new double[] {1}))
                            .reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveExactPowerOfTwoRows(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveExactPowerOfTwoRows(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
    }
}
