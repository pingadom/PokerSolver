package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Offline generation from a side-pot spot definition or the diverse six-seat fixture. */
public final class GenerateMultiwaySidePotPack {
    private GenerateMultiwaySidePotPack() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5
                || (!args[0].equals("exact") && !args[0].equals("sampled"))
                || (args[0].equals("exact") ? args.length != 5 : args.length != 7))
            throw new IllegalArgumentException(
                    "Usage: GenerateMultiwaySidePotPack exact|sampled <spot.json|six-seat-diverse-fixture> <output.json> <iterations> <generated-at> [trials seed]");
        int iterations = Integer.parseInt(args[3]);
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        Instant.parse(args[4]);
        MultiwaySidePotSpot spot =
                args[1].equals("six-seat-diverse-fixture")
                        ? SixSeatSidePotValidationSpot.create()
                        : MultiwayPackJson.readSidePotSpot(Files.readString(Path.of(args[1])));
        boolean exact = args[0].equals("exact");
        long seed = exact ? 0 : Long.parseLong(args[6]);
        MultiwayShowdownOracle oracle =
                exact
                        ? new ExactMultiwayShowdownOracle()
                        : new SeededMultiwayShowdownOracle(Integer.parseInt(args[5]), seed);
        long started = System.nanoTime();
        System.out.printf(
                Locale.ROOT,
                "Generating %s side-pot payoffs for %s (%d seats)%n",
                args[0],
                spot.id(),
                spot.seats().size());
        MultiwaySidePotPack pack =
                MultiwaySidePotPackBuilder.build(
                        spot,
                        iterations,
                        CfrSolver.Variant.CFR_PLUS,
                        oracle,
                        exact
                                ? MultiwaySolutionPack.EXACT_ENUMERATION
                                : MultiwaySolutionPack.SEEDED_MONTE_CARLO,
                        seed,
                        args[4]);
        Path path = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, MultiwayPackJson.writeSidePot(pack));
        MultiwayPreflopCallGame game = pack.rebuildGame();
        System.out.printf(
                Locale.ROOT,
                "Wrote %s; %d joint deals, %d payoff entries; NashConv %.9f bb; maximum payoff SE %.9f bb; %.2fs%n",
                path,
                game.chanceOutcomes(game.initialState()).size(),
                pack.payoffs().size(),
                pack.nashConvBb(),
                pack.maxTerminalPayoffSEBb(),
                (System.nanoTime() - started) / 1e9);
        System.out.println("Pack hash: " + MultiwayPackJson.sidePotContentHash(pack));
        System.out.println(
                "VALIDATION_ONLY: exact payoffs do not validate range realism or omitted betting actions.");
    }
}
