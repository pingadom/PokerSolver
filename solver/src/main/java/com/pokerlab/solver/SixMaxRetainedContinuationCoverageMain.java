package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Screens an explicitly source-bound checkpoint without changing it or serving trainer content. */
public final class SixMaxRetainedContinuationCoverageMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String sourcePackHash,
            String sourceSpotHash,
            String checkpointSolutionHash,
            SixMaxContinuationStudyBudget budget,
            List<SixMaxConnectedPreflopGame.Selection> checkpointSelections,
            SixMaxRetainedContinuationCoverage.Report sourceCoverage,
            SixMaxRetainedContinuationCoverage.Report retainedCoverage,
            SixMaxContinuationMenuSearch.Report sourceMenuSearch,
            SixMaxContinuationMenuSearch.Report retainedMenuSearch) {
        public Artifact {
            checkpointSelections = List.copyOf(checkpointSelections);
        }
    }

    private SixMaxRetainedContinuationCoverageMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3)
            throw new IllegalArgumentException(
                    "Usage: SixMaxRetainedContinuationCoverageMain <source-pack.json> <checkpoint.json> <report.json> "
                            + "[--thresholds <history-mass> <heads-up-fraction> <combo-mass> <combos>] "
                            + "[--search <first-seed> <seed-count> <histories> <flops-per-history>]");
        var settings = SixMaxRetainedContinuationCoverage.Settings.researchDefault();
        SixMaxContinuationMenuSearch.Settings search = null;
        boolean thresholdsSeen = false;
        for (int index = 3; index < args.length; index += 5) {
            if (index + 4 >= args.length)
                throw new IllegalArgumentException("Option requires four values");
            switch (args[index]) {
                case "--thresholds" -> {
                    if (thresholdsSeen)
                        throw new IllegalArgumentException("Duplicate thresholds option");
                    thresholdsSeen = true;
                    settings =
                            new SixMaxRetainedContinuationCoverage.Settings(
                                    Double.parseDouble(args[index + 1]),
                                            Double.parseDouble(args[index + 2]),
                                    Double.parseDouble(args[index + 3]),
                                            Integer.parseInt(args[index + 4]));
                }
                case "--search" -> {
                    if (search != null)
                        throw new IllegalArgumentException("Duplicate search option");
                    search =
                            new SixMaxContinuationMenuSearch.Settings(
                                    Long.parseLong(args[index + 1]),
                                    Integer.parseInt(args[index + 2]),
                                    Integer.parseInt(args[index + 3]),
                                    Integer.parseInt(args[index + 4]));
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
        }
        var sourcePath = Path.of(args[0]);
        var checkpointPath = Path.of(args[1]);
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        for (var input : List.of(sourcePath, checkpointPath))
            if (input.toAbsolutePath().normalize().equals(output)
                    || Files.exists(output) && Files.isSameFile(input, output))
                throw new IllegalArgumentException("Report cannot replace either input");
        if (Files.size(sourcePath) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source pack exceeds 16 MiB limit");
        var source = MultiwayPackJson.readFullRound(Files.readString(sourcePath));
        var loaded = SixMaxConnectedPolicyCheckpoint.read(checkpointPath, source);
        var checkpoint = loaded.snapshot();
        var game = loaded.game();
        var artifact =
                new Artifact(
                        "six-max-retained-continuation-coverage/v1",
                        "VALIDATION_ONLY",
                        checkpoint.sourcePackHash(),
                        checkpoint.sourceSpotHash(),
                        checkpoint.solutionHash(),
                        checkpoint.budget(),
                        checkpoint.selections(),
                        SixMaxRetainedContinuationCoverage.assess(
                                game, source.solution(), settings),
                        SixMaxRetainedContinuationCoverage.assess(
                                game, checkpoint.policy(), settings),
                        search == null
                                ? null
                                : SixMaxContinuationMenuSearch.search(
                                        game.source(),
                                        source.solution(),
                                        search,
                                        checkpoint.budget(),
                                        settings),
                        search == null
                                ? null
                                : SixMaxContinuationMenuSearch.search(
                                        game.source(),
                                        checkpoint.policy(),
                                        search,
                                        checkpoint.budget(),
                                        settings));
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".retained-coverage-", ".tmp");
        try {
            new ObjectMapper()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(temporary.toFile(), artifact);
            try {
                Files.move(
                        temporary,
                        output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException(
                        "Coverage filesystem must support atomic replacement", unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
