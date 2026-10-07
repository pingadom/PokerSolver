package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxRankTextureFlopGame.Selection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fresh joint solving in a declared partial suit model; prior policies are audit references only.
 */
public final class SixMaxSuitRefinementStudy {
    public static final String MODEL = "DECLARED_PHYSICAL_FLOPS_OTHERWISE_RANK_TEXTURE_ONE_BET/v1";
    public static final String CHECKPOINT_SCHEMA = "six-max-suit-refinement-checkpoint/v1";
    public static final String REPORT_SCHEMA = "six-max-suit-refinement-study/v1";
    public static final String QUALITY_SCOPE =
            "PARENT_AND_REACHED_PUBLIC_OBSERVATION_BEST_RESPONSES";
    public static final int MAX_ITERATIONS = 1000;
    static final int MAX_CHECKPOINT_BYTES = 64 * 1024 * 1024, MAX_REPORT_BYTES = 32 * 1024 * 1024;

    public record Checkpoint(
            String schemaVersion,
            String publicationStatus,
            String model,
            String algorithm,
            String chanceTraversal,
            String sourcePackHash,
            String sourceSpotHash,
            String parentRankTableHash,
            String payoffTableHash,
            String gameHash,
            List<Selection> selections,
            long completeTreeStates,
            String solutionHash,
            CfrSolution solution) {
        public Checkpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !MODEL.equals(model)
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm)))
                throw new IllegalArgumentException("Unsupported suit checkpoint identity");
            for (String hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            parentRankTableHash,
                            payoffTableHash,
                            gameHash,
                            solutionHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid suit checkpoint hash");
            selections = List.copyOf(selections);
            if (selections.isEmpty()
                    || selections.size() > SixMaxRankTextureFlopGame.MAX_SELECTED_HISTORIES
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES
                    || solution.iterations() > MAX_ITERATIONS)
                throw new IllegalArgumentException("Suit checkpoint cap exceeded");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String model,
            String qualityScope,
            double descriptiveGapThresholdBb,
            double descriptiveMarginalThreshold,
            String sourcePackHash,
            String sourceSpotHash,
            String parentRankTableHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int iterations,
            List<SixMaxRankTexturePayoffTable.Signal> refinedSignals,
            int observations,
            int physicalObservations,
            int preflopInformationSets,
            int postflopInformationSets,
            long completeTreeStates,
            List<Selection> selections,
            List<SixMaxRankTextureFlopGame.Coverage> coverage,
            SixMaxConnectedPreflopAudit.Quality checkdownBaseline,
            List<Double> checkdownRecoveryErrorBb,
            double maximumPreflopActionFrequencyChange,
            SixMaxFlopConditionalDiagnostics.Result jointlySolvedDiagnostics,
            SixMaxMaterialContinuationFeasibility.Report physicalFlopFeasibility) {
        public Report {
            refinedSignals = List.copyOf(refinedSignals);
            selections = List.copyOf(selections);
            coverage = List.copyOf(coverage);
            checkdownRecoveryErrorBb = List.copyOf(checkdownRecoveryErrorBb);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !MODEL.equals(model)
                    || !QUALITY_SCOPE.equals(qualityScope)
                    || descriptiveGapThresholdBb
                            != SixMaxRankTextureConditionalAudit.GAP_THRESHOLD_BB
                    || descriptiveMarginalThreshold
                            != SixMaxRankTextureConditionalAudit.MARGINAL_THRESHOLD
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || iterations < 1
                    || iterations > MAX_ITERATIONS
                    || observations < 1
                    || observations > SixMaxSuitRefinementPayoffTable.MAX_OBSERVATIONS
                    || physicalObservations < 1
                    || physicalObservations > observations
                    || preflopInformationSets < 1
                    || postflopInformationSets < 1
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES
                    || selections.isEmpty()
                    || selections.size() > SixMaxRankTextureFlopGame.MAX_SELECTED_HISTORIES
                    || checkdownRecoveryErrorBb.size() != 6
                    || checkdownRecoveryErrorBb.stream().anyMatch(e -> !Double.isFinite(e))
                    || !Double.isFinite(maximumPreflopActionFrequencyChange)
                    || maximumPreflopActionFrequencyChange < 0
                    || maximumPreflopActionFrequencyChange > 1)
                throw new IllegalArgumentException("Unsupported suit study report");
            for (String hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            parentRankTableHash,
                            payoffTableHash,
                            gameHash,
                            solutionHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid suit report hash");
            refinedSignals = SixMaxSuitRefinementPayoffTable.refinement(refinedSignals);
            java.util.Objects.requireNonNull(checkdownBaseline, "checkdownBaseline");
            java.util.Objects.requireNonNull(jointlySolvedDiagnostics, "jointlySolvedDiagnostics");
            java.util.Objects.requireNonNull(physicalFlopFeasibility, "physicalFlopFeasibility");
        }
    }

    public record Result(
            Checkpoint checkpoint,
            Report report,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    private SixMaxSuitRefinementStudy() {}

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            List<Selection> selections,
            int iterations,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        if (iterations < 1 || iterations > MAX_ITERATIONS)
            throw new IllegalArgumentException("Study requires 1–1000 iterations");
        var game = new SixMaxSuitRefinementFlopGame(source, parent, table, selections);
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, pruning);
        var cp = checkpoint(source, table, game, solver.solve(iterations), pruning);
        return new Result(
                cp,
                assess(source, parent, table, cp),
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes());
    }

    static Checkpoint checkpoint(
            SixMaxPreflopSolutionPack source,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementFlopGame game,
            CfrSolution policy,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        java.util.Objects.requireNonNull(pruning, "pruning");
        String hash = SixMaxSuitRefinementPayoffTable.hash(table);
        return new Checkpoint(
                CHECKPOINT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                pruning == MultiPlayerCfrSolver.InactivePruning.NONE
                        ? "CFR_PLUS"
                        : SixMaxTextureStudy.PRUNED_ALGORITHM,
                "EXHAUSTIVE",
                table.sourcePackHash(),
                source.spotHash(),
                table.parentRankTableHash(),
                hash,
                gameHash(
                        table.sourcePackHash(),
                        source.spotHash(),
                        table.parentRankTableHash(),
                        hash,
                        game.selections()),
                game.selections(),
                game.completeTreeStates(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                policy);
    }

    static String gameHash(
            String sourceHash,
            String spotHash,
            String parentHash,
            String tableHash,
            List<Selection> selections)
            throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(
                                                        Map.of(
                                                                "model",
                                                                MODEL,
                                                                "sourcePackHash",
                                                                sourceHash,
                                                                "sourceSpotHash",
                                                                spotHash,
                                                                "parentRankTableHash",
                                                                parentHash,
                                                                "payoffTableHash",
                                                                tableHash,
                                                                "selections",
                                                                selections))));
    }

    public static SixMaxSuitRefinementFlopGame rebuild(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            Checkpoint cp)
            throws Exception {
        if (!cp.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !cp.sourceSpotHash().equals(source.spotHash())
                || !cp.parentRankTableHash().equals(SixMaxRankTexturePayoffTable.hash(parent))
                || !cp.payoffTableHash().equals(SixMaxSuitRefinementPayoffTable.hash(table))
                || !cp.gameHash()
                        .equals(
                                gameHash(
                                        cp.sourcePackHash(),
                                        cp.sourceSpotHash(),
                                        cp.parentRankTableHash(),
                                        cp.payoffTableHash(),
                                        cp.selections()))
                || !cp.solutionHash()
                        .equals(SixMaxConnectedPostflopAudit.solutionHash(cp.solution())))
            throw new IllegalArgumentException(
                    "Suit checkpoint source, parent, table, game or policy differs");
        var game = new SixMaxSuitRefinementFlopGame(source, parent, table, cp.selections());
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, cp.solution(), SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || complete.visitedStates() != cp.completeTreeStates()
                || game.completeTreeStates() != cp.completeTreeStates())
            throw new IllegalArgumentException(
                    "Suit checkpoint requires complete declared support");
        return game;
    }

    public static Checkpoint read(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table)
            throws Exception {
        var cp =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_CHECKPOINT_BYTES),
                                Checkpoint.class);
        rebuild(source, parent, table, cp);
        return cp;
    }

    public static void write(
            Path path,
            Checkpoint cp,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table)
            throws Exception {
        rebuild(source, parent, table, cp);
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(cp).getBytes(StandardCharsets.UTF_8),
                MAX_CHECKPOINT_BYTES);
    }

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            Checkpoint cp)
            throws Exception {
        var game = rebuild(source, parent, table, cp);
        var baseline = game.checkdownBaseline(source.solution());
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, baseline));
        var diagnostics = SixMaxFlopConditionalDiagnostics.assess(game.core(), cp.solution());
        var original = MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution());
        var errors = new ArrayList<Double>();
        for (int player = 0; player < 6; player++) {
            double error = before.profileUtilitiesBb().get(player) - original[player];
            if (Math.abs(error) > 1e-9)
                throw new IllegalStateException("Suit checkdown must recover source utilities");
            errors.add(error);
        }
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        if (!pre.strategy().keySet().equals(source.solution().strategy().keySet()))
            throw new IllegalStateException("Preflop support changed");
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
        var physical =
                SixMaxMaterialContinuationFeasibility.assess(
                        game.sourceGame(),
                        pre,
                        SixMaxMaterialContinuationFeasibility.Settings.researchDefault());
        return new Report(
                REPORT_SCHEMA,
                "VALIDATION_ONLY",
                MODEL,
                QUALITY_SCOPE,
                SixMaxRankTextureConditionalAudit.GAP_THRESHOLD_BB,
                SixMaxRankTextureConditionalAudit.MARGINAL_THRESHOLD,
                cp.sourcePackHash(),
                cp.sourceSpotHash(),
                cp.parentRankTableHash(),
                cp.payoffTableHash(),
                cp.gameHash(),
                cp.solutionHash(),
                cp.algorithm(),
                cp.chanceTraversal(),
                cp.solution().iterations(),
                table.refinedSignals(),
                table.observations().size(),
                (int)
                        table.observations().stream()
                                .filter(SixMaxSuitRefinementPayoffTable.Observation::physical)
                                .count(),
                pre.strategy().size(),
                cp.solution().strategy().size() - pre.strategy().size(),
                game.completeTreeStates(),
                game.selections(),
                game.coverage(),
                before,
                errors,
                maximum,
                diagnostics,
                physical);
    }

    public static void writeReport(Path output, Report report) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8),
                MAX_REPORT_BYTES);
    }

    public static Report replay(
            Path input,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            Checkpoint cp)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(input, MAX_REPORT_BYTES),
                                Report.class);
        var expected = assess(source, parent, table, cp);
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Suit study replay differs");
        return expected;
    }
}
