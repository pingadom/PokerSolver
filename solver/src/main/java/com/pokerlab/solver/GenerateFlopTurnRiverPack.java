package com.pokerlab.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Offline exact-deck pack builder; refuses output when the measured gap misses the target. */
public final class GenerateFlopTurnRiverPack {
    private GenerateFlopTurnRiverPack() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 4)
            throw new IllegalArgumentException(
                    "Usage: GenerateFlopTurnRiverPack <output.json.gz> <iterations> "
                            + "<generated-at-UTC> <maximum-gap-bb>");
        Path output = Path.of(arguments[0]).toAbsolutePath();
        if (!output.getFileName().toString().endsWith(".json.gz"))
            throw new IllegalArgumentException("Flop pack output must end in .json.gz");
        FlopTurnRiverSolutionPack pack =
                FlopTurnRiverPackBuilder.generate(
                        FlopTurnRiverValidationSpot.create().withFullTurnDeck(),
                        Integer.parseInt(arguments[1]),
                        arguments[2],
                        Double.parseDouble(arguments[3]));
        byte[] compressed = FlopTurnRiverPackJson.gzip(pack);
        Files.write(output, compressed, StandardOpenOption.CREATE_NEW);
        System.out.printf(
                "Wrote validation-only exact flop pack %s; spot hash %s; pack hash %s; "
                        + "best-response gap %.9f bb; compressed bytes %d%n",
                output,
                pack.spotHash(),
                FlopTurnRiverPackJson.contentHash(pack),
                pack.gameGapBb(),
                compressed.length);
    }
}
