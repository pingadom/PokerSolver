package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Reproducible exact-versus-restricted connected solve study; no trainer publication. */
public final class SixMaxConnectedPostflopAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String sourceContinuationModel,
            String publicationStatus,
            double sourceNashConvBb,
            String interpretation,
            SixMaxConnectedPostflopAudit.Report report) {}

    private SixMaxConnectedPostflopAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7)
            throw new IllegalArgumentException(
                    "Usage: SixMaxConnectedPostflopAuditMain <pack.json> <output.json> <seed> <exact-iterations> <restricted-iterations> <bet-pot-fraction> <turn-quantiles-csv>");
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var quantiles = Arrays.stream(args[6].split(",", -1)).map(Double::parseDouble).toList();
        long start = System.nanoTime();
        var report =
                SixMaxConnectedPostflopAudit.assess(
                        pack.rebuildGame(),
                        pack.solution(),
                        Long.parseLong(args[2]),
                        2,
                        Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]),
                        Double.parseDouble(args[5]),
                        quantiles);
        var artifact =
                new Artifact(
                        "six-max-connected-postflop-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        pack.spot().continuationModel(),
                        "VALIDATION_ONLY",
                        pack.nashConvBb(),
                        "Each heads-up flop, turn and river policy is re-solved together in a single one-bet-per-street tree retaining all six private hands. Exact and restricted-turn best-response gaps certify only their own declared games. Restricted checkdown bias diagnoses a fixed policy, not strategic abstraction error. Preflop remains frozen with mandatory checkdown; these selected flops, tiny private supports, no raises and no rake do not certify a connected six-player cash equilibrium. Complete solutions are hashed; this summary is not a playable strategy pack.",
                        report);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "connected_examples=%d exact_gap_bb=%.9f restricted_gap_bb=%.9f max_checkdown_bias_bb=%.6f elapsed_seconds=%.3f output=%s%n",
                report.examples().size(),
                report.maximumExactGapBb(),
                report.maximumRestrictedGapBb(),
                report.maximumAbsoluteRestrictedCheckdownBiasBb(),
                (System.nanoTime() - start) / 1e9,
                output);
    }
}
