package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;

/** Audits saved source support without building or training a connected continuation. */
public final class SixMaxPrivateRangeCorrelationAuditMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String sourcePackHash,
            String sourceSpotHash,
            SixMaxPrivateRangeCorrelationAudit.Report correlation) {}

    private SixMaxPrivateRangeCorrelationAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPrivateRangeCorrelationAuditMain <source-pack.json> <report.json>");
        var sourcePath = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath().normalize();
        if (sourcePath.toAbsolutePath().normalize().equals(output)
                || (Files.exists(output) && Files.isSameFile(sourcePath, output)))
            throw new IllegalArgumentException("Report cannot replace the source pack");
        if (Files.size(sourcePath) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source pack exceeds 16 MiB limit");
        var source = MultiwayPackJson.readFullRound(Files.readString(sourcePath));
        var report =
                new Artifact(
                        "six-max-private-range-correlation/v1",
                        "VALIDATION_ONLY",
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        SixMaxPrivateRangeCorrelationAudit.assess(source.rebuildGame()));
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), report);
    }
}
