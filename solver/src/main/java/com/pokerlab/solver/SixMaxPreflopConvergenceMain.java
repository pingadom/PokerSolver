package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reproducible exact-board convergence study for two deliberately tiny physical-card fixtures. */
public final class SixMaxPreflopConvergenceMain {
    private SixMaxPreflopConvergenceMain() {}

    public static void main(String[] args) {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPreflopConvergenceMain <utg-mix|button-mix> "
                            + "<shove|open-shove> <none|five-percent-cap-one> "
                            + "<iterations,iterations,...>");
        List<List<WeightedCombo>> ranges = ranges(args[0]);
        List<Double> raises =
                switch (args[1]) {
                    case "shove" -> List.of(100.0);
                    case "open-shove" -> List.of(3.0, 100.0);
                    default -> throw new IllegalArgumentException("Unknown raise menu: " + args[1]);
                };
        CashRakeRule rake =
                switch (args[2]) {
                    case "none" -> CashRakeRule.none();
                    case "five-percent-cap-one" -> new CashRakeRule(0.05, 1, true);
                    default -> throw new IllegalArgumentException("Unknown rake rule: " + args[2]);
                };
        List<Integer> budgets = parseBudgets(args[3]);
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, raises),
                        ranges,
                        rake,
                        new ExactMultiwayShowdownOracle());
        System.out.printf(
                Locale.ROOT,
                "fixture=%s raise_menu=%s rake=%s chance_model=%s chance_samples=%d "
                        + "joint_deals=%d public_states=%d "
                        + "max_payoff_se_bb=%.12f%n",
                args[0],
                args[1],
                args[2],
                game.chanceModel(),
                game.chanceSamples(),
                game.chanceOutcomes(game.initialState()).size(),
                game.treeSummary().totalStates(),
                game.maximumTerminalPayoffStandardErrorBb());
        System.out.println(
                "iterations,information_sets,nash_conv_bb,max_deviation_bb,profile_total_bb,"
                        + "utg_gain_bb,hj_gain_bb,co_gain_bb,btn_gain_bb,sb_gain_bb,bb_gain_bb");
        for (var row : SixMaxPreflopConvergenceAudit.run(game, budgets)) {
            System.out.printf(
                    Locale.ROOT,
                    "%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                    row.iterations(),
                    row.informationSets(),
                    row.nashConvBb(),
                    row.largestDeviationBb(),
                    row.profileTotalBb(),
                    row.deviationGainsBb().get(0),
                    row.deviationGainsBb().get(1),
                    row.deviationGainsBb().get(2),
                    row.deviationGainsBb().get(3),
                    row.deviationGainsBb().get(4),
                    row.deviationGainsBb().get(5));
        }
    }

    static List<Integer> parseBudgets(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("At least one budget is needed");
        List<Integer> budgets = new ArrayList<>();
        int previous = 0;
        for (String part : input.split(",", -1)) {
            int value;
            try {
                value = Integer.parseInt(part.trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid iteration budget: " + part, error);
            }
            if (value <= previous)
                throw new IllegalArgumentException("Budgets must be positive and increasing");
            budgets.add(value);
            previous = value;
        }
        return List.copyOf(budgets);
    }

    private static List<List<WeightedCombo>> ranges(String fixture) {
        List<List<WeightedCombo>> ranges = new ArrayList<>();
        ranges.add(List.of(combo("AS", "AH")));
        ranges.add(List.of(combo("KS", "KH")));
        ranges.add(List.of(combo("QS", "QH")));
        ranges.add(List.of(combo("JS", "JH")));
        ranges.add(List.of(combo("TS", "TH")));
        ranges.add(List.of(combo("9S", "9H")));
        switch (fixture) {
            case "utg-mix" -> ranges.set(0, List.of(combo("AS", "AH"), combo("5S", "5H")));
            case "button-mix" -> ranges.set(3, List.of(combo("JS", "JH"), combo("5S", "5H")));
            default -> throw new IllegalArgumentException("Unknown fixture: " + fixture);
        }
        return List.copyOf(ranges);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
