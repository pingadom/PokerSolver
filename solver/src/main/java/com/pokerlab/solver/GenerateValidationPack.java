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
        String mode = arguments.length == 0 ? "" : arguments[0];
        boolean sampled = "mc".equals(mode) || "mc-plus".equals(mode);
        boolean exact = "exact".equals(mode) || "exact-plus".equals(mode);
        if (!((sampled && (arguments.length == 6 || arguments.length == 7))
                || (exact && (arguments.length == 4 || arguments.length == 5))))
            throw new IllegalArgumentException(
                    "Usage: <mc|mc-plus> <output.json> <iterations> <trials-per-matchup> <seed> <generated-at-UTC> [baseline|diverse] | <exact|exact-plus> <output.json> <iterations> <generated-at-UTC> [baseline|diverse]");
        Path output = Path.of(arguments[1]).toAbsolutePath();
        PreflopSolutionPack pack;
        boolean hasSpotChoice = arguments.length == 5 || arguments.length == 7;
        String spotChoice = hasSpotChoice ? arguments[arguments.length - 1] : "baseline";
        PreflopAllInSpot spot =
                switch (spotChoice) {
                    case "baseline" -> ValidationSpot.create();
                    case "diverse" -> DiverseValidationSpot.create();
                    default -> throw new IllegalArgumentException("Unknown validation spot");
                };
        CfrSolver.Variant variant =
                mode.endsWith("-plus") ? CfrSolver.Variant.CFR_PLUS : CfrSolver.Variant.VANILLA;
        if (sampled) {
            pack =
                    PreflopPackBuilder.generate(
                            spot,
                            Integer.parseInt(arguments[2]),
                            Integer.parseInt(arguments[3]),
                            Long.parseLong(arguments[4]),
                            arguments[5],
                            variant);
        } else {
            pack =
                    PreflopPackBuilder.generateExact(
                            spot, Integer.parseInt(arguments[2]), arguments[3], variant);
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
        System.out.println(
                "Provisional content screening (not publication): "
                        + PreflopPackScreening.assess(pack).findings());
    }
}
