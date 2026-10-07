package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline rank-aware joint solve. Suit information and broader poker remain separate gates. */
public final class SixMaxRankTextureStudy {
    public static final String MODEL = "BOARD_RANKS_SIX_TEXTURES_ONE_BET_THEN_CHECKDOWN/v1";
    public static final String CHECKPOINT_SCHEMA = "six-max-rank-texture-checkpoint/v1";
    public static final String REPORT_SCHEMA = "six-max-rank-texture-study/v1";
    public static final int MAX_ITERATIONS = 1000;
    static final int MAX_CHECKPOINT_BYTES = 64 * 1024 * 1024;

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
            List<SixMaxRankTextureFlopGame.Selection> selections,
            long completeTreeStates,
            String solutionHash,
            CfrSolution solution) {
        public Checkpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !MODEL.equals(model)
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || !"EXHAUSTIVE".equals(chanceTraversal))
                throw new IllegalArgumentException("Unsupported rank/texture checkpoint identity");
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
            Objects.requireNonNull(solution, "solution");
            if (selections.isEmpty()
                    || selections.size() > SixMaxRankTextureFlopGame.MAX_SELECTED_HISTORIES
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES
                    || solution.iterations() > MAX_ITERATIONS)
                throw new IllegalArgumentException("Rank/texture checkpoint cap exceeded");
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
            String qualityScope,
            String sourcePackHash,
            String sourceSpotHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int sourceIterations,
            int iterations,
            int publicSignals,
            int preflopInformationSets,
            int postflopInformationSets,
            long completeTreeStates,
            List<SixMaxRankTextureFlopGame.Selection> selections,
            List<SixMaxRankTextureFlopGame.Coverage> coverage,
            SixMaxConnectedPreflopAudit.Quality checkdownBaselineInRankTextureGame,
            SixMaxConnectedPreflopAudit.Quality jointlySolvedInRankTextureGame,
            List<Double> checkdownRecoveryErrorBb,
            double maximumPreflopActionFrequencyChange,
            Reach sourceReach,
            Reach jointlySolvedReach,
            SixMaxMaterialContinuationFeasibility.Report sourcePhysicalFlopFeasibility,
            SixMaxMaterialContinuationFeasibility.Report jointlySolvedPhysicalFlopFeasibility) {
        public Report {
            selections = List.copyOf(selections);
            coverage = List.copyOf(coverage);
            checkdownRecoveryErrorBb = List.copyOf(checkdownRecoveryErrorBb);
        }
    }

    public record Result(
            Checkpoint checkpoint,
            Report report,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    private SixMaxRankTextureStudy() {}

    public static List<SixMaxRankTextureFlopGame.Selection> select(
            SixMaxPreflopSolutionPack source, List<Integer> ranks, double fraction) {
        return SixMaxTextureStudy.select(source, ranks, fraction).stream()
                .map(s -> new SixMaxRankTextureFlopGame.Selection(s.history(), s.potFraction()))
                .toList();
    }

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            List<SixMaxRankTextureFlopGame.Selection> selections,
            int iterations,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        if (iterations < 1 || iterations > MAX_ITERATIONS)
            throw new IllegalArgumentException("Study requires 1–1000 iterations");
        var game = new SixMaxRankTextureFlopGame(source, table, selections);
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, pruning);
        var checkpoint = checkpoint(source, table, game, solver.solve(iterations), pruning);
        return new Result(
                checkpoint,
                assess(source, table, checkpoint),
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes());
    }

    static Checkpoint checkpoint(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureFlopGame game,
            CfrSolution policy,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        Objects.requireNonNull(pruning, "pruning");
        String sourceHash = MultiwayPackJson.fullRoundContentHash(source),
                tableHash = SixMaxRankTexturePayoffTable.hash(table);
        return new Checkpoint(
                CHECKPOINT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                pruning == MultiPlayerCfrSolver.InactivePruning.NONE
                        ? "CFR_PLUS"
                        : SixMaxTextureStudy.PRUNED_ALGORITHM,
                "EXHAUSTIVE",
                sourceHash,
                source.spotHash(),
                tableHash,
                gameHash(sourceHash, source.spotHash(), tableHash, game.selections()),
                game.selections(),
                game.completeTreeStates(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                policy);
    }

    static String gameHash(
            String sourceHash,
            String spotHash,
            String tableHash,
            List<SixMaxRankTextureFlopGame.Selection> selections)
            throws Exception {
        var identity =
                java.util.Map.of(
                        "model",
                        MODEL,
                        "sourcePackHash",
                        sourceHash,
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

    public static SixMaxRankTextureFlopGame rebuild(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            Checkpoint checkpoint)
            throws Exception {
        if (!checkpoint.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !checkpoint.sourceSpotHash().equals(source.spotHash())
                || !checkpoint.payoffTableHash().equals(SixMaxRankTexturePayoffTable.hash(table))
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
                    "Checkpoint source, model, table or policy identity differs");
        var game = new SixMaxRankTextureFlopGame(source, table, checkpoint.selections());
        var completed =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, checkpoint.solution(), SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES);
        if (completed.addedInformationSets() != 0
                || completed.visitedStates() != checkpoint.completeTreeStates()
                || game.completeTreeStates() != checkpoint.completeTreeStates())
            throw new IllegalArgumentException("Checkpoint requires the complete declared policy");
        return game;
    }

    public static Checkpoint read(
            Path input,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table)
            throws Exception {
        var cp =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(input, MAX_CHECKPOINT_BYTES),
                                Checkpoint.class);
        rebuild(source, table, cp);
        return cp;
    }

    public static void write(
            Path output,
            Checkpoint cp,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table)
            throws Exception {
        rebuild(source, table, cp);
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxTextureStudy.json(cp).getBytes(StandardCharsets.UTF_8),
                MAX_CHECKPOINT_BYTES);
    }

    /**
     * Read-only parent-game best responses and physical-board feasibility; no local rank-bucket
     * certificate.
     */
    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            Checkpoint cp)
            throws Exception {
        var game = rebuild(source, table, cp);
        var baseline = game.checkdownBaseline(source.solution());
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, baseline));
        var after =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, cp.solution()));
        double[] original =
                MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution());
        var errors = new ArrayList<Double>();
        for (int player = 0; player < 6; player++) {
            double error = before.profileUtilitiesBb().get(player) - original[player];
            if (Math.abs(error) > 1e-9)
                throw new IllegalStateException(
                        "Rank/texture checkdown must recover source utilities");
            errors.add(error);
        }
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        if (!pre.strategy().keySet().equals(source.solution().strategy().keySet()))
            throw new IllegalStateException("Preflop information support changed");
        double maximum = 0;
        for (var row : pre.strategy().entrySet())
            for (var action : row.getValue().entrySet())
                maximum =
                        Math.max(
                                maximum,
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
                SixMaxMaterialContinuationFeasibility.assess(game.sourceGame(), pre, settings);
        return new Report(
                REPORT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                "PARENT_INFORMATION_SET_BEST_RESPONSES_ONLY",
                cp.sourcePackHash(),
                cp.sourceSpotHash(),
                cp.payoffTableHash(),
                cp.gameHash(),
                cp.solutionHash(),
                cp.algorithm(),
                cp.chanceTraversal(),
                source.solution().iterations(),
                cp.solution().iterations(),
                table.signals().size(),
                pre.strategy().size(),
                cp.solution().strategy().size() - pre.strategy().size(),
                game.completeTreeStates(),
                game.selections(),
                game.coverage(),
                before,
                after,
                errors,
                maximum,
                reach(game, source.solution(), sourceContent.headsUpProbability()),
                reach(game, pre, candidateContent.headsUpProbability()),
                sourceContent,
                candidateContent);
    }

    private static Reach reach(
            SixMaxRankTextureFlopGame game, CfrSolution preflop, double headsUp) {
        double mass = 0;
        for (var selection : game.selections())
            if (SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), preflop, selection.history()))
                mass +=
                        new SixMaxPolicyFlopTransition(
                                        game.sourceGame(), preflop, selection.history())
                                .reachProbability();
        return new Reach(mass, headsUp, headsUp > 0 ? mass / headsUp : 0);
    }
}
