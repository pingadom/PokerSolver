package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class ResolvableRefinedLpTest {
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
                                ResolvableRefinedLpTest.class.getResourceAsStream(
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
        var result = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        assertEquals(b[1] / a[1][0], result.point().getFirst(), 1e-25);
        assertEquals(
                BoundedLinearProgram.RESOLVABLE_REFINED_BASIS_ALGORITHM,
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
                    BoundedLinearProgram.solveResolvableRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(p.expectedMaximum(), solved.certificate().primalValue(), 1e-8, p.id());
            assertEquals(81, solved.certificate().variables());
            assertEquals(86, solved.certificate().constraints());
            assertEquals(
                    BoundedLinearProgram.RESOLVABLE_REFINED_BASIS_ALGORITHM,
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
                    BoundedLinearProgram.solveResolvableRefinedBasis(p.matrix(), p.rhs(), p.cost());
            assertEquals(
                    p.expectedMaximum(),
                    solved.certificate().primalValue(),
                    1e-8,
                    p.group() + ":" + p.id());
            assertEquals(
                    BoundedLinearProgram.RESOLVABLE_REFINED_BASIS_ALGORITHM,
                    solved.certificate().algorithm());
            RefinedLpDirectionsTest.checkOriginal(p.matrix(), p.rhs(), p.cost(), solved);
        }
    }

    @Test
    void fixedRefinementUsesTheOriginalBudgetAndCannotRaiseLimits() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var old = BoundedLinearProgram.solveAscendingL1Rows(a, b, c);
        var refined = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        assertEquals(BoundedLinearProgram.ASCENDING_L1_ALGORITHM, old.certificate().algorithm());
        assertEquals(2, BoundedLinearProgram.DIRECTION_REFINEMENT_PASSES);
        long spent = refined.certificate().work().arithmeticWork();
        assertTrue(spent > old.certificate().work().arithmeticWork());
        assertEquals(
                refined.certificate(),
                BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c, 10000, spent)
                        .certificate());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                                a, b, c, 10000, spent - 1))
                        .reason());
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                                a, b, c, 0, 4_000_000_000L))
                        .reason());
        for (long work : new long[] {-1, 4_000_000_001L})
            assertEquals(
                    BoundedLinearProgram.Failure.INVALID_INPUT,
                    assertThrows(
                                    BoundedLinearProgram.Rejected.class,
                                    () ->
                                            BoundedLinearProgram.solveResolvableRefinedBasis(
                                                    a, b, c, 10000, work))
                            .reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                a, b, c, 10001, 4_000_000_000L));
    }

    @Test
    void infeasibleUnboundedUnresolvedAndInvalidInputsFailClosed() {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1e-11}));
        for (double value :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1_000_001
                }) {
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveResolvableRefinedBasis(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveResolvableRefinedBasis(
                                    new double[][] {{1}}, new double[] {value}, new double[] {1}));
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveResolvableRefinedBasis(
                                    new double[][] {{1}}, new double[] {1}, new double[] {value}));
        }
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveResolvableRefinedBasis(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
    }

    @Test
    void successiveEndpointsShareTheSameCumulativeBudget() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var one = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        long work = one.certificate().work().arithmeticWork();
        int pivots = one.certificate().work().pivots();
        var budget =
                FiniteTwoPlayerRootActionIntervals.LpBudget.resolvableRefinedBasis(
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

    static JsonNode weakProblem() throws Exception {
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                ResolvableRefinedLpTest.class.getResourceAsStream(
                                        "/lp-resolvable-refined-weak-hand.json.gz")))) {
            return SixMaxTexturePayoffTable.mapper().readTree(input);
        }
    }

    @Test
    void capturedWeakHandRejectionIsFixedOnlyByTheExplicitNewMode() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var p = weakProblem();
        var a = m.treeToValue(p.get("matrix"), double[][].class);
        var b = m.treeToValue(p.get("rhs"), double[].class);
        var c = m.treeToValue(p.get("cost"), double[].class);
        var rejected =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () -> BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c));
        assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, rejected.reason());
        assertEquals("Unresolved positive LP reduced cost", rejected.getMessage());
        var result = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        assertEquals(102.48663656218744, result.certificate().primalValue(), 1e-8);
        assertEquals(
                BoundedLinearProgram.RESOLVABLE_REFINED_BASIS_ALGORITHM,
                result.certificate().algorithm());
        RefinedLpDirectionsTest.checkOriginal(a, b, c, result);
        assertEquals(
                result.certificate(),
                BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c).certificate());
        var point = result.point();
        var dual = result.dual();
        a[0][0] = 999;
        b[0] = 999;
        c[0] = 999;
        assertSame(point, result.point());
        assertSame(dual, result.dual());
        assertThrows(UnsupportedOperationException.class, () -> point.set(0, 999.0));
        assertThrows(UnsupportedOperationException.class, () -> dual.set(0, 999.0));
        assertTrue(
                Arrays.stream(BoundedLinearProgram.Result.class.getDeclaredConstructors())
                        .allMatch(v -> java.lang.reflect.Modifier.isPrivate(v.getModifiers())));
    }

    @Test
    void smallResolvablePositiveCostCannotBeMistakenForAnOptimumOrUnboundedness() throws Exception {
        double[][] a = {{1}};
        double[] b = {1_000_000}, c = {1e-11};
        assertEquals(
                "Unresolved positive LP reduced cost",
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () -> BoundedLinearProgram.solveAscendingRefinedBasis(a, b, c))
                        .getMessage());
        var r = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        assertEquals(1_000_000, r.point().getFirst());
        assertEquals(1e-11, r.dual().getFirst());
        assertEquals(1e-5, r.certificate().primalValue(), 1e-15);
        RefinedLpDirectionsTest.checkOriginal(a, b, c, r);
    }

    static Map<String, Object> row(
            String origin, String id, double[][] a, double[] b, double[] c, double expected)
            throws Exception {
        var r = BoundedLinearProgram.solveResolvableRefinedBasis(a, b, c);
        RefinedLpDirectionsTest.checkOriginal(a, b, c, r);
        assertEquals(expected, r.certificate().primalValue(), 1e-8);
        return Map.of(
                "origin",
                origin,
                "id",
                id,
                "point",
                r.point(),
                "dual",
                r.dual(),
                "certificate",
                r.certificate());
    }

    static Map<String, Object> evidence() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var rows = new ArrayList<Map<String, Object>>();
        try (var in =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                ResolvableRefinedLpTest.class.getResourceAsStream(
                                        "/bounded-lp-oracle-controls.json.gz")))) {
            var controls = m.readValue(in, BoundedLinearProgramTest.Controls.class);
            for (var p : controls.problems())
                rows.add(
                        row(
                                "ORACLE",
                                p.group() + ":" + p.id(),
                                p.matrix(),
                                p.rhs(),
                                p.cost(),
                                p.expectedMaximum()));
        }
        for (var p : captured().cases())
            rows.add(
                    row(
                            "CAPTURED",
                            ":" + p.id(),
                            p.matrix(),
                            p.rhs(),
                            p.cost(),
                            p.expectedMaximum()));
        rows.add(
                row(
                        "NEAR_RATIO",
                        "two-distinct-positive-steps",
                        new double[][] {{1}, {35491.780049572175}},
                        new double[] {1.0236165219481362e-12, 2.2032117954153633e-8},
                        new double[] {1},
                        2.2032117954153633e-8 / 35491.780049572175));
        var p = weakProblem();
        rows.add(
                row(
                        "WEAK_HAND",
                        "call-lower",
                        m.treeToValue(p.get("matrix"), double[][].class),
                        m.treeToValue(p.get("rhs"), double[].class),
                        m.treeToValue(p.get("cost"), double[].class),
                        102.48663656218744));
        rows.add(
                row(
                        "SMALL_PRICING",
                        "resolvable-positive",
                        new double[][] {{1}},
                        new double[] {1_000_000},
                        new double[] {1e-11},
                        1e-5));
        assertEquals(298, rows.size());
        return Map.of(
                "schemaVersion",
                "pokerlab-resolvable-refined-lp-evidence/v1",
                "scope",
                "ORIGINAL_LP_REGRESSIONS_NOT_PHYSICAL_OR_TRAINER_QUALIFICATION",
                "trainerAdmission",
                false,
                "cases",
                rows);
    }

    @Test
    void everySavedOriginalWitnessAndWorkCounterIsReproduced() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        try (var in =
                new GZIPInputStream(
                        Files.newInputStream(
                                Path.of("../docs/data/resolvable-refined-lp-evidence.json.gz")))) {
            assertEquals(m.readTree(in), m.readTree(SixMaxTextureStudy.json(evidence())));
        }
    }

    public static void main(String[] args) throws Exception {
        try (var out =
                new java.util.zip.GZIPOutputStream(
                        Files.newOutputStream(Path.of(args[0]), StandardOpenOption.CREATE_NEW))) {
            out.write(
                    SixMaxTextureStudy.json(evidence())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
