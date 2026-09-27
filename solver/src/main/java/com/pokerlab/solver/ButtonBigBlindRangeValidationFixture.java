package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** A disjoint synthetic 3-by-3 prior for testing connected-game range sensitivity. */
public final class ButtonBigBlindRangeValidationFixture {
    private ButtonBigBlindRangeValidationFixture() {}

    public static ButtonBigBlindPhysicalDeckGame createBucketed() {
        return new ButtonBigBlindPhysicalDeckGame(
                List.of(combo("Ah", "Ad", 0.5), combo("Qh", "Jh", 1), combo("7c", "7d", 1)),
                List.of(combo("Ac", "Kc", 1), combo("Tc", "Td", 1), combo("8h", "8s", 1)),
                2,
                4,
                8,
                ButtonBigBlindPhysicalDeckGame.InformationMode.BOARD_BUCKETS);
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }
}
