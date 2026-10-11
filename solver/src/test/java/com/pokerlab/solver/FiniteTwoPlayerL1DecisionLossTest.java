package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerConditionalDecisionLossTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerL1DecisionLossTest {
    @Test
    void bothActorsAndContinuationPlansKeepIndependentActionBoundsAndAnalyticJointExtrema()
            throws Exception {
        for (int hero : List.of(3, 5))
            for (int mode : List.of(0, 1, 2)) {
                var game = correlated(hero, mode);
                var old =
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                        game, question(hero), 1e-8, .01)
                                .audit();
                var result =
                        FiniteTwoPlayerConditionedDecisionLoss.solveAscendingL1JointRows(
                                        game, question(hero), 1e-8, .01)
                                .audit();
                assertEquals(
                        FiniteTwoPlayerConditionedDecisionLoss.L1_REPRESENTATION,
                        result.representation());
                assertEquals(
                        FiniteTwoPlayerConditionalDecisionLoss.L1_CONDITIONED_ALGORITHM,
                        result.joint().algorithm());
                assertEquals(old.upperObjectiveShift(), result.upperObjectiveShift());
                assertEquals(old.joint().actions(), result.joint().actions());
                assertFalse(result.joint().trainerAdmission());
                for (int i = 0; i < old.joint().moves().size(); i++) {
                    var before = old.joint().moves().get(i);
                    var after = result.joint().moves().get(i);
                    assertEquals(before.lowerLoss(), after.lowerLoss(), 1e-9);
                    assertEquals(before.upperLoss(), after.upperLoss(), 1e-9);
                    for (var controls : List.of(after.lowerControls(), after.upperControls()))
                        for (var control : controls) {
                            var w = control.witness().selected();
                            assertEquals(
                                    BoundedLinearProgram.ASCENDING_L1_ALGORITHM,
                                    w.certificate().algorithm());
                            assertTrue(w.certificate().maximumPrimalViolation() <= 1e-8);
                            assertTrue(w.certificate().maximumDualViolation() <= 1e-8);
                            assertTrue(w.certificate().absoluteDualityGap() <= 1e-8);
                            assertTrue(w.posteriorTotalVariation() <= 1e-8);
                        }
                }
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new FiniteTwoPlayerConditionedDecisionLoss.Audit(
                                        FiniteTwoPlayerConditionedDecisionLoss.REPRESENTATION,
                                        result.upperObjectiveShift(),
                                        result.joint()));
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new FiniteTwoPlayerConditionedDecisionLoss.Audit(
                                        FiniteTwoPlayerConditionedDecisionLoss.L1_REPRESENTATION,
                                        old.upperObjectiveShift(),
                                        old.joint()));
            }
    }

    @Test
    void originalActionControlsAndOrderedJointControlsShareAllAggregateCaps() throws Exception {
        var game = correlated(5, 1);
        var work =
                FiniteTwoPlayerConditionedDecisionLoss.solveAscendingL1JointRows(
                                game, question(5), 1e-8, .01)
                        .audit()
                        .joint()
                        .work();
        var exact =
                FiniteTwoPlayerConditionedDecisionLoss.solve(
                        game,
                        question(5),
                        1e-8,
                        .01,
                        work.compilerUnits(),
                        work.intervalLpPivots(),
                        work.intervalLpArithmeticWork(),
                        true);
        var exactWork = exact.audit().joint().work();
        assertEquals(work.compilerUnits(), exactWork.compilerUnits());
        assertEquals(work.intervalLpArithmeticWork(), exactWork.intervalLpArithmeticWork());
        assertEquals(work.intervalLpPivots(), exactWork.intervalLpPivots());
        assertEquals(work.intervalLpSolves(), exactWork.intervalLpSolves());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                game,
                                question(5),
                                1e-8,
                                .01,
                                work.compilerUnits() - 1,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                                true));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                game,
                                question(5),
                                1e-8,
                                .01,
                                8_000_000,
                                work.intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                                true));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                game,
                                question(5),
                                1e-8,
                                .01,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                work.intervalLpArithmeticWork() - 1,
                                true));
    }

    @Test
    void lowReachAndInvalidDeclaredLimitsStillReject() {
        var low =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionedDecisionLoss.solveAscendingL1JointRows(
                                        FiniteTwoPlayerConditionalActionIntervalsTest.posteriorGame(
                                                5, false, false),
                                        question(5),
                                        1e-8,
                                        .01));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH, low.reason());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionedDecisionLoss.solveAscendingL1JointRows(
                                null, question(5), 1e-3, .01));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalDecisionLoss.solve(
                                null,
                                question(5),
                                1e-8,
                                .01,
                                8_000_000,
                                100,
                                1000000,
                                false,
                                true));
    }
}
