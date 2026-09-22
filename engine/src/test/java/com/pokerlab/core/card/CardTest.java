package com.pokerlab.core.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CardTest {

    @Test
    void parsesCompactNotation() {
        Card card = Card.parse("As");

        assertEquals(Rank.ACE, card.rank());
        assertEquals(Suit.SPADES, card.suit());
        assertEquals("As", card.compact());
    }

    @Test
    void rejectsInvalidNotation() {
        assertThrows(IllegalArgumentException.class, () -> Card.parse("10s"));
        assertThrows(IllegalArgumentException.class, () -> Card.parse("Ax"));
    }
}
