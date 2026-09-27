package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** The same synthetic prior hands as the restricted-chance connected research fixture. */
public final class ButtonBigBlindPhysicalDeckFixture {
    private ButtonBigBlindPhysicalDeckFixture() {}

    public static ButtonBigBlindPhysicalDeckGame create() {
        return create(ButtonBigBlindPhysicalDeckGame.InformationMode.EXACT_PUBLIC_CARDS);
    }

    public static ButtonBigBlindPhysicalDeckGame createBucketed() {
        return create(ButtonBigBlindPhysicalDeckGame.InformationMode.BOARD_BUCKETS);
    }

    private static ButtonBigBlindPhysicalDeckGame create(
            ButtonBigBlindPhysicalDeckGame.InformationMode informationMode) {
        return new ButtonBigBlindPhysicalDeckGame(
                List.of(
                        new WeightedCombo(Card.parse("Ac"), Card.parse("Ad"), 0.25),
                        combo("Kh", "Qh")),
                List.of(combo("Jc", "Jd"), combo("As", "Ks")),
                2,
                4,
                8,
                informationMode);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
