package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline joint preflop/texture experiment. No trainer admission or full-poker quality claim. */
public final class SixMaxTextureStudy {
    public static final String MODEL = "SIX_PUBLIC_FLOP_TEXTURES_ONE_BET_THEN_CHECKDOWN/v1";
    public static final String CHECKPOINT_SCHEMA = "six-max-texture-checkpoint/v1";
    public static final String REPORT_SCHEMA = "six-max-texture-study/v1";
    public static final String PRUNED_ALGORITHM = "CFR_PLUS_FIXED_UTILITY_PRUNING";

    public record Checkpoint(
            String schemaVersion,
            String publicationStatus,
            String model,
            String algorithm,
            String chanceTraversal,
            String sourcePackHash,
            String sourceSpotHash,
            String payoffTableHash,
            String gameHash,
            List<SixMaxTextureFlopGame.Selection> selections,
            long completeTreeStates,
            String solutionHash,
            CfrSolution solution) {
        public Checkpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !MODEL.equals(model)
                    || !("CFR_PLUS".equals(algorithm) || PRUNED_ALGORITHM.equals(algorithm))
                    || !"EXHAUSTIVE".equals(chanceTraversal))
                throw new IllegalArgumentException("Unsupported texture checkpoint identity");
            for (String hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            payoffTableHash,
                            gameHash,
                            solutionHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid checkpoint hash");
            selections = List.copyOf(selections);
            if (selections.isEmpty()
                    || selections.size() > SixMaxTextureFlopGame.MAX_SELECTED_HISTORIES
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxTextureFlopGame.MAX_COMPLETE_STATES)
                throw new IllegalArgumentException("Invalid texture checkpoint budget");
            Objects.requireNonNull(solution, "solution");
            if (solution.iterations() > 3000)
                throw new IllegalArgumentException("Checkpoint iteration cap exceeded");
        }
    }

    public record Reach(
            double selectedHistoryProbability,
            double headsUpProbability,
            double selectedHeadsUpFraction) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String model,
            String sourcePackHash,
            String sourceSpotHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int sourceIterations,
            int iterations,
            int preflopInformationSets,
            int postflopInformationSets,
            long completeTreeStates,
            List<SixMaxTextureFlopGame.Selection> selections,
            List<SixMaxTextureFlopGame.Coverage> coverage,
            SixMaxConnectedPreflopAudit.Quality checkdownBaselineInTextureGame,
            SixMaxConnectedPreflopAudit.Quality jointlySolvedInTextureGame,
            List<Double> checkdownRecoveryErrorBb,
            double maximumPreflopActionFrequencyChange,
            Reach sourceReach,
            Reach jointlySolvedReach,
            List<SixMaxTextureConditionalAudit.Conditional> jointlySolvedConditionalTextures,
            SixMaxMaterialContinuationFeasibility.Report sourcePhysicalFlopFeasibility,
            SixMaxMaterialContinuationFeasibility.Report jointlySolvedPhysicalFlopFeasibility) {
        public Report {
            selections = List.copyOf(selections);
            coverage = List.copyOf(coverage);
            checkdownRecoveryErrorBb = List.copyOf(checkdownRecoveryErrorBb);
            jointlySolvedConditionalTextures = List.copyOf(jointlySolvedConditionalTextures);
        }
    }

    public record Result(
            Checkpoint checkpoint,
            Report report,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    private SixMaxTextureStudy() {}

    /** Ranks refer to the ORIGINAL source policy; retraining never silently changes the menu. */
    public static List<SixMaxTextureFlopGame.Selection> select(
            SixMaxPreflopSolutionPack source, List<Integer> ranks, double betFraction) {
        if (ranks.isEmpty()
                || ranks.size() > SixMaxTextureFlopGame.MAX_SELECTED_HISTORIES
                || ranks.stream().distinct().count() != ranks.size()
                || ranks.stream().anyMatch(rank -> rank < 1 || rank > 20))
            throw new IllegalArgumentException("Select one to six distinct source ranks in 1–20");
        // Validate sizing before the all-flop preflight.
        new SixMaxTextureFlopGame.Selection(List.of(), betFraction);
        var feasibility =
                SixMaxMaterialContinuationFeasibility.assess(
                        source.rebuildGame(),
                        source.solution(),
                        SixMaxMaterialContinuationFeasibility.Settings.researchDefault());
        return ranks.stream()
                .map(
                        rank ->
                                new SixMaxTextureFlopGame.Selection(
                                        feasibility.histories().stream()
                                                .filter(h -> h.reachRank() == rank)
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalArgumentException(
                                                                        "Source history rank does not exist: "
                                                                                + rank))
                                                .history(),
                                        betFraction))
                .toList();
    }

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            List<SixMaxTextureFlopGame.Selection> selections,
            int iterations)
            throws Exception {
        return solve(
                source, table, selections, iterations, MultiPlayerCfrSolver.InactivePruning.NONE);
    }

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            List<SixMaxTextureFlopGame.Selection> selections,
            int iterations,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        if (iterations < 1 || iterations > 3000)
            throw new IllegalArgumentException("Study requires 1–3000 iterations");
        var game = new SixMaxTextureFlopGame(source, table, selections);
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, pruning);
        var policy = solver.solve(iterations);
        var checkpoint = checkpoint(source, table, game, policy, pruning);
        return new Result(
                checkpoint,
                assess(source, table, checkpoint),
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes());
    }

    static Checkpoint checkpoint(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            SixMaxTextureFlopGame game,
            CfrSolution policy)
            throws Exception {
        return checkpoint(source, table, game, policy, MultiPlayerCfrSolver.InactivePruning.NONE);
    }

    static Checkpoint checkpoint(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            SixMaxTextureFlopGame game,
            CfrSolution policy,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        Objects.requireNonNull(pruning, "pruning");
        return new Checkpoint(
                CHECKPOINT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                pruning == MultiPlayerCfrSolver.InactivePruning.NONE
                        ? "CFR_PLUS"
                        : PRUNED_ALGORITHM,
                "EXHAUSTIVE",
                MultiwayPackJson.fullRoundContentHash(source),
                source.spotHash(),
                SixMaxTexturePayoffTable.hash(table),
                gameHash(
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        SixMaxTexturePayoffTable.hash(table),
                        game.selections()),
                game.selections(),
                game.completeTreeStates(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                policy);
    }

    /** Complete policy and exact source/model binding are required; no unseen row is filled. */
    public static SixMaxTextureFlopGame rebuild(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            Checkpoint checkpoint)
            throws Exception {
        if (!checkpoint.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !checkpoint.sourceSpotHash().equals(source.spotHash())
                || !checkpoint.payoffTableHash().equals(SixMaxTexturePayoffTable.hash(table))
                || !checkpoint
                        .gameHash()
                        .equals(
                                gameHash(
                                        checkpoint.sourcePackHash(),
                                        checkpoint.sourceSpotHash(),
                                        checkpoint.payoffTableHash(),
                                        checkpoint.selections()))
                || !checkpoint
                        .solutionHash()
                        .equals(SixMaxConnectedPostflopAudit.solutionHash(checkpoint.solution())))
            throw new IllegalArgumentException(
                    "Checkpoint source, payoff table or policy hash differs");
        var game = new SixMaxTextureFlopGame(source, table, checkpoint.selections());
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, checkpoint.solution(), SixMaxTextureFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || complete.visitedStates() != checkpoint.completeTreeStates()
                || game.completeTreeStates() != checkpoint.completeTreeStates())
            throw new IllegalArgumentException(
                    "Checkpoint must contain the complete declared game policy");
        return game;
    }

    static String gameHash(
            String packHash,
            String spotHash,
            String tableHash,
            List<SixMaxTextureFlopGame.Selection> selections)
            throws Exception {
        var identity =
                java.util.Map.of(
                        "model",
                        MODEL,
                        "sourcePackHash",
                        packHash,
                        "sourceSpotHash",
                        spotHash,
                        "payoffTableHash",
                        tableHash,
                        "selections",
                        selections);
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(identity)));
    }

    public static Checkpoint read(
            Path input, SixMaxPreflopSolutionPack source, SixMaxTexturePayoffTable.Artifact table)
            throws Exception {
        if (Files.size(input) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Checkpoint exceeds 16 MiB");
        byte[] bytes;
        if (input.toString().endsWith(".gz")) {
            // Own the raw stream even if gzip header validation fails in the constructor.
            try (var raw = Files.newInputStream(input);
                    var stream = new java.util.zip.GZIPInputStream(raw)) {
                bytes = stream.readNBytes(16 * 1024 * 1024 + 1);
            }
            if (bytes.length > 16 * 1024 * 1024)
                throw new IllegalArgumentException("Expanded checkpoint exceeds 16 MiB");
        } else bytes = Files.readAllBytes(input);
        var checkpoint = SixMaxTexturePayoffTable.mapper().readValue(bytes, Checkpoint.class);
        rebuild(source, table, checkpoint);
        return checkpoint;
    }

    /** Optional gzip transport has the same canonical JSON/policy identity and bounded reload. */
    public static void write(
            Path output,
            Checkpoint checkpoint,
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table)
            throws Exception {
        rebuild(source, table, checkpoint);
        byte[] bytes = json(checkpoint).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Checkpoint exceeds 16 MiB");
        if (output.toString().endsWith(".gz")) {
            var compressed = new java.io.ByteArrayOutputStream();
            try (var stream = new java.util.zip.GZIPOutputStream(compressed)) {
                stream.write(bytes);
            }
            bytes = compressed.toByteArray();
        }
        SixMaxTexturePayoffTableMain.atomicWrite(output.toAbsolutePath().normalize(), bytes);
    }

    /** Read-only replay: same report as at export, without running CFR or payoff generation. */
    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxTexturePayoffTable.Artifact table,
            Checkpoint checkpoint)
            throws Exception {
        var game = rebuild(source, table, checkpoint);
        var baseline = game.checkdownBaseline(source.solution());
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, baseline));
        var after =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, checkpoint.solution()));
        var originalUtilities =
                MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution());
        var errors = new ArrayList<Double>();
        for (int seat = 0; seat < 6; seat++) {
            double error = before.profileUtilitiesBb().get(seat) - originalUtilities[seat];
            if (Math.abs(error) > 1e-9)
                throw new IllegalStateException(
                        "Texture integration does not recover source checkdown");
            errors.add(error);
        }
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(checkpoint.solution());
        double maxChange = 0;
        if (!preflop.strategy().keySet().equals(source.solution().strategy().keySet()))
            throw new IllegalStateException("Joint solve changed preflop information support");
        for (var row : preflop.strategy().entrySet())
            for (var action : row.getValue().entrySet())
                maxChange =
                        Math.max(
                                maxChange,
                                Math.abs(
                                        action.getValue()
                                                - source.solution()
                                                        .strategy()
                                                        .get(row.getKey())
                                                        .get(action.getKey())));
        var settings = SixMaxMaterialContinuationFeasibility.Settings.researchDefault();
        var sourceContent =
                SixMaxMaterialContinuationFeasibility.assess(
                        game.sourceGame(), source.solution(), settings);
        var candidateContent =
                SixMaxMaterialContinuationFeasibility.assess(game.sourceGame(), preflop, settings);
        return new Report(
                REPORT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                checkpoint.sourcePackHash(),
                checkpoint.sourceSpotHash(),
                checkpoint.payoffTableHash(),
                checkpoint.gameHash(),
                checkpoint.solutionHash(),
                checkpoint.algorithm(),
                checkpoint.chanceTraversal(),
                source.solution().iterations(),
                checkpoint.solution().iterations(),
                preflop.strategy().size(),
                checkpoint.solution().strategy().size() - preflop.strategy().size(),
                game.completeTreeStates(),
                game.selections(),
                game.coverage(),
                before,
                after,
                errors,
                maxChange,
                reach(game, source.solution(), sourceContent.headsUpProbability()),
                reach(game, preflop, candidateContent.headsUpProbability()),
                SixMaxTextureConditionalAudit.assess(game, checkpoint.solution()),
                sourceContent,
                candidateContent);
    }

    private static Reach reach(SixMaxTextureFlopGame game, CfrSolution preflop, double headsUp) {
        double mass = 0;
        for (var selection : game.selections()) {
            // An exact zero-action prefix contributes no reach; the game still retains it for BRs.
            if (SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), preflop, selection.history()))
                mass +=
                        new SixMaxPolicyFlopTransition(
                                        game.sourceGame(), preflop, selection.history())
                                .reachProbability();
        }
        return new Reach(mass, headsUp, headsUp > 0 ? mass / headsUp : 0);
    }

    public static String json(Object value) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(value)
                        .replace("\r\n", "\n")
                + "\n";
    }
}
