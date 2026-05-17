package com.pokerlab.core.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckTest {

    @Test
    void createsStandardFiftyTwoCardDeck() {
        Deck deck = new Deck();

        assertEquals(52, deck.size());
        assertEquals(52, new HashSet<>(deck.cards()).size());
    }

    @Test
    void dealingCardsReducesDeckSize() {
        Deck deck = new Deck();

        List<Card> dealt = deck.deal(5);

        assertEquals(5, dealt.size());
        assertEquals(47, deck.size());
    }
}
