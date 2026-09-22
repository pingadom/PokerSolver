package com.pokerlab.core.card;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {

    public Card {
        Objects.requireNonNull(rank, "rank must not be null");
        Objects.requireNonNull(suit, "suit must not be null");
    }

    public static Card of(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }

    /** Parses compact poker notation such as As, Qd, Tc. Ten must be written as T, not 10. */
    public static Card parse(String text) {
        if (text == null || text.length() != 2) {
            throw new IllegalArgumentException(
                    "Card must use two-character notation, e.g. As, Qd, Tc");
        }

        Rank rank = Rank.fromChar(text.charAt(0));
        Suit suit = Suit.fromChar(text.charAt(1));
        return new Card(rank, suit);
    }

    public String compact() {
        return "" + rank.symbol() + suit.symbol();
    }

    @Override
    public String toString() {
        return "" + rank.symbol() + suit.displaySymbol();
    }
}
