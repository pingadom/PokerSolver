package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Offline generator for exact-range six-seat full-round research artifacts. */
public final class GenerateSixMaxPreflopPack {
    private GenerateSixMaxPreflopPack() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5
                || (!args[0].equals("exact") && !args[0].equals("sampled"))
                || (args[0].equals("exact") ? args.length != 5 : args.length != 7))
            throw new IllegalArgumentException(
                    "Usage: GenerateSixMaxPreflopPack exact|sampled <spot.json|utg-mix|button-mix> "
                            + "<output.json> <iterations> <generated-at> [boards-per-deal seed]");
        int iterations = Integer.parseInt(args[3]);
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        Instant.parse(args[4]);
        var path = Path.of(args[2]).toAbsolutePath();
        boolean builtin = args[1].equals("utg-mix") || args[1].equals("button-mix");
        if (!builtin && Files.exists(path) && Files.isSameFile(Path.of(args[1]), path))
            throw new IllegalArgumentException("Generator must not overwrite its source spot");
        if (!builtin && Files.size(Path.of(args[1])) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Spot exceeds 16 MiB limit");
        var spot =
                builtin
                        ? new SixMaxPreflopResearchSpot(
                                "six-seat-" + args[1] + "-open-shove",
                                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                                SixMaxPreflopConvergenceMain.ranges(args[1]),
                                CashRakeRule.none(),
                                SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN)
                        : MultiwayPackJson.readFullRoundSpot(Files.readString(Path.of(args[1])));
        boolean exact = args[0].equals("exact");
        long seed = exact ? 0 : Long.parseLong(args[6]);
        MultiwayShowdownOracle oracle =
                exact
                        ? new ExactMultiwayShowdownOracle()
                        : new SharedBoardMultiwayShowdownOracle(Integer.parseInt(args[5]), seed);
        System.out.println("Generating validation-only full-round pack for " + spot.id());
        var pack =
                SixMaxPreflopPackBuilder.build(
                        spot,
                        iterations,
                        CfrSolver.Variant.CFR_PLUS,
                        oracle,
                        exact
                                ? MultiwaySolutionPack.EXACT_ENUMERATION
                                : SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO,
                        seed,
                        args[4]);
        Files.createDirectories(path.getParent());
        Files.writeString(path, MultiwayPackJson.writeFullRound(pack));
        System.out.printf(
                Locale.ROOT,
                "Wrote %s; payoff_entries=%d information_sets=%d nash_conv_bb=%.9f "
                        + "max_payoff_se_bb=%.9f pack_hash=%s%n",
                path,
                pack.payoffs().size(),
                pack.solution().strategy().size(),
                pack.nashConvBb(),
                pack.maxTerminalPayoffSEBb(),
                MultiwayPackJson.fullRoundContentHash(pack));
    }
}
