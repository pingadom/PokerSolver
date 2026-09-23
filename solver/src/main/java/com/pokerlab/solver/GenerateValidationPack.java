package com.pokerlab.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Explicit offline command for writing a validation-only solution pack. */
public final class GenerateValidationPack {
    private GenerateValidationPack() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 6 && arguments.length != 4)
            throw new IllegalArgumentException(
                    "Usage: mc <output.json> <iterations> <trials-per-matchup> <seed> <generated-at-UTC> | exact <output.json> <iterations> <generated-at-UTC>");
        Path output = Path.of(arguments[1]).toAbsolutePath();
        PreflopSolutionPack pack;
        if ("mc".equals(arguments[0]) && arguments.length == 6) {
            pack =
                    PreflopPackBuilder.generate(
                            ValidationSpot.create(),
                            Integer.parseInt(arguments[2]),
                            Integer.parseInt(arguments[3]),
                            Long.parseLong(arguments[4]),
                            arguments[5]);
        } else if ("exact".equals(arguments[0]) && arguments.length == 4) {
            pack =
                    PreflopPackBuilder.generateExact(
                            ValidationSpot.create(), Integer.parseInt(arguments[2]), arguments[3]);
        } else {
            throw new IllegalArgumentException("Unknown or incomplete payoff method");
        }
        Files.writeString(
                output,
                PreflopPackJson.write(pack) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.printf(
                "Wrote validation-only pack %s; spot hash %s; estimated-game gap %.6f bb; max called-payoff SE %.6f bb%n",
                output,
                pack.spotHash(),
                pack.estimatedGameGapBb(),
                pack.maximumCalledPayoffStandardErrorBb());
    }
}
