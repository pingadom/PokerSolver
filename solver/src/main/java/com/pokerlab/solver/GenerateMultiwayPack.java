package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Offline generation from a JSON spot definition or the built-in synthetic six-seat fixture. */
public final class GenerateMultiwayPack {
    private GenerateMultiwayPack() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5
                || (!args[0].equals("exact") && !args[0].equals("sampled"))
                || (args[0].equals("exact") ? args.length != 5 : args.length != 7))
            throw new IllegalArgumentException(
                    "Usage: GenerateMultiwayPack exact|sampled <spot.json|six-seat-fixture> <output.json> <iterations> <generated-at> [trials seed]");
        int iterations = Integer.parseInt(args[3]);
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        Instant.parse(args[4]);
        MultiwayCallSpot spot =
                args[1].equals("six-seat-fixture")
                        ? SixSeatValidationSpot.create()
                        : MultiwayPackJson.readSpot(Files.readString(Path.of(args[1])));
        boolean exact = args[0].equals("exact");
        long seed = exact ? 0 : Long.parseLong(args[6]);
        MultiwayShowdownOracle oracle =
                exact
                        ? new ExactMultiwayShowdownOracle()
                        : new SeededMultiwayShowdownOracle(Integer.parseInt(args[5]), seed);
        long started = System.nanoTime();
        System.out.printf(
                Locale.ROOT,
                "Generating %s payoffs for %s (%d seats)%n",
                args[0],
                spot.id(),
                spot.seats().size());
        var pack =
                MultiwayPackBuilder.build(
                        spot,
                        iterations,
                        CfrSolver.Variant.CFR_PLUS,
                        oracle,
                        exact ? "EXACT_ENUMERATION" : "SEEDED_MONTE_CARLO",
                        seed,
                        args[4]);
        Path path = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, MultiwayPackJson.write(pack));
        System.out.printf(
                Locale.ROOT,
                "Wrote %s; %d payoff entries; NashConv %.9f bb; maximum payoff SE %.9f bb; %.2fs%n",
                path,
                pack.payoffs().size(),
                pack.nashConvBb(),
                pack.maxTerminalPayoffSEBb(),
                (System.nanoTime() - started) / 1e9);
        System.out.println("Pack hash: " + MultiwayPackJson.contentHash(pack));
        System.out.println(
                "VALIDATION_ONLY: exact payoffs do not validate range realism or omitted betting actions.");
    }
}
