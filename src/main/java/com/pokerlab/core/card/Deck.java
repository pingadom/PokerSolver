package com.pokerlab.core.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class Deck {
    private final List<Card> cards;

    public Deck() {
        this.cards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(Card.of(rank, suit));
            }
        }
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
    }

    public Card deal() {
        if (cards.isEmpty()) {
            throw new NoSuchElementException("Cannot deal from an empty deck");
        }
        return cards.remove(cards.size() - 1);
    }

    public List<Card> deal(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count > cards.size()) {
            throw new IllegalArgumentException("Cannot deal " + count + " cards from a deck with " + cards.size() + " cards");
        }

        List<Card> dealt = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dealt.add(deal());
        }
        return dealt;
    }

    public boolean remove(Card card) {
        return cards.remove(card);
    }

    public List<Card> cards() {
        return List.copyOf(cards);
    }
}
