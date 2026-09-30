package com.pokerlab.solver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Exact-response calibration across several independently chosen physical-card chance menus. */
public final class BenchmarkFiniteChanceResponseMenuSweep {
    private BenchmarkFiniteChanceResponseMenuSweep() {}

    public static void main(String[] args) {
        if (args.length > 6)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkFiniteChanceResponseMenuSweep [3x3|5x5] [baseline-iterations] [sampled-iterations] [chance-points-per-street] [response-seed] [comma-separated-chance-seeds]");
        var profile =
                args.length >= 1
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[0])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        int baselineIterations = args.length >= 2 ? Integer.parseInt(args[1]) : 300;
        int sampledIterations = args.length >= 3 ? Integer.parseInt(args[2]) : 10_000;
        int chancePoints = args.length >= 4 ? Integer.parseInt(args[3]) : 2;
        long responseSeed = args.length >= 5 ? Long.parseLong(args[4]) : 200_045;
        List<Long> chanceSeeds =
                args.length == 6
                        ? Arrays.stream(args[5].split(",", -1))
                                .map(String::trim)
                                .map(Long::parseLong)
                                .toList()
                        : List.of(142L, 143L, 144L);
        var physical =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var report =
                FiniteChanceResponseMenuSweep.assess(
                        physical,
                        chancePoints,
                        chanceSeeds,
                        baselineIterations,
                        sampledIterations,
                        responseSeed);
        System.out.printf(
                Locale.ROOT,
                "Menu sweep %s: %d points/street, %d CFR+ baseline iterations per menu, %d sampled private-deal traversals, response seed %d, physical hash %s%n",
                profile,
                chancePoints,
                baselineIterations,
                sampledIterations,
                responseSeed,
                report.physicalGameHash());
        for (var menu : report.menus()) {
            System.out.printf(
                    Locale.ROOT,
                    "Menu seed %d: %d root deals, %d exact-root iterations (%d deal traversals); flop/turn/river quantiles %s / %s / %s%n",
                    menu.chanceSeed(),
                    menu.rootDeals(),
                    menu.exactRootIterations(),
                    menu.rootDeals() * menu.exactRootIterations(),
                    menu.flopQuantiles(),
                    menu.turnQuantiles(),
                    menu.riverQuantiles());
            for (var player : menu.players())
                System.out.printf(
                        Locale.ROOT,
                        "  %s exact gain %+.6fbb; sampled shortfall average/final %.6f/%.6fbb; exact-root shortfall average/final %.6f/%.6fbb; final improvement %+.6fbb; missing target keys sampled/root %d/%d%n",
                        player.player() == 0 ? "BB" : "BTN",
                        player.exactResponseGainBb(),
                        player.sampledAverageShortfallBb(),
                        player.sampledFinalShortfallBb(),
                        player.exactRootAverageShortfallBb(),
                        player.exactRootFinalShortfallBb(),
                        player.finalRegretImprovementBb(),
                        player.sampledMissingTargetKeys(),
                        player.exactRootMissingTargetKeys());
        }
        for (int player = 0; player <= 1; player++) {
            var summary = report.summary(player);
            System.out.printf(
                    Locale.ROOT,
                    "%s across %d menus: exact-root final-regret wins %d; mean shortfall sampled/root %.6f/%.6fbb; worst %.6f/%.6fbb%n",
                    player == 0 ? "BB" : "BTN",
                    summary.menus(),
                    summary.exactRootWins(),
                    summary.meanSampledFinalShortfallBb(),
                    summary.meanExactRootFinalShortfallBb(),
                    summary.worstSampledFinalShortfallBb(),
                    summary.worstExactRootFinalShortfallBb());
        }
        System.out.println(
                "Each exact bound applies only to its restricted chance menu. A few menus cannot certify full-deck exploitability or a globally better response mode.");
    }
}
