package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public final class SixMaxFlopCoverageAuditMain {
    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String publicationStatus,
            String interpretation,
            SixMaxFlopCoverageAudit.Report report) {}

    private SixMaxFlopCoverageAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6)
            throw new IllegalArgumentException(
                    "Usage: SixMaxFlopCoverageAuditMain <pack.json> <output.json> <training-seeds-csv> <iterations> <maximum-flops> <proposal-mixtures-csv>");
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Audit must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var seeds = Arrays.stream(args[2].split(",", -1)).map(Long::parseLong).toList();
        var mixtures = Arrays.stream(args[5].split(",", -1)).map(Double::parseDouble).toList();
        long start = System.nanoTime();
        var report =
                SixMaxFlopCoverageAudit.assess(
                        pack.rebuildGame(),
                        pack.solution(),
                        711,
                        seeds,
                        Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]),
                        mixtures,
                        (width, run) ->
                                System.out.printf(
                                        Locale.ROOT,
                                        "flops=%d seed=%d mixture=%.2f centered=%s nash_conv_bb=%.6f visited_infosets=%d missing_infosets=%d traversal_reduction=%.2fx%n",
                                        width,
                                        run.seed(),
                                        run.uniformProposalMixture(),
                                        run.checkdownControlVariate(),
                                        run.quality().nashConvBb(),
                                        run.visitedInformationSets(),
                                        run.uniformlyCompletedInformationSets(),
                                        run.traversalReductionFactor()));
        var artifact =
                new Artifact(
                        "six-max-flop-coverage-audit/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        "VALIDATION_ONLY",
                        "Sampled vanilla CFR updates all six preflop and selected heads-up postflop policies. Proposal probabilities only guide traversal; likelihood ratios retain the declared physical game. Missing information sets receive explicit uniform completion solely for bounded exact-game audits. Full-game and conditional gaps include that completion; traversal savings do not imply decision quality, full-deck flop coverage, or six-player equilibrium convergence. No trainer pack is published.",
                        report);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
        System.out.printf(
                Locale.ROOT,
                "coverage_cases=%d runs=%d elapsed_seconds=%.3f output=%s%n",
                report.cases().size(),
                report.cases().stream().mapToInt(c -> c.runs().size()).sum(),
                (System.nanoTime() - start) / 1e9,
                output);
    }
}
