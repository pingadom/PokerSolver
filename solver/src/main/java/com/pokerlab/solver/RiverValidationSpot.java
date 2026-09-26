package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Synthetic, fixed-board river fixture for checking betting and best-response math. */
public final class RiverValidationSpot {
    private RiverValidationSpot() {}

    public static RiverBetSpot create() {
        return new RiverBetSpot(
                "button-versus-big-blind-river-validation",
                PreflopAllInSpot.Seat.BB,
                PreflopAllInSpot.Seat.BTN,
                List.of(card("2c"), card("3d"), card("4h"), card("8s"), card("9c")),
                20,
                80,
                10,
                List.of(combo("As", "Ah"), combo("Ts", "Th"), combo("7s", "6s")),
                List.of(combo("Kc", "Kd"), combo("Qc", "Qd"), combo("5c", "5d")));
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }
}
