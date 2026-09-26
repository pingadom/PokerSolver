package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TurnRiverHandSessionTest {
    private static TurnRiverHandSession session;

    @BeforeAll
    static void load() throws Exception {
        try (var resource =
                TurnRiverHandSessionTest.class.getResourceAsStream(
                        "/turn-river-validation-pack.json")) {
            assertNotNull(resource);
            TurnRiverSolutionPack pack =
                    TurnRiverPackJson.read(
                            new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            session = new TurnRiverHandSession(pack);
        }
    }

    @Test
    void completesConnectedHandsForBothSeatsWithoutLeakingCardsMidHand() {
        for (int heroPlayer = 0; heroPlayer <= 1; heroPlayer++) {
            for (long seed = 0; seed < 12; seed++) {
                List<String> actions = new ArrayList<>();
                TurnRiverHandSession.Snapshot state =
                        session.replay(seed, session.packHash(), heroPlayer, actions);
                String heroCombo = state.heroCombo();
                assertEquals(state, session.replay(seed, session.packHash(), heroPlayer, actions));
                for (int decision = 0; !state.complete(); decision++) {
                    assertTrue(decision < 4, "A two-street hand needs at most four hero decisions");
                    assertNull(state.opponentCombo());
                    assertNull(state.heroCenteredResultBb());
                    assertEquals(heroCombo, state.heroCombo());
                    String action = state.legalActions().contains("k") ? "k" : "c";
                    actions.add(action);
                    state = session.replay(seed, session.packHash(), heroPlayer, actions);
                    assertEquals(actions.size(), state.feedback().size());
                    assertEquals(action, state.feedback().getLast().selectedAction());
                    assertEquals(
                            state.feedback().getLast().bestEvBb()
                                    - state.feedback().getLast().selectedEvBb(),
                            state.feedback().getLast().evLossBb(),
                            1e-9);
                }
                assertTrue(state.showdown());
                assertNotNull(state.river());
                assertNotNull(state.opponentCombo());
                assertNotNull(state.heroCenteredResultBb());
                assertTrue(Double.isFinite(state.heroCenteredResultBb()));
                assertTrue(state.legalActions().isEmpty());
                assertEquals(state, session.replay(seed, session.packHash(), heroPlayer, actions));
                assertEquals(
                        showdownResult(state) * state.potBb() / 2,
                        state.heroCenteredResultBb(),
                        1e-9);
            }
        }
    }

    private static int showdownResult(TurnRiverHandSession.Snapshot snapshot) {
        String[] hero = snapshot.heroCombo().split(" ");
        String[] opponent = snapshot.opponentCombo().split(" ");
        List<Card> board = snapshot.turnBoard();
        int heroScore =
                HandEvaluator.evaluateBestScore(
                        Card.parse(hero[0]),
                        Card.parse(hero[1]),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        snapshot.river());
        int opponentScore =
                HandEvaluator.evaluateBestScore(
                        Card.parse(opponent[0]),
                        Card.parse(opponent[1]),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        snapshot.river());
        return Integer.compare(heroScore, opponentScore);
    }

    @Test
    void rejectsStalePackIllegalAndExcessHeroActions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, "0".repeat(64), 0, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 2, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 0, List.of("c")));
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 0, List.of("k", "k", "k", "k", "k")));
        List<String> actions = new ArrayList<>();
        TurnRiverHandSession.Snapshot state = session.replay(42, session.packHash(), 0, actions);
        while (!state.complete()) {
            actions.add(state.legalActions().contains("k") ? "k" : "c");
            state = session.replay(42, session.packHash(), 0, actions);
        }
        actions.add("k");
        List<String> excess = List.copyOf(actions);
        assertThrows(
                IllegalArgumentException.class,
                () -> session.replay(42, session.packHash(), 0, excess));
    }

    @Test
    void heroFoldEndsHandWithoutRevealingOpponent() {
        boolean found = false;
        for (long seed = 0; seed < 500 && !found; seed++) {
            TurnRiverHandSession.Snapshot state =
                    session.replay(seed, session.packHash(), 1, List.of());
            if (!state.legalActions().contains("f")) continue;
            TurnRiverHandSession.Snapshot folded =
                    session.replay(seed, session.packHash(), 1, List.of("f"));
            assertTrue(folded.complete());
            assertFalse(folded.showdown());
            assertNull(folded.opponentCombo());
            assertEquals(-10, folded.heroCenteredResultBb());
            assertNull(folded.river());
            found = true;
        }
        assertTrue(found, "The seeded fixture should include a turn bet to fold to");
    }

    @Test
    void oppositeHeroSeatsSeeTheSameSeededJointDeal() {
        long seed = 42;
        String firstHero = session.replay(seed, session.packHash(), 0, List.of()).heroCombo();
        String secondHero = session.replay(seed, session.packHash(), 1, List.of()).heroCombo();
        List<String> actions = new ArrayList<>();
        TurnRiverHandSession.Snapshot state = session.replay(seed, session.packHash(), 0, actions);
        while (!state.complete()) {
            actions.add(state.legalActions().contains("k") ? "k" : "c");
            state = session.replay(seed, session.packHash(), 0, actions);
        }
        assertEquals(firstHero, state.heroCombo());
        assertEquals(secondHero, state.opponentCombo());
    }
}
