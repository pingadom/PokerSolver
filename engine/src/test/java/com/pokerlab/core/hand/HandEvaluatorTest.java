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
