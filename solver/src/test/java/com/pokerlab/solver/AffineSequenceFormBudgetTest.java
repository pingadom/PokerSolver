package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AffineSequenceFormBudgetTest {
    @Test
    void exactCompilerBudgetAcceptsAndExhaustedOrRaisedBudgetsCannotReturnAHandle()
            throws Exception {
        var game =
                new AffineSequenceFormEdgeTest.Game(
                        new int[] {5, 2}, new int[] {2, 2}, -3.25, .75, 6.5, false);
        var baseline = FiniteTwoPlayerAffineSequenceForm.solve(game);
        long work = baseline.audit().reductionWork().chargedUnits();
        for (long limit : new long[] {0, work - 1}) {
            var failure =
                    assertThrows(
                            FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                            () -> FiniteTwoPlayerAffineSequenceForm.solve(game, limit));
            assertEquals(FiniteTwoPlayerAffineSequenceForm.Failure.WORK_LIMIT, failure.reason());
        }
        var exact = FiniteTwoPlayerAffineSequenceForm.solve(game, work);
        assertEquals(work, exact.audit().reductionWork().chargedUnits());
        assertEquals(-3.25, exact.audit().lowerValue(), 1e-10);
        for (long limit :
                new long[] {-1, FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK + 1}) {
            var failure =
                    assertThrows(
                            FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                            () -> FiniteTwoPlayerAffineSequenceForm.solve(game, limit));
            assertEquals(FiniteTwoPlayerAffineSequenceForm.Failure.INVALID_INPUT, failure.reason());
        }
        assertThrows(
                UnsupportedOperationException.class,
                () -> baseline.audit().firstProjection().transform().getFirst().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> baseline.audit().projectedPayoff().matrix().getFirst().clear());
        assertTrue(external.audit.client.AffinePublicAuditClient.inspect(baseline) >= work);
    }
}
