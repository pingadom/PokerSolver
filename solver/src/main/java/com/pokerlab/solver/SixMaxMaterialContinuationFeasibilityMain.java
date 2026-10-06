package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Read-only all-flop preflight; no training, menu selection, or trainer publication. */
public final class SixMaxMaterialContinuationFeasibilityMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String sourcePackHash,
            String sourceSpotHash,
            SixMaxMaterialContinuationFeasibility.Report feasibility) {}

    private SixMaxMaterialContinuationFeasibilityMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxMaterialContinuationFeasibilityMain <source-pack.json> <report.json> [candidate-limit maximum-histories]");
        var input = Path.of(args[0]).toAbsolutePath().normalize();
        var output = Path.of(args[1]).toAbsolutePath().normalize();
        if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output)))
            throw new IllegalArgumentException("Report must not alias the source");
        if (Files.size(input) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source exceeds 16 MiB limit");
        var defaults = SixMaxMaterialContinuationFeasibility.Settings.researchDefault();
        var settings =
                args.length == 2
                        ? defaults
                        : new SixMaxMaterialContinuationFeasibility.Settings(
                                Integer.parseInt(args[2]),
                                Integer.parseInt(args[3]),
                                defaults.coverage());
        var source = MultiwayPackJson.readFullRound(Files.readString(input));
        var artifact =
                new Artifact(
                        "six-max-material-continuation-feasibility/v1",
                        "VALIDATION_ONLY",
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        SixMaxMaterialContinuationFeasibility.assess(
                                source.rebuildGame(), source.solution(), settings));
        String json =
                new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsString(artifact);
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".material-feasibility-", ".json");
        try {
            Files.writeString(temporary, json.replace("\r\n", "\n") + "\n");
            Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        System.out.println(artifact.feasibility().status() + ": " + output);
    }
}
