package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Deliberately synthetic BB-versus-BTN turn fixture for exact chance-node validation. */
public final class TurnRiverValidationSpot {
    private TurnRiverValidationSpot() {}

    public static TurnRiverSpot create() {
        return new TurnRiverSpot(
                List.of(card("2c"), card("3d"), card("4h"), card("8s")),
                20,
                80,
                10,
                10,
                List.of(combo("As", "Ah"), combo("7s", "6s")),
                List.of(combo("Kc", "Kd"), combo("Qc", "Qd")));
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }
}
