package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class RefinedLpDirectionsTest {
    @Test
    void newExplicitModeMatchesIndependentOracleControlsAndOriginalCertificates() throws Exception {
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
            var result = BoundedLinearProgram.solveRefinedDirections(p.matrix(), p.rhs(), p.cost());
            assertEquals(
                    BoundedLinearProgram.REFINED_DIRECTION_ALGORITHM,
                    result.certificate().algorithm());
            assertEquals(
                    p.expectedMaximum(),
                    result.certificate().primalValue(),
                    1e-8,
                    p.group() + ":" + p.id());
            checkOriginal(p.matrix(), p.rhs(), p.cost(), result);
        }
    }

    static void checkOriginal(
            double[][] matrix, double[] rhs, double[] cost, BoundedLinearProgram.Result solved) {
        double primal = 0, dual = 0;
        for (int j = 0; j < cost.length; j++) {
            assertTrue(Double.isFinite(solved.point().get(j)) && solved.point().get(j) >= -1e-8);
            primal += cost[j] * solved.point().get(j);
            double reduced = 0;
            for (int i = 0; i < rhs.length; i++) reduced += matrix[i][j] * solved.dual().get(i);
            assertTrue(reduced >= cost[j] - 1e-8);
        }
        for (int i = 0; i < rhs.length; i++) {
            assertTrue(Double.isFinite(solved.dual().get(i)) && solved.dual().get(i) >= -1e-8);
            double value = 0;
            for (int j = 0; j < cost.length; j++) value += matrix[i][j] * solved.point().get(j);
            assertTrue(value <= rhs[i] + 1e-8);
            dual += rhs[i] * solved.dual().get(i);
        }
        assertEquals(primal, dual, 1e-8);
        assertEquals(primal, solved.certificate().primalValue(), 1e-12);
        assertEquals(dual, solved.certificate().dualValue(), 1e-12);
    }

    @Test
    void fixedPassesAreChargedToTheSameCapAndOldModeStaysExplicit() throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var old = BoundedLinearProgram.solveExactPowerOfTwoRows(a, b, c);
        var refined = BoundedLinearProgram.solveRefinedDirections(a, b, c);
        assertEquals(2, BoundedLinearProgram.DIRECTION_REFINEMENT_PASSES);
        assertEquals(BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM, old.certificate().algorithm());
        assertEquals(
                BoundedLinearProgram.REFINED_DIRECTION_ALGORITHM,
                refined.certificate().algorithm());
        long spent = refined.certificate().work().arithmeticWork();
        assertTrue(spent > old.certificate().work().arithmeticWork());
        assertEquals(
                refined.certificate(),
                BoundedLinearProgram.solveRefinedDirections(a, b, c, 10000, spent).certificate());
        var failure =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                BoundedLinearProgram.solveRefinedDirections(
                                        a, b, c, 10000, spent - 1));
        assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, failure.reason());
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> BoundedLinearProgram.solveRefinedDirections(a, b, c, 0, 4_000_000_000L));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> BoundedLinearProgram.solveRefinedDirections(a, b, c, 10001, 4_000_000_000L));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> BoundedLinearProgram.solveRefinedDirections(a, b, c, 10000, 4_000_000_001L));
    }

    @Test
    void originalInfeasibleUnboundedAndBadInputsNeverYieldACertificate() {
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveRefinedDirections(
                                new double[][] {{1}, {-1}},
                                new double[] {0, -1},
                                new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveRefinedDirections(
                                new double[][] {{-1}}, new double[] {0}, new double[] {1}));
        for (double value :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1_000_001
                })
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            BoundedLinearProgram.solveRefinedDirections(
                                    new double[][] {{value}}, new double[] {1}, new double[] {1}));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        BoundedLinearProgram.solveRefinedDirections(
                                new double[][] {{1, 2}}, new double[] {1}, new double[] {1}));
    }

    @Test
    void oldPhysicalModeStillRejectsBothRecordedCasesAndNewModeCertifiesAllEndpoints()
            throws Exception {
        var input =
                SixMaxSuppliedFlopBettingStudy.readRequest(
                        Path.of(
                                "../docs/data/supplied-four-button-hand-connected-flop-request.json"));
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        for (String hand : List.of("Ah Kh", "Qh Th")) {
            var question = SixMaxBtnFlopFeedback.question(game, hand, "|BB:call");
            var check =
                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                            3, question.informationSet(), "check");
            var rejected =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () ->
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .solvePhysicalFlopExactPowerOfTwo(
                                                    game, check, 1e-8, .0001));
            assertTrue(rejected.getMessage().contains("Singular LP basis"));
            for (String action : List.of("check", "bet")) {
                var solved =
                        FiniteTwoPlayerConditionalActionIntervals
                                .solvePhysicalFlopRefinedDirections(
                                        game,
                                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                3, question.informationSet(), action),
                                        1e-8,
                                        .0001);
                assertEquals(
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_REFINED_DIRECTION_ALGORITHM,
                        solved.audit().algorithm());
                assertEquals(
                        BoundedLinearProgram.REFINED_DIRECTION_ALGORITHM,
                        solved.audit().lowerWitness().certificate().algorithm());
                assertTrue(solved.audit().certifiedReachLowerBound() > .249);
                assertTrue(solved.audit().work().intervalLpArithmeticWork() <= 4_000_000_000L);
                assertTrue(solved.audit().work().intervalLpPivots() <= 10000);
            }
        }
    }
}
