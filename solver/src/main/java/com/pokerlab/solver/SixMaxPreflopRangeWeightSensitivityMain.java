package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Read-only matched-budget range comparisons, with an atomically replaced diagnostic report. */
public final class SixMaxPreflopRangeWeightSensitivityMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            List<SixMaxPreflopRangeWeightSensitivity.Report> comparisons) {
        public Artifact {
            comparisons = List.copyOf(comparisons);
        }
    }

    private SixMaxPreflopRangeWeightSensitivityMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 10)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPreflopRangeWeightSensitivityMain <baseline-pack.json> <variant-pack.json> [up to eight variants] <report.json>");
        var paths =
                java.util.Arrays.stream(args)
                        .map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize())
                        .toList();
        var output = paths.getLast();
        var inputs = paths.subList(0, paths.size() - 1);
        for (var input : inputs) {
            if (output.equals(input)
                    || (Files.exists(output)
                            && Files.exists(input)
                            && Files.isSameFile(output, input)))
                throw new IllegalArgumentException("Report must not alias an input");
            if (Files.size(input) > 16L * 1024 * 1024)
                throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        }
        var baseline = MultiwayPackJson.readFullRound(Files.readString(inputs.getFirst()));
        var reports = new ArrayList<SixMaxPreflopRangeWeightSensitivity.Report>();
        for (var input : inputs.subList(1, inputs.size()))
            reports.add(
                    SixMaxPreflopRangeWeightSensitivity.assess(
                            baseline, MultiwayPackJson.readFullRound(Files.readString(input))));
        String json =
                new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsString(
                                new Artifact(
                                        "six-max-preflop-range-weight-sensitivity/v1",
                                        "VALIDATION_ONLY",
                                        reports));
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".range-sensitivity-", ".json");
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
        System.out.println("Wrote matched-budget range-weight sensitivity " + output);
    }
}
