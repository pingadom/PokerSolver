package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Map;

/** A disjoint synthetic 3-by-3 prior for testing connected-game range sensitivity. */
public final class ButtonBigBlindRangeValidationFixture {
    public enum RangeProfile {
        VALIDATION_3X3,
        STRESS_5X5;

        public static RangeProfile parse(String label) {
            return switch (label) {
                case "3x3" -> VALIDATION_3X3;
                case "5x5" -> STRESS_5X5;
                default -> throw new IllegalArgumentException("Range must be 3x3 or 5x5");
            };
        }
    }

    private ButtonBigBlindRangeValidationFixture() {}

    public static ButtonBigBlindPhysicalDeckGame createBucketed() {
        return create(ButtonBigBlindPhysicalDeckGame.InformationMode.BOARD_BUCKETS);
    }

    public static ButtonBigBlindPhysicalDeckGame createCoarseBucketed() {
        return create(ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS);
    }

    public static ButtonBigBlindPhysicalDeckGame createTextureBucketed() {
        return create(ButtonBigBlindPhysicalDeckGame.InformationMode.TEXTURE_BOARD_BUCKETS);
    }

    private static ButtonBigBlindPhysicalDeckGame create(
            ButtonBigBlindPhysicalDeckGame.InformationMode informationMode) {
        return create(informationMode, RangeProfile.VALIDATION_3X3);
    }

    public static ButtonBigBlindPhysicalDeckGame create(
            ButtonBigBlindPhysicalDeckGame.InformationMode informationMode, RangeProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        var button = List.of(combo("Ah", "Ad", 0.5), combo("Qh", "Jh", 1), combo("7c", "7d", 1));
        var bigBlind = List.of(combo("Ac", "Kc", 1), combo("Tc", "Td", 1), combo("8h", "8s", 1));
        if (profile == RangeProfile.STRESS_5X5) {
            button =
                    List.of(
                            combo("Ah", "Ad", 0.5),
                            combo("Qh", "Jh", 1),
                            combo("7c", "7d", 1),
                            combo("6h", "5h", 1),
                            combo("Ks", "Qs", 1));
            bigBlind =
                    List.of(
                            combo("Ac", "Kc", 1),
                            combo("Tc", "Td", 1),
                            combo("8h", "8s", 1),
                            combo("4c", "4d", 1),
                            combo("9h", "9s", 1));
        }
        return new ButtonBigBlindPhysicalDeckGame(button, bigBlind, 2, 4, 8, informationMode);
    }

    /** Exogenous, synthetic action frequencies for a controlled Bayes-update experiment. */
    public static PreflopActionBelief actionBelief(RangeProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        Map<String, Double> button =
                profile == RangeProfile.STRESS_5X5
                        ? Map.of(
                                "Ad Ah", 0.95,
                                "Jh Qh", 0.75,
                                "7c 7d", 0.50,
                                "5h 6h", 0.35,
                                "Ks Qs", 0.65)
                        : Map.of("Ad Ah", 0.95, "Jh Qh", 0.75, "7c 7d", 0.50);
        Map<String, Double> bigBlind =
                profile == RangeProfile.STRESS_5X5
                        ? Map.of(
                                "Ac Kc", 0.80,
                                "Tc Td", 0.75,
                                "8h 8s", 0.60,
                                "4c 4d", 0.35,
                                "9h 9s", 0.65)
                        : Map.of("Ac Kc", 0.80, "Tc Td", 0.75, "8h 8s", 0.60);
        return new PreflopActionBelief(button, bigBlind);
    }

    public static ButtonBigBlindPhysicalDeckGame createActionBucketed(RangeProfile profile) {
        var base =
                create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var deals = base.chanceOutcomes(base.initialState());
        var button = deals.stream().map(deal -> deal.state().button()).distinct().toList();
        var bigBlind = deals.stream().map(deal -> deal.state().bigBlind()).distinct().toList();
        return new ButtonBigBlindPhysicalDeckGame(
                button,
                bigBlind,
                2,
                4,
                8,
                ButtonBigBlindPhysicalDeckGame.InformationMode.PREFLOP_ACTION_RIVER_BUCKETS,
                actionBelief(profile));
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }
}
