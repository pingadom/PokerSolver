package com.pokerlab.core.hand;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandEvaluatorValidationTest {

    @Test
    void evaluateFiveRejectsDuplicateCards() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HandEvaluator.evaluateFive(cards("As", "As", "Kd", "9c", "2s")));
    }

    @Test
    void evaluateBestRejectsDuplicateCards() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HandEvaluator.evaluateBest(cards("As", "Ks", "Qs", "Js", "Ts", "As", "2d")));
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
