package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategicRiverCallPolicyTest {
    @Test
    void choosesCallByConditionalValueAndDoesNotReadDealtBigBlindCards() {
        var high = combo("Ac", "Kc");
        var pair = combo("Ah", "Ad");
        var button = combo("Qc", "Qd");
        var preflop =
                new PreflopActionBelief(
                        Map.of(button.key(), 1.0), Map.of(high.key(), 1.0, pair.key(), 1.0));
        var postflop = new PostflopActionBelief(0.7, 0.7, 0.5, 0.5);
        var board = cards("2c", "7s", "Th", "3d", "9c");
        var versusValue =
                new StrategicRiverCallPolicy(
                        List.of(high, pair), preflop, postflop, 0.9, 0.1, 3.25, 8);
        var versusStrength =
                new StrategicRiverCallPolicy(
                        List.of(high, pair), preflop, postflop, 0.1, 0.9, 3.25, 8);
        var highDealt = PublicRiverHistory.from(state(high, button, board));
        var pairDealt = PublicRiverHistory.from(state(pair, button, board));
        assertEquals(highDealt, pairDealt);
        assertEquals(12.25, versusValue.callMinusFoldBb(button, board, highDealt), 1e-12);
        assertEquals(-5.75, versusStrength.callMinusFoldBb(button, board, highDealt), 1e-12);
        assertTrue(versusValue.calls(button, board, highDealt));
        assertFalse(versusStrength.calls(button, board, highDealt));
        assertEquals(
                versusValue.callMinusFoldBb(button, board, highDealt),
                versusValue.callMinusFoldBb(button, board, pairDealt),
                1e-12);
        assertNotEquals(versusValue.definition(), versusStrength.definition());
    }

    @Test
    void blockerRemovalAndInvalidRangeAreChecked() {
        var high = combo("Ac", "Kc");
        var pair = combo("Ah", "Ad");
        var button = combo("Ac", "Qd");
        var preflop =
                new PreflopActionBelief(
                        Map.of(button.key(), 1.0), Map.of(high.key(), 1.0, pair.key(), 1.0));
        var policy =
                new StrategicRiverCallPolicy(
                        List.of(high, pair),
                        preflop,
                        new PostflopActionBelief(0.7, 0.7, 0.5, 0.5),
                        0.9,
                        0.1,
                        3.25,
                        8);
        var board = cards("2c", "7s", "Th", "3d", "9c");
        assertEquals(
                -8,
                policy.callMinusFoldBb(
                        button, board, PublicRiverHistory.from(state(pair, button, board))),
                1e-12);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        policy.callMinusFoldBb(
                                combo("2c", "Qd"),
                                board,
                                PublicRiverHistory.from(state(pair, button, board))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StrategicRiverCallPolicy(
                                List.of(high),
                                preflop,
                                new PostflopActionBelief(0.7, 0.7, 0.5, 0.5),
                                0,
                                0.1,
                                3.25,
                                8));
    }

    @Test
    void observedChecksChangeTheInferredBettingRangeAndCallDecision() {
        var high = combo("Ac", "Kc");
        var pair = combo("Ah", "Ad");
        var button = combo("Qc", "Qd");
        var preflop =
                new PreflopActionBelief(
                        Map.of(button.key(), 1.0), Map.of(high.key(), 1.0, pair.key(), 1.0));
        var board = cards("2c", "7s", "Th", "3d", "9c");
        var publicHistory = PublicRiverHistory.from(state(high, button, board));
        var highChecks =
                new StrategicRiverCallPolicy(
                        List.of(high, pair),
                        preflop,
                        new PostflopActionBelief(0.9, 0.2, 0.5, 0.5),
                        0.5,
                        0.5,
                        3.25,
                        8);
        var pairChecks =
                new StrategicRiverCallPolicy(
                        List.of(high, pair),
                        preflop,
                        new PostflopActionBelief(0.2, 0.9, 0.5, 0.5),
                        0.5,
                        0.5,
                        3.25,
                        8);
        assertTrue(highChecks.calls(button, board, publicHistory));
        assertFalse(pairChecks.calls(button, board, publicHistory));
        assertThrows(
                IllegalArgumentException.class,
                () -> highChecks.calls(button, cards("2c", "7s", "Th", "3d", "8c"), publicHistory));
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static List<Card> cards(String... labels) {
        return java.util.Arrays.stream(labels).map(Card::parse).toList();
    }

    private static ButtonBigBlindPhysicalDeckGame.State state(
            WeightedCombo bigBlind, WeightedCombo button, List<Card> board) {
        return new ButtonBigBlindPhysicalDeckGame.State(
                bigBlind,
                button,
                "oc",
                board.subList(0, 3),
                "kk",
                board.get(3),
                "kk",
                board.get(4),
                "");
    }
}
