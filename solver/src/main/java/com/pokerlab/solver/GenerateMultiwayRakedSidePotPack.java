package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Generates a validation-only raked research pack from an exact no-rake side-pot pack. */
public final class GenerateMultiwayRakedSidePotPack {
    private GenerateMultiwayRakedSidePotPack() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7)
            throw new IllegalArgumentException(
                    "Usage: GenerateMultiwayRakedSidePotPack <exact-source-pack.json> <output.json> <rake-fraction> <cap-bb> <no-flop-no-drop:true|false> <iterations> <generated-at>");
        Path sourcePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (sourcePath.equals(output))
            throw new IllegalArgumentException("Source and raked output paths must differ");
        var source = MultiwayPackJson.readSidePot(Files.readString(sourcePath));
        var rule =
                new CashRakeRule(
                        Double.parseDouble(args[2]),
                        Double.parseDouble(args[3]),
                        switch (args[4]) {
                            case "true" -> true;
                            case "false" -> false;
                            default ->
                                    throw new IllegalArgumentException(
                                            "no-flop-no-drop must be true or false");
                        });
        int iterations = Integer.parseInt(args[5]);
        Instant.parse(args[6]);
        long started = System.nanoTime();
        var pack =
                MultiwayRakedSidePotPackBuilder.build(
                        source, rule, iterations, CfrSolver.Variant.CFR_PLUS, args[6]);
        Files.createDirectories(output.getParent());
        String json = MultiwayPackJson.writeRakedSidePot(pack);
        Files.writeString(output, json);
        if (!json.equals(
                MultiwayPackJson.writeRakedSidePot(
                        MultiwayPackJson.readRakedSidePot(Files.readString(output)))))
            throw new IllegalStateException("Written raked pack did not round-trip");
        System.out.printf(
                Locale.ROOT,
                "Wrote validation-only raked pack %s; %d bytes, %d seats; NashConv %.9fbb, expected rake %.9fbb, maximum payoff SE %.9fbb; %.2fs%n",
                output,
                Files.size(output),
                source.spot().seats().size(),
                pack.nashConvBb(),
                pack.expectedRakeBb(),
                pack.maxTerminalPayoffSEBb(),
                (System.nanoTime() - started) / 1e9);
        System.out.println("Pack hash: " + MultiwayPackJson.rakedSidePotContentHash(pack));
        System.out.println(
                "VALIDATION_ONLY: the forced-shove tree, synthetic ranges and declared rake do not establish real cash-game strategy.");
    }
}
