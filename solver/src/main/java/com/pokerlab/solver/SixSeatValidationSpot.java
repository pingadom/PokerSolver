package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Synthetic two-combo-per-seat research input, not a recommended cash-game range. */
public final class SixSeatValidationSpot {
    private SixSeatValidationSpot() {}

    public static MultiwayCallSpot create() {
        return new MultiwayCallSpot(
                "six-seat-twenty-bb-forced-shove-validation",
                List.of(PreflopAllInSpot.Seat.values()),
                List.of(
                        List.of(combo("As", "Ah"), combo("Ks", "Kh")),
                        List.of(combo("Ad", "Ac"), combo("Qs", "Qh")),
                        List.of(combo("Js", "Jh"), combo("Ts", "Th")),
                        List.of(combo("9s", "9h"), combo("8s", "8h")),
                        List.of(combo("7s", "7h"), combo("6s", "6h")),
                        List.of(combo("5s", "5h"), combo("4s", "4h"))),
                List.of(20.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                20,
                0);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
