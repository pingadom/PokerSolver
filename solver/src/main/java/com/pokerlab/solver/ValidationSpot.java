package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.ActionKind.*;
import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Small synthetic range fixture for the first six-seat preflop solver validation. */
public final class ValidationSpot {
    private ValidationSpot() {}

    public static PreflopAllInSpot create() {
        return new PreflopAllInSpot(
                "utg-versus-button-five-bet",
                100,
                0.5,
                UTG,
                BTN,
                List.of(
                        new PreflopAllInSpot.Action(SB, POST_SMALL_BLIND, 0.5),
                        new PreflopAllInSpot.Action(BB, POST_BIG_BLIND, 1),
                        new PreflopAllInSpot.Action(UTG, RAISE_TO, 3),
                        new PreflopAllInSpot.Action(HJ, FOLD, 0),
                        new PreflopAllInSpot.Action(CO, FOLD, 0),
                        new PreflopAllInSpot.Action(BTN, RAISE_TO, 10),
                        new PreflopAllInSpot.Action(SB, FOLD, 0),
                        new PreflopAllInSpot.Action(BB, FOLD, 0),
                        new PreflopAllInSpot.Action(UTG, RAISE_TO, 22),
                        new PreflopAllInSpot.Action(BTN, RAISE_TO, 40)),
                List.of(combo("AS", "AH", 1), combo("KS", "KH", 1), combo("AD", "KD", 0.5)),
                List.of(combo("AS", "KC", 0.75), combo("QC", "QD", 1), combo("JS", "JH", 0.5)));
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }
}
