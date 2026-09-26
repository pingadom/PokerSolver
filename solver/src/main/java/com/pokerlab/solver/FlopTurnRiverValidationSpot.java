package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Synthetic four-combo fixture; the five declared turn cards deliberately restrict chance. */
public final class FlopTurnRiverValidationSpot {
    private FlopTurnRiverValidationSpot() {}

    public static FlopTurnRiverSpot create() {
        return new FlopTurnRiverSpot(
                List.of(card("2c"), card("3d"), card("8s")),
                20,
                80,
                10,
                10,
                10,
                List.of(combo("As", "Ah"), combo("7s", "6s")),
                List.of(combo("Kc", "Kd"), combo("Qc", "Qd")),
                List.of(card("4h"), card("5s"), card("9c"), card("Ts"), card("Ad")));
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }
}
