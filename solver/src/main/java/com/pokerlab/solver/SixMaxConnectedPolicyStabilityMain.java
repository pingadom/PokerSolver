package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Reads two source/menu-bound checkpoints and compares them without training or changing inputs.
 */
public final class SixMaxConnectedPolicyStabilityMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String sourcePackHash,
            String sourceSpotHash,
            SixMaxContinuationStudyBudget budget,
            List<SixMaxConnectedPreflopGame.Selection> selections,
            SixMaxConnectedPolicyStability.Report comparison,
            SixMaxAlternatingContinuationSolver.Audit firstQuality,
            SixMaxAlternatingContinuationSolver.Audit secondQuality,
            SixMaxRetainedContinuationCoverage.Report firstContent,
            SixMaxRetainedContinuationCoverage.Report secondContent) {}

    private SixMaxConnectedPolicyStabilityMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxConnectedPolicyStabilityMain <source-pack.json> <first-checkpoint.json> <second-checkpoint.json> <report.json>");
        var paths =
                java.util.Arrays.stream(args)
                        .map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize())
                        .toList();
        var output = paths.get(3);
        for (var input : paths.subList(0, 3))
            if (input.equals(output)
                    || (Files.exists(input)
                            && Files.exists(output)
                            && Files.isSameFile(input, output)))
                throw new IllegalArgumentException("Report must not alias an input");
        if (Files.size(paths.getFirst()) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source exceeds 16 MiB limit");
        var source = MultiwayPackJson.readFullRound(Files.readString(paths.getFirst()));
        var first = SixMaxConnectedPolicyCheckpoint.read(paths.get(1), source);
        // Keep only the second snapshot after validation; all audits share the first game.
        var second = SixMaxConnectedPolicyCheckpoint.read(paths.get(2), source).snapshot();
        if (!first.snapshot().selections().equals(second.selections())
                || !first.snapshot().budget().equals(second.budget()))
            throw new IllegalArgumentException(
                    "Stability checkpoints must use the same continuation menu and budget");
        var game = first.game();
        var budget = first.snapshot().budget();
        var firstPolicy = first.snapshot().policy();
        var secondPolicy = second.policy();
        var artifact =
                new Artifact(
                        "six-max-connected-policy-stability/v1",
                        "VALIDATION_ONLY",
                        first.snapshot().sourcePackHash(),
                        source.spotHash(),
                        budget,
                        game.selections(),
                        SixMaxConnectedPolicyStability.assess(
                                game, firstPolicy, secondPolicy, budget),
                        SixMaxAlternatingContinuationSolver.audit(game, firstPolicy, budget),
                        SixMaxAlternatingContinuationSolver.audit(game, secondPolicy, budget),
                        SixMaxRetainedContinuationCoverage.assess(
                                game,
                                firstPolicy,
                                SixMaxRetainedContinuationCoverage.Settings.researchDefault()),
                        SixMaxRetainedContinuationCoverage.assess(
                                game,
                                secondPolicy,
                                SixMaxRetainedContinuationCoverage.Settings.researchDefault()));
        String json =
                new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsString(artifact);
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".policy-stability-", ".json");
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
        System.out.println("Wrote same-game policy comparison " + output);
    }
}
