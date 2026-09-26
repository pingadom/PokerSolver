package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectedChanceCalibrationTest {
    @Test
    void seededSearchIsReproducibleAndStillRequiresUnseenComboValidation() {
        var oracle = new ExactPreflopEquityOracle();
        var first = ConnectedChanceCalibration.search(42, 10, oracle);
        var repeated = ConnectedChanceCalibration.search(42, 10, oracle);
        assertEquals(10, first.acceptedCandidates());
        assertEquals(first.flops(), repeated.flops());
        assertEquals(first.turns(), repeated.turns());
        assertEquals(first.audit().gameHash(), repeated.audit().gameHash());
        assertEquals(
                first.audit().maxAbsoluteDealErrorBb(), repeated.audit().maxAbsoluteDealErrorBb());
        assertEquals(
                "eeda6f24a4e705adc8de896d8216cc5d6b2195163284c2ebf3cbfded8e1e1b3f",
                first.audit().gameHash());
        assertTrue(first.audit().maxAbsoluteDealErrorBb() < 1);

        var holdout =
                ConnectedChanceAudit.assess(
                        ButtonBigBlindResearchFixture.holdout(first.flops(), first.turns()),
                        oracle);
        assertNotEquals(first.audit().signedErrorBb(), holdout.signedErrorBb());
        assertEquals(0.490469, holdout.maxAbsoluteDealErrorBb(), 1e-6);
        assertThrows(
                IllegalArgumentException.class,
                () -> ConnectedChanceCalibration.search(42, 0, oracle));
    }
}
