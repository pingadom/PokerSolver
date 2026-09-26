package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Locale;

/** Reproducible, restricted-chance preflop-to-river CFR research demonstration. */
public final class ButtonBigBlindResearchMain {
    private ButtonBigBlindResearchMain() {}

    public static void main(String[] args) {
        if (args.length > 1)
            throw new IllegalArgumentException("Usage: ButtonBigBlindResearchMain [iterations]");
        int iterations = args.length == 0 ? 300 : Integer.parseInt(args[0]);
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be positive");
        ButtonBigBlindContinuationGame game =
                new ButtonBigBlindContinuationGame(
                        List.of(new WeightedCombo(card("Ac"), card("Ad"), 0.25), combo("Kh", "Qh")),
                        List.of(combo("Jc", "Jd"), combo("As", "Ks")),
                        List.of(List.of(card("2c"), card("7d"), card("Th"))),
                        List.of(card("3s"), card("4s")),
                        2,
                        4,
                        8);
        long started = System.nanoTime();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        HeadsUpBestResponse.Report report = HeadsUpBestResponse.assess(game, solution);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(
                Locale.ROOT,
                "Research-only BTN vs BB game: %d CFR+ iterations, %.2fs, profile %.6fbb, Nash gap %.6fbb%n",
                iterations,
                seconds,
                report.profileValue(),
                report.gap());
        System.out.println(
                "100bb, no rake, UTG/HJ/CO/SB fixed folds, BTN fold/open 3bb, BB fold/call;");
        System.out.println(
                "one declared flop, two declared turns, exact remaining rivers; synthetic ranges.");
        System.out.println("Game hash: " + game.contentHash());
        solution.strategy().entrySet().stream()
                .filter(entry -> entry.getKey().contains(":P:"))
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
    }

    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }
}
