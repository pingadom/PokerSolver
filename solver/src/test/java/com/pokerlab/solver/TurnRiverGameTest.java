package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnRiverGameTest {
    private static TurnRiverSpot spot() {
        return TurnRiverValidationSpot.create();
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }

    @Test
    void turnActionsDealExactlyOneVisibleUnblockedRiver() {
        TurnRiverGame game = spot().game();
        assertEquals(4, game.chanceOutcomes(game.initialState()).size());
        TurnRiverGame.State deal = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertEquals(List.of("k", "b"), game.legalActions(deal));
        assertFalse(game.informationSet(deal).contains(deal.second().key()));
        TurnRiverGame.State alternateOpponent =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.first().equals(deal.first()))
                        .filter(state -> !state.second().equals(deal.second()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(game.informationSet(deal), game.informationSet(alternateOpponent));
        TurnRiverGame.State turnCall = game.afterAction(game.afterAction(deal, "b"), "c");
        assertEquals(-1, game.currentPlayer(turnCall));
        List<ChanceOutcome<TurnRiverGame.State>> rivers = game.chanceOutcomes(turnCall);
        assertEquals(44, rivers.size());
        assertEquals(1, rivers.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        for (ChanceOutcome<TurnRiverGame.State> outcome : rivers) {
            Card river = outcome.state().river();
            assertFalse(spot().turnBoard().contains(river));
            assertNotEquals(deal.first().first(), river);
            assertNotEquals(deal.first().second(), river);
            assertNotEquals(deal.second().first(), river);
            assertNotEquals(deal.second().second(), river);
            assertTrue(game.informationSet(outcome.state()).contains(river.compact()));
        }
        TurnRiverGame.State alternateRiver =
                game
                        .chanceOutcomes(
                                game.afterAction(game.afterAction(alternateOpponent, "b"), "c"))
                        .stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.river().equals(card("9c")))
                        .findFirst()
                        .orElseThrow();
        TurnRiverGame.State firstRiver =
                rivers.stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.river().equals(card("9c")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(game.informationSet(firstRiver), game.informationSet(alternateRiver));
    }

    @Test
    void turnAndRiverPayoffsAccountForBothCalledBets() {
        TurnRiverGame game = spot().game();
        TurnRiverGame.State deal =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.first().key().equals("Ah As"))
                        .filter(state -> state.second().key().equals("Kc Kd"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(10, game.terminalUtility(game.afterAction(game.afterAction(deal, "b"), "f")));
        TurnRiverGame.State called = game.afterAction(game.afterAction(deal, "b"), "c");
        TurnRiverGame.State river =
                game.chanceOutcomes(called).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.river().equals(card("9c")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(20, game.terminalUtility(game.afterAction(game.afterAction(river, "k"), "k")));
        assertEquals(20, game.terminalUtility(game.afterAction(game.afterAction(river, "b"), "f")));
        assertEquals(30, game.terminalUtility(game.afterAction(game.afterAction(river, "b"), "c")));
        TurnRiverGame.State noTurnBet = game.afterAction(game.afterAction(deal, "k"), "k");
        TurnRiverGame.State plainRiver =
                game.chanceOutcomes(noTurnBet).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.river().equals(card("9c")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                10, game.terminalUtility(game.afterAction(game.afterAction(plainRiver, "k"), "k")));
    }

    @Test
    void invalidSpotRejectsImpossibleCardsOrCommitment() {
        TurnRiverSpot valid = spot();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TurnRiverSpot(
                                valid.turnBoard(),
                                20,
                                15,
                                10,
                                10,
                                valid.firstRange(),
                                valid.secondRange()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TurnRiverSpot(
                                valid.turnBoard(),
                                20,
                                80,
                                10,
                                10,
                                List.of(combo("2c", "Ah")),
                                valid.secondRange()));
    }

    @Test
    void exactBestResponseBoundsTurnRiverProfile() {
        TurnRiverGame game = spot().game();
        CfrSolver<TurnRiverGame.State> solver = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        HeadsUpBestResponse.Report early = HeadsUpBestResponse.assess(game, solver.solve(100));
        CfrSolution solution = solver.solve(1_000);
        HeadsUpBestResponse.Report report = HeadsUpBestResponse.assess(game, solution);
        assertTrue(Double.isFinite(report.gap()));
        assertEquals(1_112, solution.strategy().size());
        assertTrue(report.gap() < early.gap());
        assertTrue(report.gap() < 0.01);
        assertTrue(report.secondBestResponse() <= report.profileValue());
        assertTrue(report.profileValue() <= report.firstBestResponse());
    }
}
