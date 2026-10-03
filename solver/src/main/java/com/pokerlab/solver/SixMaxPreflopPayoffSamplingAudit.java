package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Escalates a fixed seeded board stream until the estimated game's largest terminal-payoff standard
 * error meets a declared target or the board budget is exhausted. This is an empirical SE gate, not
 * a confidence interval or an equilibrium-quality certificate.
 */
public final class SixMaxPreflopPayoffSamplingAudit {
    public record Row(int boardsPerDeal, double maximumTerminalPayoffStandardErrorBb) {}

    public record Result(
            SixMaxPreflopCheckdownGame game,
            List<Row> rows,
            double targetMaximumTerminalPayoffStandardErrorBb,
            boolean targetMet) {
        public Result {
            rows = List.copyOf(rows);
        }

        public int finalBoardsPerDeal() {
            return rows.getLast().boardsPerDeal();
        }

        /** Use this before consuming a game whose declared payoff-SE target is required. */
        public SixMaxPreflopCheckdownGame requireTargetMet() {
            if (!targetMet)
                throw new IllegalStateException(
                        "Payoff sampling did not meet the declared terminal-SE target");
            return game;
        }
    }

    private SixMaxPreflopPayoffSamplingAudit() {}

    public static Result run(
            SixMaxPreflopBetting.Rules rules,
            List<List<WeightedCombo>> ranges,
            CashRakeRule rake,
            long seed,
            int initialBoardsPerDeal,
            int maximumBoardsPerDeal,
            double targetMaximumTerminalPayoffStandardErrorBb) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(ranges, "ranges");
        Objects.requireNonNull(rake, "rake");
        if (initialBoardsPerDeal < 1 || maximumBoardsPerDeal < initialBoardsPerDeal)
            throw new IllegalArgumentException("Invalid board budgets");
        if (!Double.isFinite(targetMaximumTerminalPayoffStandardErrorBb)
                || targetMaximumTerminalPayoffStandardErrorBb <= 0)
            throw new IllegalArgumentException("Target payoff standard error must be positive");

        int boards = initialBoardsPerDeal;
        List<Row> rows = new ArrayList<>();
        while (true) {
            var game =
                    new SixMaxPreflopCheckdownGame(
                            rules,
                            ranges,
                            rake,
                            new SharedBoardMultiwayShowdownOracle(boards, seed));
            double estimatedError = game.maximumTerminalPayoffStandardErrorBb();
            rows.add(new Row(boards, estimatedError));
            boolean targetMet = estimatedError <= targetMaximumTerminalPayoffStandardErrorBb;
            if (targetMet || boards == maximumBoardsPerDeal)
                return new Result(
                        game, rows, targetMaximumTerminalPayoffStandardErrorBb, targetMet);
            boards = (int) Math.min((long) maximumBoardsPerDeal, 2L * boards);
        }
    }
}
