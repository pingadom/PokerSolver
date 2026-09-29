package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PhysicalRiverPairCallResponseTest {
    @Test
    void singletonOpponentIncrementMatchesTerminalChipPayoffs() {
        var game = ButtonBigBlindRangeValidationFixture.createBucketed();
        var deals = game.chanceOutcomes(game.initialState());
        var random = new SplittableRandom(43);
        for (int index = 0; index < 100; index++) {
            var state = PhysicalRiverAliasAudit.sampleRiverState(game, random);
            var deal =
                    deals.stream()
                            .filter(
                                    outcome ->
                                            outcome.state().bigBlind().equals(state.bigBlind())
                                                    && outcome.state()
                                                            .button()
                                                            .equals(state.button()))
                            .findFirst()
                            .orElseThrow();
            var check = game.afterAction(game.afterAction(state, "k"), "k");
            String response =
                    PhysicalRiverPairCallResponse.calls(state.button(), board(state)) ? "c" : "f";
            var bet = game.afterAction(game.afterAction(state, "b"), response);
            assertEquals(
                    game.terminalUtility(bet) - game.terminalUtility(check),
                    PhysicalRiverPairCallResponse.betIncrement(
                            state, List.of(deal), game.potBb() / 2, 8),
                    1e-12);
        }
    }

    @Test
    void responseUsesHiddenButtonHandStrength() {
        var board =
                List.of(
                        Card.parse("2c"),
                        Card.parse("4d"),
                        Card.parse("6h"),
                        Card.parse("8s"),
                        Card.parse("Ts"));
        assertFalse(
                PhysicalRiverPairCallResponse.calls(
                        new WeightedCombo(Card.parse("Qh"), Card.parse("Jh"), 1), board));
        assertTrue(
                PhysicalRiverPairCallResponse.calls(
                        new WeightedCombo(Card.parse("Tc"), Card.parse("Td"), 1), board));
    }

    private static List<Card> board(ButtonBigBlindPhysicalDeckGame.State state) {
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        return board;
    }
}
