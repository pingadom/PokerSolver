package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Export an offline study summary, never a trainer pack. */
public final class SixMaxConnectedPreflopAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String sourceContinuationModel,
            String publicationStatus,
            String interpretation,
            SixMaxConnectedPreflopAudit.Report report) {}

    private SixMaxConnectedPreflopAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: SixMaxConnectedPreflopAuditMain <pack.json> <output.json> <seed> <iterations> <bet-pot-fraction>");
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        long start = System.nanoTime();
        var report =
                SixMaxConnectedPreflopAudit.assess(
                        pack.rebuildGame(),
                        pack.solution(),
                        Long.parseLong(args[2]),
                        Integer.parseInt(args[3]),
                        Double.parseDouble(args[4]));
        var artifact =
                new Artifact(
                        "six-max-connected-preflop-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        pack.spot().continuationModel(),
                        "VALIDATION_ONLY",
                        "All six preflop policies and selected heads-up flop/turn/river policies update in one CFR tree. Named flops retain physical 1/9880 chance per compatible six-hand deal; unselected flops remain mandatory checkdown. Residual payoffs assume the source exact-board table is physically consistent. Six-player unilateral deviation bounds certify only this sparse synthetic game, not realistic cash GTO. Coverage is measured, no raises or rake are added, complete policies are hashed, and no playable pack is published.",
                        report);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "joint_infosets=%d nash_conv_bb=%.9f betting_probability=%.12f elapsed_seconds=%.3f output=%s%n",
                report.jointlySolved().informationSets(),
                report.jointlySolved().quality().nashConvBb(),
                report.jointlySolved().bettingContinuationProbability(),
                (System.nanoTime() - start) / 1e9,
                output);
    }
}
