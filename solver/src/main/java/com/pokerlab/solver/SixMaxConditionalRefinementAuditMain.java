package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pokerlab.core.card.Card;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Reproduce conditional refinement with unchanged declared coverage and parent-game auditing. */
public final class SixMaxConditionalRefinementAuditMain {
    public record Run(
            long trainingSeed,
            int jointTrainingIterations,
            String jointAlgorithm,
            String jointChanceTraversal,
            int visitedInformationSets,
            int uniformlyCompletedInformationSets,
            MultiPlayerCfrSolver.Statistics jointTraversal,
            SixMaxConditionalPostflopRefinement.Report refinement) {}

    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String coverageArtifactHash,
            String publicationStatus,
            String interpretation,
            List<SixMaxConnectedPreflopGame.Coverage> coverage,
            List<Run> runs) {
        public Artifact {
            coverage = List.copyOf(coverage);
            runs = List.copyOf(runs);
        }
    }

    private SixMaxConditionalRefinementAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6)
            throw new IllegalArgumentException(
                    "Usage: SixMaxConditionalRefinementAuditMain <pack.json> <coverage-audit.json> <output.json> <training-seeds-csv> <joint-iterations> <refinement-iterations>");
        var input = Path.of(args[0]);
        var coverageInput = Path.of(args[1]);
        var output = Path.of(args[2]).toAbsolutePath();
        for (var source : List.of(input, coverageInput)) {
            if (Files.exists(output) && Files.isSameFile(source, output))
                throw new IllegalArgumentException("Audit must not overwrite its inputs");
            if (Files.size(source) > 16 * 1024 * 1024)
                throw new IllegalArgumentException("Input exceeds 16 MiB limit");
        }
        var seeds = Arrays.stream(args[3].split(",", -1)).map(Long::parseLong).toList();
        int jointIterations = Integer.parseInt(args[4]);
        int refinementIterations = Integer.parseInt(args[5]);
        if (seeds.isEmpty()
                || seeds.size() > 3
                || seeds.stream().distinct().count() != seeds.size()
                || jointIterations < 1
                || jointIterations > 3000
                || refinementIterations < 1
                || refinementIterations > 500)
            throw new IllegalArgumentException(
                    "Select 1–3 distinct seeds, 1–3000 joint and 1–500 refinement iterations");
        var mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var coverageArtifact =
                mapper.readValue(
                        coverageInput.toFile(), SixMaxFlopCoverageAuditMain.Artifact.class);
        String sourceHash = MultiwayPackJson.fullRoundContentHash(pack);
        if (!coverageArtifact.schemaVersion().equals("six-max-flop-coverage-audit/v1")
                || !coverageArtifact.publicationStatus().equals("VALIDATION_ONLY")
                || !coverageArtifact.sourcePackHash().equals(sourceHash)
                || !coverageArtifact.sourceSpotHash().equals(pack.spotHash()))
            throw new IllegalArgumentException("Coverage artifact does not match the source pack");
        var declared = coverageArtifact.report().cases().getLast().coverage();
        var selections =
                declared.stream()
                        .map(
                                c ->
                                        new SixMaxConnectedPreflopGame.Selection(
                                                c.actions(),
                                                c.flops().stream()
                                                        .map(
                                                                b ->
                                                                        b.stream()
                                                                                .map(Card::parse)
                                                                                .toList())
                                                        .toList(),
                                                c.flopBetBb(),
                                                c.requestedTurnBetBb(),
                                                c.requestedRiverBetBb()))
                        .toList();
        var game = new SixMaxConnectedPreflopGame(pack.rebuildGame(), selections);
        if (!game.coverage().equals(declared))
            throw new IllegalArgumentException(
                    "Declared coverage disagrees with the rebuilt physical game");
        if (game.coverage().stream()
                        .flatMap(c -> c.legalSelectedFlopsByDeal().stream())
                        .mapToInt(Integer::intValue)
                        .sum()
                > 8)
            throw new IllegalArgumentException(
                    "Audit supports at most eight compatible deal/flop pairs");
        var runs = new ArrayList<Run>();
        for (long seed : seeds) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.VANILLA,
                            MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS,
                            seed,
                            0,
                            true,
                            true);
            var sampled = solver.solve(jointIterations);
            var complete = MultiPlayerStrategyCompletion.uniformAtUnseen(game, sampled, 2_000_000);
            var result =
                    SixMaxConditionalPostflopRefinement.refine(
                            game,
                            complete.solution(),
                            refinementIterations,
                            branch ->
                                    System.out.printf(
                                            Locale.ROOT,
                                            "seed=%d flop=%s status=%s before_gap_bb=%s after_gap_bb=%s%n",
                                            seed,
                                            branch.flop(),
                                            branch.status(),
                                            branch.before() == null ? "n/a" : branch.before().gap(),
                                            branch.after() == null ? "n/a" : branch.after().gap()));
            runs.add(
                    new Run(
                            seed,
                            jointIterations,
                            "LINEAR_CFR",
                            "SAMPLED_RUNOUTS",
                            sampled.strategy().size(),
                            complete.addedInformationSets(),
                            solver.statistics(),
                            result.report()));
            System.out.printf(
                    Locale.ROOT,
                    "seed=%d parent_before_bb=%.9f parent_after_bb=%.9f%n",
                    seed,
                    result.report().originalQuality().nashConvBb(),
                    result.report().candidateQuality().nashConvBb());
        }
        String coverageHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(mapper.writeValueAsBytes(coverageArtifact)));
        var artifact =
                new Artifact(
                        "six-max-conditional-refinement-audit/v1",
                        sourceHash,
                        pack.spotHash(),
                        coverageHash,
                        "VALIDATION_ONLY",
                        "Exact physical heads-up CFR+ refines selected continuations under the joint policy's frozen preflop posterior. Preflop and unsupported rows are preserved. Exact six-player best responses recheck the entire candidate parent profile; conditional improvement is not a safe subgame replacement theorem, a full cash-poker certificate, or trainer publication. Original and refinement iteration budgets remain separate.",
                        game.coverage(),
                        runs);
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), artifact);
    }
}
