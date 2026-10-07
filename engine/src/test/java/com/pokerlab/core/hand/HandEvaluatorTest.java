package com.pokerlab.core.hand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandEvaluatorTest {

    @Test
    void recognisesRoyalFlush() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "Ks", "Qs", "Js", "Ts"));

        assertEquals(HandCategory.ROYAL_FLUSH, hand.category());
    }

    @Test
    void recognisesWheelStraight() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "2d", "3c", "4h", "5s"));

        assertEquals(HandCategory.STRAIGHT, hand.category());
        assertEquals(List.of(5), hand.rank().tiebreakers());
    }

    @Test
    void fourOfAKindBeatsFullHouse() {
        EvaluatedHand fourOfAKind = HandEvaluator.evaluateFive(cards("As", "Ah", "Ad", "Ac", "2s"));
        EvaluatedHand fullHouse = HandEvaluator.evaluateFive(cards("Ks", "Kh", "Kd", "2c", "2d"));

        assertTrue(fourOfAKind.compareTo(fullHouse) > 0);
    }

    @Test
    void threeOfAKindPreservesBothKickersInFiveAndSevenCardResults() {
        var strong = cards("2c", "2d", "2h", "Ac", "Kd");
        var weak = cards("2c", "2d", "2h", "Ac", "6c");
        var first = HandEvaluator.evaluateFive(strong);
        var second = HandEvaluator.evaluateFive(weak);
        assertEquals(List.of(2, 14, 13), first.rank().tiebreakers());
        assertEquals(List.of(2, 14, 6), second.rank().tiebreakers());
        assertTrue(
                first.compareTo(second) > 0, "Second kicker must break equal trips/first kicker");
        var sevenStrong = cards("2c", "2d", "3h", "Ac", "2h", "Kd", "9d");
        var sevenWeak = cards("2c", "2d", "3h", "Ac", "2h", "6c", "5c");
        assertEquals(
                List.of(2, 14, 13), HandEvaluator.evaluateBest(sevenStrong).rank().tiebreakers());
        assertEquals(List.of(2, 14, 6), HandEvaluator.evaluateBest(sevenWeak).rank().tiebreakers());
        assertTrue(
                HandEvaluator.evaluateBest(sevenStrong)
                                .compareTo(HandEvaluator.evaluateBest(sevenWeak))
                        > 0);
        assertEquals(
                Integer.signum(
                        Integer.compare(
                                HandEvaluator.evaluateBestScore(sevenStrong.toArray(Card[]::new)),
                                HandEvaluator.evaluateBestScore(sevenWeak.toArray(Card[]::new)))),
                Integer.signum(
                        HandEvaluator.evaluateBest(sevenStrong)
                                .compareTo(HandEvaluator.evaluateBest(sevenWeak))));
        var reordered = new java.util.ArrayList<>(sevenStrong);
        java.util.Collections.reverse(reordered);
        assertEquals(
                HandEvaluator.evaluateBest(sevenStrong).rank(),
                HandEvaluator.evaluateBest(reordered).rank());
    }

    @Test
    void evaluatesBestFiveFromSevenCards() {
        EvaluatedHand best =
                HandEvaluator.evaluateBest(cards("As", "Ks", "Qs", "Js", "Ts", "2d", "3c"));

        assertEquals(HandCategory.ROYAL_FLUSH, best.category());
        assertEquals(5, best.cards().size());
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
