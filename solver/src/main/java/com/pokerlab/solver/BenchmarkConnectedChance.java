package com.pokerlab.solver;

import java.util.List;
import java.util.Locale;

/** Compares nested public-card abstractions against exact full-deck check-down values. */
public final class BenchmarkConnectedChance {
    private record Menu(int flops, int turns) {}

    private BenchmarkConnectedChance() {}

    public static void main(String[] args) {
        if (args.length > 1)
            throw new IllegalArgumentException("Usage: BenchmarkConnectedChance [iterations]");
        int iterations = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be positive");
        ExactPreflopEquityOracle oracle = new ExactPreflopEquityOracle();
        System.out.println(
                "Research-only BTN/BB; exact 1,712,304-board check-down reference per matchup.");
        System.out.println(
                "flops turns infosets gap_bb exact_checkdown_bb abstract_checkdown_bb signed_error_bb mean_abs_error_bb max_deal_error_bb seconds hash");
        for (Menu menu : List.of(new Menu(1, 2), new Menu(2, 2), new Menu(4, 4))) {
            long started = System.nanoTime();
            ButtonBigBlindContinuationGame game =
                    ButtonBigBlindResearchFixture.create(menu.flops(), menu.turns());
            ConnectedChanceAudit.Report audit = ConnectedChanceAudit.assess(game, oracle);
            CfrSolution solution =
                    new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            HeadsUpBestResponse.Report response = HeadsUpBestResponse.assess(game, solution);
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            System.out.printf(
                    Locale.ROOT,
                    "%d %d %d %.6f %+.6f %+.6f %+.6f %.6f %.6f %.2f %s%n",
                    menu.flops(),
                    menu.turns(),
                    solution.strategy().size(),
                    response.gap(),
                    audit.exactWeightedBb(),
                    audit.abstractWeightedBb(),
                    audit.signedErrorBb(),
                    audit.meanAbsoluteErrorBb(),
                    audit.maxAbsoluteDealErrorBb(),
                    seconds,
                    game.contentHash());
            if (menu.flops() == 1)
                for (var matchup : audit.matchups())
                    System.out.printf(
                            Locale.ROOT,
                            "  BB %s vs BTN %s: p=%.3f exact=%+.6f abstract=%+.6f error=%+.6f%n",
                            matchup.bigBlindCombo(),
                            matchup.buttonCombo(),
                            matchup.dealProbability(),
                            matchup.exactCheckdownBb(),
                            matchup.abstractCheckdownBb(),
                            matchup.errorBb());
        }
        System.out.println(
                "Check-down error is a fixed-policy diagnostic, not a bound on strategic error.");
    }
}
