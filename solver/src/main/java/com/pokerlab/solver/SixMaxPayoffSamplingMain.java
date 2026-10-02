package com.pokerlab.solver;

import java.util.List;
import java.util.Locale;

/** Prints the bounded empirical payoff-SE budget ladder for the exact 64-deal fixtures. */
public final class SixMaxPayoffSamplingMain {
    private SixMaxPayoffSamplingMain() {}

    public static void main(String[] args) {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPayoffSamplingMain <hierarchy|mixed-pairs> "
                            + "<initial-boards-per-deal> <maximum-boards-per-deal> "
                            + "<target-max-terminal-se-bb> <seed>");
        var ranges =
                switch (args[0]) {
                    case "hierarchy" -> SixMaxExpandedConvergenceMain.fixtureRanges();
                    case "mixed-pairs" -> SixMaxExpandedConvergenceMain.mixedPairRanges();
                    default -> throw new IllegalArgumentException("Unknown fixture: " + args[0]);
                };
        int initialBoards = Integer.parseInt(args[1]);
        int maximumBoards = Integer.parseInt(args[2]);
        double target = Double.parseDouble(args[3]);
        long seed = Long.parseLong(args[4]);
        var result =
                SixMaxPreflopPayoffSamplingAudit.run(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        CashRakeRule.none(),
                        seed,
                        initialBoards,
                        maximumBoards,
                        target);
        System.out.printf(
                Locale.ROOT,
                "fixture=%s chance_model=%s deals=%d target_max_terminal_se_bb=%.9f "
                        + "target_met=%s final_boards_per_deal=%d%n",
                args[0],
                result.game().chanceModel(),
                result.game().chanceOutcomes(result.game().initialState()).size(),
                target,
                result.targetMet(),
                result.finalBoardsPerDeal());
        System.out.println("boards_per_deal,max_terminal_payoff_se_bb");
        for (var row : result.rows())
            System.out.printf(
                    Locale.ROOT,
                    "%d,%.9f%n",
                    row.boardsPerDeal(),
                    row.maximumTerminalPayoffStandardErrorBb());
    }
}
