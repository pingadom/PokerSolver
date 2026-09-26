package com.pokerlab.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Offline command for the synthetic turn-to-river validation fixture. */
public final class GenerateTurnRiverPack {
    private GenerateTurnRiverPack() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3)
            throw new IllegalArgumentException(
                    "Usage: GenerateTurnRiverPack <output.json> <iterations> <generated-at-UTC>");
        Path output = Path.of(arguments[0]).toAbsolutePath();
        TurnRiverSolutionPack pack =
                TurnRiverPackBuilder.generate(
                        TurnRiverValidationSpot.create(),
                        Integer.parseInt(arguments[1]),
                        arguments[2]);
        Files.writeString(
                output,
                TurnRiverPackJson.write(pack) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.printf(
                "Wrote validation-only turn-river pack %s; spot hash %s; pack hash %s; best-response gap %.6f bb%n",
                output, pack.spotHash(), TurnRiverPackJson.contentHash(pack), pack.gameGapBb());
    }
}
