package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/** Nested, reproducible chance menus for the connected-game research benchmark. */
public final class ButtonBigBlindResearchFixture {
    private static final List<List<Card>> FLOPS =
            List.of(
                    List.of(card("2c"), card("7d"), card("Th")),
                    List.of(card("Ac"), card("7c"), card("2d")),
                    List.of(card("Kh"), card("8d"), card("3c")),
                    List.of(card("Jc"), card("5d"), card("9h")));
    private static final List<Card> TURNS = List.of(card("3s"), card("4s"), card("5s"), card("6s"));

    private ButtonBigBlindResearchFixture() {}

    public static ButtonBigBlindContinuationGame create(int flopCount, int turnCount) {
        if (flopCount < 1 || flopCount > FLOPS.size() || turnCount < 1 || turnCount > TURNS.size())
            throw new IllegalArgumentException("Fixture supports 1-4 flops and 1-4 turns");
        return create(FLOPS.subList(0, flopCount), TURNS.subList(0, turnCount));
    }

    public static ButtonBigBlindContinuationGame create(List<List<Card>> flops, List<Card> turns) {
        return new ButtonBigBlindContinuationGame(
                List.of(new WeightedCombo(card("Ac"), card("Ad"), 0.25), combo("Kh", "Qh")),
                List.of(combo("Jc", "Jd"), combo("As", "Ks")),
                flops,
                turns,
                2,
                4,
                8);
    }

    /** Distinct exact combos that are never used when selecting the chance menu. */
    public static ButtonBigBlindContinuationGame holdout(List<List<Card>> flops, List<Card> turns) {
        return new ButtonBigBlindContinuationGame(
                List.of(combo("As", "Ah"), combo("Qs", "Js")),
                List.of(combo("Tc", "Td"), combo("9c", "8c")),
                flops,
                turns,
                2,
                4,
                8);
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }
}
