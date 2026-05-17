package com.pokerlab.core.card;

import java.util.Arrays;

public enum Suit {
    CLUBS('c', '♣'),
    DIAMONDS('d', '♦'),
    HEARTS('h', '♥'),
    SPADES('s', '♠');

    private final char symbol;
    private final char displaySymbol;

    Suit(char symbol, char displaySymbol) {
        this.symbol = symbol;
        this.displaySymbol = displaySymbol;
    }

    public char symbol() {
        return symbol;
    }

    public char displaySymbol() {
        return displaySymbol;
    }

    public static Suit fromChar(char value) {
        char lower = Character.toLowerCase(value);
        return Arrays.stream(values())
                .filter(suit -> suit.symbol == lower)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown suit: " + value));
    }
}
