package com.pokerlab.core.hand;

import com.pokerlab.core.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandEvaluatorCategoryTest {

    @Test
    void recognisesHighCard() {
        assertCategory(HandCategory.HIGH_CARD, "As", "Kd", "9c", "6h", "2s");
    }

    @Test
    void recognisesOnePair() {
        assertCategory(HandCategory.ONE_PAIR, "As", "Ah", "Kd", "9c", "2s");
    }

    @Test
    void recognisesTwoPair() {
        assertCategory(HandCategory.TWO_PAIR, "As", "Ah", "Kd", "Kc", "2s");
    }

    @Test
    void recognisesThreeOfAKind() {
        assertCategory(HandCategory.THREE_OF_A_KIND, "As", "Ah", "Ad", "Kc", "2s");
    }

    @Test
    void recognisesStraight() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("9s", "8h", "7d", "6c", "5s"));

        assertEquals(HandCategory.STRAIGHT, hand.category());
        assertEquals(List.of(9), hand.rank().tiebreakers());
    }

    @Test
    void recognisesWheelStraightAsFiveHighStraight() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "2h", "3d", "4c", "5s"));

        assertEquals(HandCategory.STRAIGHT, hand.category());
        assertEquals(List.of(5), hand.rank().tiebreakers());
    }

    @Test
    void recognisesFlush() {
        assertCategory(HandCategory.FLUSH, "As", "Js", "9s", "6s", "2s");
    }

    @Test
    void recognisesFullHouse() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "Ah", "Ad", "Kc", "Kd"));

        assertEquals(HandCategory.FULL_HOUSE, hand.category());
        assertEquals(List.of(14, 13), hand.rank().tiebreakers());
    }

    @Test
    void recognisesFourOfAKind() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "Ah", "Ad", "Ac", "Kd"));

        assertEquals(HandCategory.FOUR_OF_A_KIND, hand.category());
        assertEquals(List.of(14, 13), hand.rank().tiebreakers());
    }

    @Test
    void recognisesStraightFlush() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("9s", "8s", "7s", "6s", "5s"));

        assertEquals(HandCategory.STRAIGHT_FLUSH, hand.category());
        assertEquals(List.of(9), hand.rank().tiebreakers());
    }

    @Test
    void recognisesRoyalFlush() {
        EvaluatedHand hand = HandEvaluator.evaluateFive(cards("As", "Ks", "Qs", "Js", "Ts"));

        assertEquals(HandCategory.ROYAL_FLUSH, hand.category());
        assertEquals(List.of(14), hand.rank().tiebreakers());
    }

    private static void assertCategory(HandCategory expected, String... values) {
        assertEquals(expected, HandEvaluator.evaluateFive(cards(values)).category());
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
