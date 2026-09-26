package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectedChanceAuditTest {
    @Test
    void exactReferenceIsStableWhileTheDeclaredChanceMenusChange() {
        var oracle = new ExactPreflopEquityOracle();
        var narrowGame = ButtonBigBlindResearchFixture.create(1, 2);
        var widerGame = ButtonBigBlindResearchFixture.create(2, 2);
        var narrow = ConnectedChanceAudit.assess(narrowGame, oracle);
        var wider = ConnectedChanceAudit.assess(widerGame, oracle);

        assertEquals(4, oracle.uniqueMatchupsEnumerated());
        assertEquals(narrowGame.contentHash(), narrow.gameHash());
        assertEquals(widerGame.contentHash(), wider.gameHash());
        assertEquals(4, narrow.matchups().size());
        assertEquals(
                1,
                narrow.matchups().stream()
                        .mapToDouble(ConnectedChanceAudit.Matchup::dealProbability)
                        .sum(),
                1e-12);
        assertEquals(narrow.exactWeightedBb(), wider.exactWeightedBb(), 1e-12);
        assertNotEquals(narrow.abstractWeightedBb(), wider.abstractWeightedBb());
        assertEquals(
                narrow.abstractWeightedBb() - narrow.exactWeightedBb(),
                narrow.signedErrorBb(),
                1e-12);
        assertTrue(narrow.meanAbsoluteErrorBb() >= Math.abs(narrow.signedErrorBb()));
        assertTrue(narrow.maxAbsoluteDealErrorBb() >= narrow.meanAbsoluteErrorBb());
        assertTrue(narrow.signedErrorBb() > 1.0, "This fixture has material chance bias");
        assertEquals(0.196069, narrow.exactWeightedBb(), 1e-6);
        assertEquals(1.251659, narrow.signedErrorBb(), 1e-6);
        assertEquals(2.134002, narrow.maxAbsoluteDealErrorBb(), 1e-6);
    }
}
