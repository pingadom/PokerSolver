package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Source-bound offline continuation export; not a trainer pack or connected equilibrium. */
public final class SixMaxTurnRiverContinuationAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String sourceContinuationModel,
            String publicationStatus,
            double sourceNashConvBb,
            String interpretation,
            SixMaxTurnRiverContinuationAudit.Report report) {}

    private SixMaxTurnRiverContinuationAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6)
            throw new IllegalArgumentException(
                    "Usage: SixMaxTurnRiverContinuationAuditMain <pack.json> <output.json> <seed> <flop-iterations> <turn-river-iterations> <bet-pot-fraction>");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit output must not overwrite the source pack");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var report =
                SixMaxTurnRiverContinuationAudit.assess(
                        pack.rebuildGame(),
                        pack.solution(),
                        Long.parseLong(args[2]),
                        5,
                        Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]),
                        Double.parseDouble(args[5]));
        var artifact =
                new Artifact(
                        "six-max-conditional-turn-river-betting-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        pack.spot().continuationModel(),
                        "VALIDATION_ONLY",
                        pack.nashConvBb(),
                        "Separate heads-up turn/river games conditional on frozen six-seat preflop and one-bet flop policies. Exact physical river chance, one bet per street, no raises or rake. Flop policies were solved with checkdown and are not re-solved with these new values. Conditional gaps certify only the declared subgames, not a connected six-player equilibrium or wider board/private-hand support.",
                        report);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "conditional_turn_river_examples=%d maximum_best_response_gap_bb=%.9f source_pack_hash=%s output=%s%n",
                report.examples().size(),
                report.maximumConditionalBestResponseGapBb(),
                artifact.sourcePackHash(),
                output);
    }
}
