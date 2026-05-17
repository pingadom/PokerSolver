package com.pokerlab.core.showdown;

import com.pokerlab.core.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ShowdownValidationTest {

    @Test
    void compareRejectsDuplicateCardAcrossHeroAndBoard() {
        assertThrows(IllegalArgumentException.class,
                () -> Showdown.compare(
                        cards("As", "Ks"),
                        cards("Qd", "Qc"),
                        cards("As", "7c", "2s", "9d", "Jc")
                ));
    }

    @Test
    void compareRejectsDuplicateCardAcrossHeroAndVillain() {
        assertThrows(IllegalArgumentException.class,
                () -> Showdown.compare(
                        cards("As", "Ks"),
                        cards("As", "Qc"),
                        cards("Ah", "7c", "2s", "9d", "Jc")
                ));
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
