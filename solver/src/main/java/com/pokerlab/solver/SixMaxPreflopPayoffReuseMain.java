package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

/**
 * Bounded offline model comparison; writes a fresh standard pack and a separate provenance report.
 */
public final class SixMaxPreflopPayoffReuseMain {
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            SixMaxPreflopPayoffReuse.Provenance provenance,
            String targetPackHash,
            SixMaxPreflopCheckdownGame.TreeSummary sourceTree,
            SixMaxPreflopCheckdownGame.TreeSummary targetTree,
            SixMaxPreflopContinuationAudit.Report sourceReach,
            SixMaxPreflopContinuationAudit.Report targetReach,
            SixMaxContinuationMenuSearch.Report targetMenuSearch) {}

    private SixMaxPreflopPayoffReuseMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6 && args.length != 11)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPreflopPayoffReuseMain <source-pack.json> <target-spot.json> "
                            + "<target-pack.json> <report.json> <iterations> <generated-at> "
                            + "[--search first-flop-seed seed-count histories flops-per-history]");
        int iterations = Integer.parseInt(args[4]);
        if (iterations < 1 || iterations > 3000)
            throw new IllegalArgumentException("Require iterations in [1,3000]");
        Instant.parse(args[5]);
        SixMaxContinuationMenuSearch.Settings search = null;
        if (args.length == 11) {
            if (!args[6].equals("--search"))
                throw new IllegalArgumentException("Unknown option: " + args[6]);
            search =
                    new SixMaxContinuationMenuSearch.Settings(
                            Long.parseLong(args[7]),
                            Integer.parseInt(args[8]),
                            Integer.parseInt(args[9]),
                            Integer.parseInt(args[10]));
        }
        var paths =
                List.of(
                        Path.of(args[0]).toAbsolutePath().normalize(),
                        Path.of(args[1]).toAbsolutePath().normalize(),
                        Path.of(args[2]).toAbsolutePath().normalize(),
                        Path.of(args[3]).toAbsolutePath().normalize());
        for (int first = 0; first < paths.size(); first++)
            for (int second = first + 1; second < paths.size(); second++)
                if (sameFile(paths.get(first), paths.get(second)))
                    throw new IllegalArgumentException(
                            "Source, spot, pack and report must be distinct files");
        for (int input = 0; input < 2; input++)
            if (Files.size(paths.get(input)) > 16L * 1024 * 1024)
                throw new IllegalArgumentException("Input exceeds 16 MiB limit");
        var source = MultiwayPackJson.readFullRound(Files.readString(paths.get(0)));
        var target = MultiwayPackJson.readFullRoundSpot(Files.readString(paths.get(1)));
        var result =
                SixMaxPreflopPayoffReuse.buildExact(
                        source, target, iterations, CfrSolver.Variant.CFR_PLUS, args[5]);
        var sourceGame = source.rebuildGame();
        var targetGame = result.pack().rebuildGame();
        long seed = search == null ? 711 : search.firstFlopSeed();
        var artifact =
                new Artifact(
                        "six-max-preflop-payoff-reuse/v1",
                        MultiwaySolutionPack.VALIDATION_ONLY,
                        result.provenance(),
                        MultiwayPackJson.fullRoundContentHash(result.pack()),
                        sourceGame.treeSummary(),
                        targetGame.treeSummary(),
                        SixMaxPreflopContinuationAudit.assess(
                                sourceGame, source.solution(), seed, 20),
                        SixMaxPreflopContinuationAudit.assess(
                                targetGame, result.pack().solution(), seed, 20),
                        search == null
                                ? null
                                : SixMaxContinuationMenuSearch.search(
                                        targetGame,
                                        result.pack().solution(),
                                        search,
                                        SixMaxContinuationStudyBudget.widerFlops(),
                                        SixMaxRetainedContinuationCoverage.Settings
                                                .researchDefault()));
        String packJson = MultiwayPackJson.writeFullRound(result.pack());
        String reportJson =
                new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsString(artifact);
        // All validation, training and diagnostics finish before either output is replaced.
        atomicWrite(paths.get(2), packJson);
        atomicWrite(paths.get(3), reportJson);
        System.out.println(
                "Fresh target pack "
                        + artifact.targetPackHash()
                        + "; reused entries="
                        + result.provenance().reusedPayoffEntries()
                        + "; heads-up reach="
                        + artifact.targetReach().headsUpContinuationProbability()
                        + "; menu="
                        + (artifact.targetMenuSearch() == null
                                ? "NOT_REQUESTED"
                                : artifact.targetMenuSearch().status()));
    }

    private static boolean sameFile(Path first, Path second) throws Exception {
        return first.equals(second)
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    private static void atomicWrite(Path output, String json) throws Exception {
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".payoff-reuse-", ".json");
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
    }
}
