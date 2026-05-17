package com.pokerlab.core.hand;

public enum HandCategory {
    HIGH_CARD(0, "High card"),
    ONE_PAIR(1, "One pair"),
    TWO_PAIR(2, "Two pair"),
    THREE_OF_A_KIND(3, "Three of a kind"),
    STRAIGHT(4, "Straight"),
    FLUSH(5, "Flush"),
    FULL_HOUSE(6, "Full house"),
    FOUR_OF_A_KIND(7, "Four of a kind"),
    STRAIGHT_FLUSH(8, "Straight flush"),
    ROYAL_FLUSH(9, "Royal flush");

    private final int strength;
    private final String displayName;

    HandCategory(int strength, String displayName) {
        this.strength = strength;
        this.displayName = displayName;
    }

    public int strength() {
        return strength;
    }

    public String displayName() {
        return displayName;
    }
}
