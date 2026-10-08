package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteMatrixMaxminTest {
    @Test
    void agreesWithSavedIndependentHighsControlsAndTheirOriginalMatrixCertificates()
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var root =
                mapper.readTree(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/finite-maxmin-independent-controls.json.gz"),
                                1_048_576));
        assertEquals("finite-maxmin-independent-controls/v1", root.path("schemaVersion").asText());
        assertEquals(120, root.path("controls").size());
        for (var control : root.path("controls")) {
            double[][] matrix = mapper.treeToValue(control.path("values"), double[][].class);
            double[] p = mapper.treeToValue(control.path("rowMixture"), double[].class);
            double[] q = mapper.treeToValue(control.path("columnMixture"), double[].class);
            double lower = Double.POSITIVE_INFINITY, upper = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < q.length; c++) {
                double value = 0;
                for (int r = 0; r < p.length; r++) value += p[r] * matrix[r][c];
                lower = Math.min(lower, value);
            }
            for (int r = 0; r < p.length; r++) {
                double value = 0;
                for (int c = 0; c < q.length; c++) value += q[c] * matrix[r][c];
                upper = Math.max(upper, value);
            }
            assertEquals(1, Arrays.stream(p).sum(), 1e-12);
            assertEquals(1, Arrays.stream(q).sum(), 1e-12);
            assertTrue(Arrays.stream(p).allMatch(x -> x >= 0));
            assertTrue(Arrays.stream(q).allMatch(x -> x >= 0));
            assertTrue(upper - lower <= 1e-8);
            assertEquals(control.path("lowerValue").asDouble(), lower, 1e-10);
            assertEquals(control.path("upperValue").asDouble(), upper, 1e-10);
            var owned = FiniteMatrixMaxmin.solve(matrix);
            assertEquals(lower, owned.lowerValue(), 1e-8, control.path("name").asText());
            assertEquals(upper, owned.upperValue(), 1e-8, control.path("name").asText());
        }
    }

    private static void value(double[][] matrix, double expected) {
        var result = FiniteMatrixMaxmin.solve(matrix);
        assertEquals(expected, result.lowerValue(), 1e-9);
        assertEquals(expected, result.upperValue(), 1e-9);
        assertEquals(1, result.rowMixture().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(
                1, result.columnMixture().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }

    @Test
    void matchingPenniesAndRockPaperScissorsRequireMixedStrategies() {
        var pennies = FiniteMatrixMaxmin.solve(new double[][] {{1, -1}, {-1, 1}});
        for (double p : pennies.rowMixture()) assertEquals(.5, p, 1e-12);
        for (double p : pennies.columnMixture()) assertEquals(.5, p, 1e-12);
        var rps = FiniteMatrixMaxmin.solve(new double[][] {{0, -1, 1}, {1, 0, -1}, {-1, 1, 0}});
        for (double p : rps.rowMixture()) assertEquals(1.0 / 3, p, 1e-12);
        for (double p : rps.columnMixture()) assertEquals(1.0 / 3, p, 1e-12);
        assertEquals(0, rps.lowerValue(), 1e-12);
    }

    @Test
    void handlesDominanceNegativeConstantsRectanglesAndDegeneracy() {
        value(new double[][] {{1, 2}, {0, 1}}, 1);
        value(new double[][] {{-7, -7, -7}, {-7, -7, -7}}, -7);
        value(new double[][] {{-3, 4, 1}}, -3);
        value(new double[][] {{-3}, {4}, {1}}, 4);
        value(new double[][] {{2, -2, 2}, {-2, 2, -2}, {2, -2, 2}}, 0);
    }

    @Test
    void agreesWithIndependentClosedFormForSeededTwoByTwoGames() {
        var random = new Random(261);
        for (int i = 0; i < 120; i++) {
            double a = random.nextDouble() * 20 + 1,
                    b = -random.nextDouble() * 20 - 1,
                    c = -random.nextDouble() * 20 - 1,
                    d = random.nextDouble() * 20 + 1;
            double denominator = a - b - c + d;
            double expected = (a * d - b * c) / denominator;
            var result = FiniteMatrixMaxmin.solve(new double[][] {{a, b}, {c, d}});
            assertEquals(expected, result.lowerValue(), 1e-9);
            assertEquals((d - c) / denominator, result.rowMixture().getFirst(), 1e-9);
            assertEquals((d - b) / denominator, result.columnMixture().getFirst(), 1e-9);
        }
    }

    @Test
    void additiveGamesAndAffineTransformsHaveIndependentValues() {
        var random = new Random(711);
        for (int trial = 0; trial < 60; trial++) {
            int m = 1 + random.nextInt(24), n = 1 + random.nextInt(24);
            double[] rows = random.doubles(m, -100, 100).toArray(),
                    cols = random.doubles(n, -100, 100).toArray();
            double[][] values = new double[m][n];
            for (int r = 0; r < m; r++)
                for (int c = 0; c < n; c++) values[r][c] = rows[r] + cols[c];
            value(
                    values,
                    Arrays.stream(rows).max().orElseThrow()
                            + Arrays.stream(cols).min().orElseThrow());
        }
        value(new double[][] {{104, 96}, {96, 104}}, 100);
    }

    @Test
    void rejectsMalformedUnboundedAndNonfiniteInputsWithoutMutatingTheMatrix() {
        for (double[][] values :
                List.of(
                        new double[0][],
                        new double[][] {{}},
                        new double[][] {{1}, {1, 2}},
                        new double[][] {{Double.NaN}},
                        new double[][] {{Double.POSITIVE_INFINITY}},
                        new double[][] {{1_000_001}},
                        new double[65][1],
                        new double[1][65]))
            assertThrows(IllegalArgumentException.class, () -> FiniteMatrixMaxmin.solve(values));
        double[][] values = {{1, -1}, {-1, 1}};
        var solved = FiniteMatrixMaxmin.solve(values);
        assertArrayEquals(new double[] {1, -1}, values[0]);
        assertThrows(UnsupportedOperationException.class, () -> solved.rowMixture().set(0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FiniteMatrixMaxmin.Solution(List.of(.9), List.of(1.0), 0, 0, 0));
    }
}
