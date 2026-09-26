package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlopTurnRiverGameTest {
    private static FlopTurnRiverSpot spot() {
        return FlopTurnRiverValidationSpot.create();
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static FlopTurnRiverGame.State act(
            FlopTurnRiverGame game, FlopTurnRiverGame.State state, String... actions) {
        for (String action : actions) state = game.afterAction(state, action);
        return state;
    }

    private static FlopTurnRiverGame.State deal(
            FlopTurnRiverGame game, String first, String second) {
        return game.chanceOutcomes(game.initialState()).stream()
                .map(ChanceOutcome::state)
                .filter(state -> state.first().key().equals(first))
                .filter(state -> state.second().key().equals(second))
                .findFirst()
                .orElseThrow();
    }

    private static FlopTurnRiverGame.State cardOutcome(
            FlopTurnRiverGame game, FlopTurnRiverGame.State state, String card) {
        return game.chanceOutcomes(state).stream()
                .map(ChanceOutcome::state)
                .filter(
                        next ->
                                Card.parse(card)
                                        .equals(state.turn() == null ? next.turn() : next.river()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void publicChanceRespectsBothPrivateHandsAndOnlyRevealsDealtCards() {
        FlopTurnRiverGame game = spot().game();
        FlopTurnRiverGame.State start = deal(game, "Ah As", "Kc Kd");
        FlopTurnRiverGame.State otherOpponent = deal(game, "Ah As", "Qc Qd");
        assertEquals(game.informationSet(start), game.informationSet(otherOpponent));
        assertFalse(game.informationSet(start).contains("Kc"));
        FlopTurnRiverGame.State flopComplete = act(game, start, "b", "c");
        assertEquals(-1, game.currentPlayer(flopComplete));
        List<ChanceOutcome<FlopTurnRiverGame.State>> turns = game.chanceOutcomes(flopComplete);
        assertEquals(5, turns.size());
        assertEquals(1, turns.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        FlopTurnRiverGame.State turn =
                turns.stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.turn().equals(card("4h")))
                        .findFirst()
                        .orElseThrow();
        assertTrue(game.informationSet(turn).contains("4h"));
        assertFalse(game.informationSet(turn).contains("Kc"));
        FlopTurnRiverGame.State turnComplete = act(game, turn, "k", "k");
        List<ChanceOutcome<FlopTurnRiverGame.State>> rivers = game.chanceOutcomes(turnComplete);
        assertEquals(44, rivers.size());
        assertEquals(1, rivers.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        for (ChanceOutcome<FlopTurnRiverGame.State> outcome : rivers) {
            Card river = outcome.state().river();
            assertFalse(spot().flop().contains(river));
            assertNotEquals(turn.turn(), river);
            assertNotEquals(start.first().first(), river);
            assertNotEquals(start.first().second(), river);
            assertNotEquals(start.second().first(), river);
            assertNotEquals(start.second().second(), river);
        }
        FlopTurnRiverGame.State sameTurn =
                cardOutcome(game, act(game, otherOpponent, "b", "c"), "4h");
        assertEquals(game.informationSet(turn), game.informationSet(sameTurn));
    }

    @Test
    void eachStreetAccumulatesOnlyCalledBetsAndFoldsKeepCommittedChips() {
        FlopTurnRiverGame game = spot().game();
        FlopTurnRiverGame.State start = deal(game, "Ah As", "Kc Kd");
        assertEquals(10, game.terminalUtility(act(game, start, "b", "f")));
        assertEquals(-10, game.terminalUtility(act(game, start, "k", "b", "f")));
        FlopTurnRiverGame.State turn = cardOutcome(game, act(game, start, "b", "c"), "4h");
        assertEquals(20, game.terminalUtility(act(game, turn, "b", "f")));
        assertEquals(-20, game.terminalUtility(act(game, turn, "k", "b", "f")));
        FlopTurnRiverGame.State river = cardOutcome(game, act(game, turn, "b", "c"), "9c");
        assertEquals(30, game.terminalUtility(act(game, river, "b", "f")));
        assertEquals(-30, game.terminalUtility(act(game, river, "k", "b", "f")));
        assertEquals(40, game.terminalUtility(act(game, river, "b", "c")));
        FlopTurnRiverGame.State uncheckedTurn =
                cardOutcome(
                        game,
                        act(game, cardOutcome(game, act(game, start, "k", "k"), "4h"), "k", "k"),
                        "9c");
        assertEquals(10, game.terminalUtility(act(game, uncheckedTurn, "k", "k")));
    }

    @Test
    void fullTurnDeckHasExactChanceAndDifferentIdentity() {
        FlopTurnRiverSpot full = spot().withFullTurnDeck();
        assertNotEquals(spot().contentHash(), full.contentHash());
        assertEquals(49, full.turnCandidates().size());
        FlopTurnRiverGame game = full.game();
        assertEquals(
                45, game.chanceOutcomes(act(game, deal(game, "Ah As", "Kc Kd"), "k", "k")).size());
        FlopTurnChanceAudit.Report audit = FlopTurnChanceAudit.assess(full);
        assertEquals(audit.exactCheckdownBb(), audit.restrictedCheckdownBb(), 1e-12);
        assertEquals(0, audit.maxDealErrorBb(), 1e-12);
        assertTrue(FlopTurnChanceAudit.assess(spot()).maxDealErrorBb() > 0);
    }

    @Test
    void restrictedTurnChanceRenormalizesAfterPrivateCardBlockers() {
        FlopTurnRiverSpot fixture = spot();
        List<Card> candidates = new java.util.ArrayList<>(fixture.turnCandidates());
        candidates.add(card("Ah"));
        FlopTurnRiverSpot withBlocker =
                new FlopTurnRiverSpot(
                        fixture.flop(),
                        fixture.potBb(),
                        fixture.remainingStackBb(),
                        fixture.flopBetBb(),
                        fixture.turnBetBb(),
                        fixture.riverBetBb(),
                        fixture.firstRange(),
                        fixture.secondRange(),
                        candidates);
        FlopTurnRiverGame game = withBlocker.game();
        List<ChanceOutcome<FlopTurnRiverGame.State>> blocked =
                game.chanceOutcomes(act(game, deal(game, "Ah As", "Kc Kd"), "k", "k"));
        List<ChanceOutcome<FlopTurnRiverGame.State>> unblocked =
                game.chanceOutcomes(act(game, deal(game, "6s 7s", "Kc Kd"), "k", "k"));
        assertEquals(5, blocked.size());
        assertEquals(6, unblocked.size());
        assertEquals(1, blocked.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(1, unblocked.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
    }

    @Test
    void rejectsImpossibleOrInconsistentModel() {
        FlopTurnRiverSpot valid = spot();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FlopTurnRiverSpot(
                                valid.flop(),
                                20,
                                20,
                                10,
                                10,
                                10,
                                valid.firstRange(),
                                valid.secondRange(),
                                valid.turnCandidates()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FlopTurnRiverSpot(
                                valid.flop(),
                                20,
                                80,
                                10,
                                10,
                                10,
                                valid.firstRange(),
                                valid.secondRange(),
                                List.of(card("2c"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FlopTurnRiverSpot(
                                valid.flop(),
                                20,
                                80,
                                10,
                                10,
                                10,
                                valid.firstRange(),
                                valid.secondRange(),
                                List.of(card("Ah"), card("Kc"))));
        FlopTurnRiverSpot reordered =
                new FlopTurnRiverSpot(
                        valid.flop().reversed(),
                        valid.potBb(),
                        valid.remainingStackBb(),
                        valid.flopBetBb(),
                        valid.turnBetBb(),
                        valid.riverBetBb(),
                        valid.firstRange().reversed(),
                        valid.secondRange().reversed(),
                        valid.turnCandidates().reversed());
        assertEquals(valid.contentHash(), reordered.contentHash());
    }

    @Test
    void bestResponseBoundsThreeStreetProfileAndImprovesWithIterations() {
        FlopTurnRiverGame game = spot().game();
        CfrSolver<FlopTurnRiverGame.State> solver =
                new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        HeadsUpBestResponse.Report early = HeadsUpBestResponse.assess(game, solver.solve(20));
        CfrSolution solution = solver.solve(100);
        HeadsUpBestResponse.Report later = HeadsUpBestResponse.assess(game, solution);
        assertEquals(16_688, solution.strategy().size());
        assertTrue(later.gap() < early.gap());
        assertTrue(later.gap() < 0.25);
        assertTrue(later.secondBestResponse() <= later.profileValue());
        assertTrue(later.profileValue() <= later.firstBestResponse());
    }
}
