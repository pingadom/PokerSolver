package com.pokerlab.core.showdown;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShowdownValidationTest {

    @Test
    void compareRejectsDuplicateCardAcrossHeroAndBoard() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Showdown.compare(
                                cards("As", "Ks"),
                                cards("Qd", "Qc"),
                                cards("As", "7c", "2s", "9d", "Jc")));
    }

    @Test
    void compareRejectsDuplicateCardAcrossHeroAndVillain() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Showdown.compare(
                                cards("As", "Ks"),
                                cards("As", "Qc"),
                                cards("Ah", "7c", "2s", "9d", "Jc")));
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
