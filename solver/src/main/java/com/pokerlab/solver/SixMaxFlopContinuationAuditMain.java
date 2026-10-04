package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Offline conditional flop solve export; not an admitted trainer pack or a connected equilibrium.
 */
public final class SixMaxFlopContinuationAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String sourceContinuationModel,
            String publicationStatus,
            double sourceNashConvBb,
            String interpretation,
            SixMaxFlopContinuationAudit.Report report) {}

    private SixMaxFlopContinuationAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: SixMaxFlopContinuationAuditMain <pack.json> <output.json> <flop-seed> <iterations> <bet-pot-fraction>");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit output must not overwrite the source pack");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var report =
                SixMaxFlopContinuationAudit.assess(
                        pack.rebuildGame(),
                        pack.solution(),
                        Long.parseLong(args[2]),
                        10,
                        Integer.parseInt(args[3]),
                        Double.parseDouble(args[4]));
        var artifact =
                new Artifact(
                        "six-max-conditional-flop-betting-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        pack.spot().continuationModel(),
                        "VALIDATION_ONLY",
                        pack.nashConvBb(),
                        "Separate heads-up flop games conditional on a fixed six-seat preflop policy; one flop bet then mandatory turn/river checkdown. Conditional best-response gaps do not certify the source preflop policy under betting continuation, other boards, or a six-player connected equilibrium.",
                        report);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "conditional_flop_examples=%d maximum_best_response_gap_bb=%.9f source_pack_hash=%s output=%s%n",
                report.examples().size(),
                report.maximumConditionalBestResponseGapBb(),
                artifact.sourcePackHash(),
                output);
    }
}
