package com.pokerlab.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Explicit offline command for the first fixed-board river validation fixture. */
public final class GenerateRiverPack {
    private GenerateRiverPack() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3)
            throw new IllegalArgumentException(
                    "Usage: GenerateRiverPack <output.json> <iterations> <generated-at-UTC>");
        Path output = Path.of(arguments[0]).toAbsolutePath();
        RiverSolutionPack pack =
                RiverPackBuilder.generate(
                        RiverValidationSpot.create(), Integer.parseInt(arguments[1]), arguments[2]);
        Files.writeString(
                output,
                RiverPackJson.write(pack) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.printf(
                "Wrote validation-only river pack %s; spot hash %s; pack hash %s; best-response gap %.6f bb%n",
                output, pack.spotHash(), RiverPackJson.contentHash(pack), pack.gameGapBb());
    }
}
