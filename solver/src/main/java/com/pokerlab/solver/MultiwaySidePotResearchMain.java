package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Exact-board six-seat side-pot fixture; saved output remains validation-only. */
public final class MultiwaySidePotResearchMain {
    private MultiwaySidePotResearchMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 2)
            throw new IllegalArgumentException(
                    "Usage: MultiwaySidePotResearchMain [iterations] [output-pack]");
        int iterations = args.length == 0 ? 500 : Integer.parseInt(args[0]);
        long started = System.nanoTime();
        MultiwaySidePotSpot spot =
                new MultiwaySidePotSpot(
                        "six-seat-side-pot-fixture",
                        List.of(PreflopAllInSpot.Seat.values()),
                        List.of(
                                List.of(combo("As", "Ah")),
                                List.of(combo("Ks", "Kh")),
                                List.of(combo("Qs", "Qh")),
                                List.of(combo("Js", "Jh")),
                                List.of(combo("Ts", "Th")),
                                List.of(combo("9s", "9h"))),
                        List.of(30.0, 1.0, 2.0, 0.0, 0.5, 1.0),
                        List.of(30.0, 10.0, 20.0, 15.0, 25.0, 5.0),
                        0);
        MultiwaySidePotPack pack =
                MultiwaySidePotPackBuilder.build(
                        spot,
                        iterations,
                        CfrSolver.Variant.CFR_PLUS,
                        new ExactMultiwayShowdownOracle(),
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        "2026-09-26T00:00:00Z");
        double buildSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        MultiwayPreflopCallGame game = pack.rebuildGame();
        MultiwayCallBestResponse.Report report =
                MultiwayCallBestResponse.assess(game, pack.solution());
        double totalSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        var allCall = game.chanceOutcomes(game.initialState()).getFirst().state();
        for (int seat = 1; seat < game.playerCount(); seat++)
            allCall = game.afterAction(allCall, "c");
        System.out.printf(
                Locale.ROOT,
                "Six-seat unequal-stack forced-shove fixture: %d CFR+ iterations, exact boards%n",
                iterations);
        System.out.printf(
                Locale.ROOT,
                "Stacks %s; all-call utility %s; payoff SE %.6fbb%n",
                game.stacksBb(),
                Arrays.toString(game.terminalUtilities(allCall)),
                game.maximumTerminalPayoffStandardErrorBb());
        System.out.printf(
                Locale.ROOT,
                "Profile EV %s; deviation gains %s; NashConv %.6fbb%n",
                report.profileUtilitiesBb(),
                report.deviationGainsBb(),
                report.nashConvBb());
        System.out.printf(
                Locale.ROOT,
                "Pack schema %s; spot hash %s; pack hash %s%n",
                pack.schemaVersion(),
                pack.spotHash(),
                MultiwayPackJson.sidePotContentHash(pack));
        System.out.printf(
                Locale.ROOT, "Pack build %.2fs; total %.2fs%n", buildSeconds, totalSeconds);
        if (args.length == 2) {
            Path output = Path.of(args[1]);
            Files.writeString(output, MultiwayPackJson.writeSidePot(pack), StandardCharsets.UTF_8);
            System.out.println("Wrote validation-only side-pot pack to " + output);
        }
        System.out.println(
                "Synthetic single-combo ranges, forced shove, no rake or trainer route.");
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
