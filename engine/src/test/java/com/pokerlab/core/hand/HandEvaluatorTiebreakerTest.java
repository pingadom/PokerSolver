package com.pokerlab.core.hand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandEvaluatorTiebreakerTest {

    @Test
    void higherPairBeatsLowerPair() {
        EvaluatedHand aces = HandEvaluator.evaluateFive(cards("As", "Ah", "Kd", "9c", "2s"));
        EvaluatedHand kings = HandEvaluator.evaluateFive(cards("Ks", "Kh", "Qd", "9h", "2c"));

        assertTrue(aces.compareTo(kings) > 0);
    }

    @Test
    void samePairUsesHighestKickerFirst() {
        EvaluatedHand aceKicker = HandEvaluator.evaluateFive(cards("Qs", "Qh", "Ad", "9c", "2s"));
        EvaluatedHand kingKicker = HandEvaluator.evaluateFive(cards("Qc", "Qd", "Ks", "9h", "2c"));

        assertTrue(aceKicker.compareTo(kingKicker) > 0);
        assertEquals(List.of(12, 14, 9, 2), aceKicker.rank().tiebreakers());
    }

    @Test
    void higherTopTwoPairBeatsLowerTopTwoPair() {
        EvaluatedHand acesAndKings =
                HandEvaluator.evaluateFive(cards("As", "Ah", "Kd", "Kc", "2s"));
        EvaluatedHand kingsAndQueens =
                HandEvaluator.evaluateFive(cards("Ks", "Kh", "Qd", "Qc", "As"));

        assertTrue(acesAndKings.compareTo(kingsAndQueens) > 0);
    }

    @Test
    void sameTwoPairUsesKicker() {
        EvaluatedHand aceKicker = HandEvaluator.evaluateFive(cards("As", "Ah", "Kd", "Kc", "Qs"));
        EvaluatedHand jackKicker = HandEvaluator.evaluateFive(cards("Ac", "Ad", "Kh", "Ks", "Jd"));

        assertTrue(aceKicker.compareTo(jackKicker) > 0);
        assertEquals(List.of(14, 13, 12), aceKicker.rank().tiebreakers());
    }

    @Test
    void flushVersusFlushUsesHighestCardThenNextKickers() {
        EvaluatedHand aceHighFlush =
                HandEvaluator.evaluateFive(cards("As", "Js", "9s", "6s", "2s"));
        EvaluatedHand kingHighFlush =
                HandEvaluator.evaluateFive(cards("Kh", "Jh", "9h", "6h", "2h"));

        assertTrue(aceHighFlush.compareTo(kingHighFlush) > 0);
        assertEquals(List.of(14, 11, 9, 6, 2), aceHighFlush.rank().tiebreakers());
    }

    @Test
    void fullHouseVersusFullHouseComparesTripRankFirst() {
        EvaluatedHand acesFull = HandEvaluator.evaluateFive(cards("As", "Ah", "Ad", "Kc", "Kd"));
        EvaluatedHand kingsFull = HandEvaluator.evaluateFive(cards("Ks", "Kh", "Kd", "Ac", "Ad"));

        assertTrue(acesFull.compareTo(kingsFull) > 0);
        assertEquals(List.of(14, 13), acesFull.rank().tiebreakers());
    }

    @Test
    void fullHouseWithSameTripsComparesPairRankSecond() {
        EvaluatedHand acesFullOfKings =
                HandEvaluator.evaluateFive(cards("As", "Ah", "Ad", "Kc", "Kd"));
        EvaluatedHand acesFullOfQueens =
                HandEvaluator.evaluateFive(cards("Ac", "As", "Ah", "Qc", "Qd"));

        assertTrue(acesFullOfKings.compareTo(acesFullOfQueens) > 0);
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
