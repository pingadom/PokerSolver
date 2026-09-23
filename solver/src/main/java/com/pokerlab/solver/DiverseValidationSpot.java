package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Wider synthetic range fixture for testing whether a drill can contain distinct decisions. */
public final class DiverseValidationSpot {
    private DiverseValidationSpot() {}

    public static PreflopAllInSpot create() {
        PreflopAllInSpot baseline = ValidationSpot.create();
        return new PreflopAllInSpot(
                "utg-versus-button-five-bet-diverse-validation",
                baseline.effectiveStackBb(),
                baseline.smallBlindBb(),
                baseline.firstSeat(),
                baseline.secondSeat(),
                baseline.priorActions(),
                List.of(
                        combo("AC", "AD", 1),
                        combo("KC", "KD", 1),
                        combo("QC", "QD", 1),
                        combo("AH", "KH", 1),
                        combo("AS", "5S", 0.5),
                        combo("KS", "QS", 0.5),
                        combo("JH", "TH", 0.5),
                        combo("7H", "6H", 0.25)),
                List.of(
                        combo("AS", "AD", 1),
                        combo("KS", "KD", 1),
                        combo("AC", "KC", 1),
                        combo("QH", "QS", 1),
                        combo("AH", "5H", 0.5),
                        combo("JC", "JD", 0.75),
                        combo("KH", "QH", 0.5)));
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }
}
