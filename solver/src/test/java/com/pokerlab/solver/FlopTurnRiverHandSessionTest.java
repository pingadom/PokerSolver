package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FlopTurnRiverHandSessionTest {
    private static FlopTurnRiverHandSession session;

    @BeforeAll
    static void prepare() {
        FlopTurnRiverSpot fixture = FlopTurnRiverValidationSpot.create().withFullTurnDeck();
        FlopTurnRiverSpot small =
                new FlopTurnRiverSpot(
                        fixture.flop(),
                        fixture.potBb(),
                        fixture.remainingStackBb(),
                        fixture.flopBetBb(),
                        fixture.turnBetBb(),
                        fixture.riverBetBb(),
                        List.of(
                                fixture.firstRange().stream()
                                        .filter(combo -> combo.key().equals("Ah As"))
                                        .findFirst()
                                        .orElseThrow()),
                        List.of(fixture.secondRange().getFirst()),
                        fixture.turnCandidates());
        session =
                new FlopTurnRiverHandSession(
                        FlopTurnRiverPackBuilder.generate(small, 3, "2026-09-26T12:00:00Z", 100));
    }

    @Test
    void replaysAConnectedThreeStreetHandAndKeepsOpponentPrivateUntilShowdown() {
        List<String> actions = new ArrayList<>();
        FlopTurnRiverHandSession.Snapshot snapshot =
                session.replay(42, session.packHash(), 0, actions);
        assertEquals("FLOP", snapshot.street());
        assertNull(snapshot.turn());
        assertNull(snapshot.river());
        assertNull(snapshot.opponentCombo());
        assertEquals(20, snapshot.potBb());
        while (!snapshot.complete()) {
            assertNull(snapshot.opponentCombo());
            assertFalse(snapshot.legalActions().isEmpty());
            actions.add(snapshot.legalActions().contains("k") ? "k" : "c");
            snapshot = session.replay(42, session.packHash(), 0, actions);
            assertEquals(actions.size(), snapshot.feedback().size());
            assertTrue(actions.size() <= 6);
        }
        assertTrue(snapshot.showdown());
        assertEquals("RIVER", snapshot.street());
        assertNotNull(snapshot.turn());
        assertNotNull(snapshot.river());
        assertEquals("Kc Kd", snapshot.opponentCombo());
        assertNotNull(snapshot.heroCenteredResultBb());
        assertEquals(snapshot, session.replay(42, session.packHash(), 0, actions));
    }

    @Test
    void rejectsChangedPackIllegalActionsAndExcessTranscript() {
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, "0".repeat(64), 0, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 0, List.of("c")));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 2, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        session.replay(
                                42,
                                session.packHash(),
                                0,
                                List.of("k", "k", "k", "k", "k", "k", "k")));
    }
}
