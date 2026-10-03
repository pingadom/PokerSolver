package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Offline audit of a strictly validated saved pack; no policy training or pack promotion. */
public final class SixMaxPolicyFlopAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String sourceContinuationModel,
            String publicationStatus,
            double sourceNashConvBb,
            String interpretation,
            SixMaxPreflopContinuationAudit.Report report) {}

    private SixMaxPolicyFlopAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPolicyFlopAuditMain <pack.json> <output.json> <flop-seed>");
        Path input = Path.of(args[0]);
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var game = pack.rebuildGame();
        var report =
                SixMaxPreflopContinuationAudit.assess(
                        game, pack.solution(), Long.parseLong(args[2]), 10);
        var artifact =
                new Artifact(
                        "six-max-policy-flop-reach-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        pack.spot().continuationModel(),
                        "VALIDATION_ONLY",
                        pack.nashConvBb(),
                        "Fixed mandatory-checkdown policy reach; exact flop checkdown baselines, not solved postflop betting or a connected equilibrium",
                        report);
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit output must not overwrite the source pack");
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "heads_up_continuation_probability=%.9f reached_histories=%d examples=%d source_pack_hash=%s output=%s%n",
                report.headsUpContinuationProbability(),
                report.reachedHeadsUpHistories(),
                report.examples().size(),
                artifact.sourcePackHash(),
                output);
    }
}
