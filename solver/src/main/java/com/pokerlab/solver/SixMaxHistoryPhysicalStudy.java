package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Fresh joint CFR+ solving and full public-history diagnostics in a separately bound model. */
public final class SixMaxHistoryPhysicalStudy {
    public static final String CHECKPOINT_SCHEMA = "six-max-history-physical-checkpoint/v1";
    public static final String REPORT_SCHEMA = "six-max-history-physical-study/v1";
    public static final int MAX_ITERATIONS = 1000;
    static final int MAX_CHECKPOINT_BYTES = 64 * 1024 * 1024;
    static final int MAX_REPORT_BYTES = 32 * 1024 * 1024;

    public record Binding(
            String model,
            String sourcePackHash,
            String sourceSpotHash,
            String parentRankTableHash,
            String payoffTableHash,
            String gameHash,
            long completeTreeStates) {
        public Binding {
            if (!SixMaxHistoryPhysicalPayoffTable.MODEL.equals(model)
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxOneBetFlopGame.MAX_COMPLETE_STATES)
                throw new IllegalArgumentException("Invalid history physical binding");
            for (var hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            parentRankTableHash,
                            payoffTableHash,
                            gameHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid history physical hash");
        }
    }

    public record Checkpoint(
            String schemaVersion,
            String publicationStatus,
            Binding binding,
            String algorithm,
            String chanceTraversal,
            String solutionHash,
            CfrSolution solution) {
        public Checkpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || !solutionHash.matches("[0-9a-f]{64}")
                    || solution.iterations() < 1
                    || solution.iterations() > MAX_ITERATIONS)
                throw new IllegalArgumentException("Invalid history physical checkpoint");
            Objects.requireNonNull(binding, "binding");
        }
    }

    public record PhysicalCoverage(
            double wholeGameReach,
            double materialWholeGameReach,
            double materialAllHeadsUpFraction,
            int reachedCases,
            int materialCases) {
        public PhysicalCoverage {
            for (double p :
                    List.of(wholeGameReach, materialWholeGameReach, materialAllHeadsUpFraction))
                if (!Double.isFinite(p) || p < 0 || p > 1 + 1e-12)
                    throw new IllegalArgumentException("Invalid physical reach");
            if (materialWholeGameReach > wholeGameReach + 1e-12
                    || reachedCases < 0
                    || materialCases < 0
                    || materialCases > reachedCases)
                throw new IllegalArgumentException("Invalid physical coverage counts");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Binding binding,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int iterations,
            int observations,
            int revelations,
            int preflopInformationSets,
            int postflopInformationSets,
            List<Double> checkdownRecoveryErrorBb,
            SixMaxConnectedPreflopAudit.Quality checkdownBaseline,
            SixMaxFlopConditionalDiagnostics.Result jointlySolvedDiagnostics,
            SixMaxMaterialContinuationFeasibility.Report physicalFlopFeasibility,
            PhysicalCoverage physicalCoverage) {
        public Report {
            checkdownRecoveryErrorBb = List.copyOf(checkdownRecoveryErrorBb);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || trainerAdmission
                    || !solutionHash.matches("[0-9a-f]{64}")
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || iterations < 1
                    || iterations > MAX_ITERATIONS
                    || observations < 1
                    || observations > SixMaxHistoryPhysicalPayoffTable.MAX_OBSERVATIONS
                    || revelations < 1
                    || revelations > SixMaxHistoryPhysicalPayoffTable.MAX_REVELATIONS
                    || preflopInformationSets < 1
                    || postflopInformationSets < 1
                    || checkdownRecoveryErrorBb.size() != 6
                    || checkdownRecoveryErrorBb.stream()
                            .anyMatch(e -> !Double.isFinite(e) || Math.abs(e) > 1e-9))
                throw new IllegalArgumentException("Invalid history physical report");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(checkdownBaseline, "checkdownBaseline");
            Objects.requireNonNull(jointlySolvedDiagnostics, "jointlySolvedDiagnostics");
            Objects.requireNonNull(physicalFlopFeasibility, "physicalFlopFeasibility");
            Objects.requireNonNull(physicalCoverage, "physicalCoverage");
        }
    }

    public record Result(
            Checkpoint checkpoint,
            Report report,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    /** Counters from fresh training only; diagnostic traversals are deliberately excluded. */
    public record TrainingEvidence(
            String publicationStatus,
            Binding binding,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int freshIterations,
            int initialRegretRows,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {
        public TrainingEvidence {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(traversal, "traversal");
            if (!"VALIDATION_ONLY".equals(publicationStatus)
                    || !solutionHash.matches("[0-9a-f]{64}")
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || initialRegretRows != 0
                    || freshIterations < 1
                    || freshIterations > MAX_ITERATIONS
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || traversal.visitedNodes() <= 0
                    || traversal.terminalNodes() <= 0
                    || traversal.terminalNodes() > traversal.visitedNodes()
                    || traversal.sampledChanceNodes() != 0
                    || traversal.baselineCorrections() != 0
                    || inactiveUtilityPrunedNodes < 0
                    || ("CFR_PLUS".equals(algorithm) && inactiveUtilityPrunedNodes != 0))
                throw new IllegalArgumentException("Invalid fresh exhaustive traversal evidence");
        }
    }

    public static TrainingEvidence trainingEvidence(Result result) {
        var cp = result.checkpoint();
        return new TrainingEvidence(
                "VALIDATION_ONLY",
                cp.binding(),
                cp.solutionHash(),
                cp.algorithm(),
                cp.chanceTraversal(),
                cp.solution().iterations(),
                0,
                result.traversal(),
                result.inactiveUtilityPrunedNodes());
    }

    public static void writeTrainingEvidence(Path path, Result result) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(trainingEvidence(result)).getBytes(StandardCharsets.UTF_8),
                16384);
    }

    /** Exhaustive traversal/pruning support is policy-independent in this fixed game. */
    public static TrainingEvidence replayTrainingEvidence(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, 16384),
                                TrainingEvidence.class);
        if (!saved.binding().equals(cp.binding())
                || !saved.solutionHash().equals(cp.solutionHash())
                || !saved.algorithm().equals(cp.algorithm())
                || !saved.chanceTraversal().equals(cp.chanceTraversal())
                || saved.freshIterations() != cp.solution().iterations())
            throw new IllegalArgumentException("Training evidence lineage differs");
        var game = rebuild(source, parent, table, cp);
        var pruning =
                "CFR_PLUS".equals(cp.algorithm())
                        ? MultiPlayerCfrSolver.InactivePruning.NONE
                        : MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY;
        var fresh = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, pruning);
        fresh.solve(1);
        long n = saved.freshIterations();
        if (saved.traversal().visitedNodes() != n * fresh.statistics().visitedNodes()
                || saved.traversal().terminalNodes() != n * fresh.statistics().terminalNodes()
                || saved.inactiveUtilityPrunedNodes() != n * fresh.inactiveUtilityPrunedNodes())
            throw new IllegalArgumentException("Fresh exhaustive traversal count replay differs");
        return saved;
    }

    private SixMaxHistoryPhysicalStudy() {}

    static Binding binding(SixMaxHistoryPhysicalPayoffTable.Verified verified) throws Exception {
        var t = verified.artifact();
        String tableHash = SixMaxHistoryPhysicalPayoffTable.hash(t);
        String gameHash =
                SixMaxHistoryPhysicalPayoffTable.hashValue(
                        Map.of(
                                "model",
                                t.model(),
                                "payoffTableHash",
                                tableHash,
                                "menu",
                                t.menu(),
                                "sourcePackHash",
                                t.sourcePackHash(),
                                "sourceSpotHash",
                                t.sourceSpotHash(),
                                "parentRankTableHash",
                                t.parentRankTableHash()));
        return new Binding(
                t.model(),
                t.sourcePackHash(),
                t.sourceSpotHash(),
                t.parentRankTableHash(),
                tableHash,
                gameHash,
                t.completeTreeStates());
    }

    static Checkpoint checkpoint(
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            CfrSolution policy,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        Objects.requireNonNull(pruning, "pruning");
        return new Checkpoint(
                CHECKPOINT_SCHEMA,
                "VALIDATION_ONLY",
                binding(table),
                pruning == MultiPlayerCfrSolver.InactivePruning.NONE
                        ? "CFR_PLUS"
                        : SixMaxTextureStudy.PRUNED_ALGORITHM,
                "EXHAUSTIVE",
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                policy);
    }

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            int iterations,
            MultiPlayerCfrSolver.InactivePruning pruning)
            throws Exception {
        if (iterations < 1 || iterations > MAX_ITERATIONS)
            throw new IllegalArgumentException("Study requires 1–1000 fresh iterations");
        var game = new SixMaxHistoryPhysicalFlopGame(source, parent, table);
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS, pruning);
        var cp = checkpoint(table, solver.solve(iterations), pruning);
        return new Result(
                cp,
                assess(source, parent, table, cp),
                solver.statistics(),
                solver.inactiveUtilityPrunedNodes());
    }

    public static SixMaxHistoryPhysicalFlopGame rebuild(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        if (!cp.binding().equals(binding(table))
                || !cp.solutionHash()
                        .equals(SixMaxConnectedPostflopAudit.solutionHash(cp.solution())))
            throw new IllegalArgumentException("History checkpoint lineage or policy differs");
        var game = new SixMaxHistoryPhysicalFlopGame(source, parent, table);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, cp.solution(), SixMaxOneBetFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || complete.visitedStates() != cp.binding().completeTreeStates())
            throw new IllegalArgumentException("History checkpoint requires complete support");
        return game;
    }

    /** Full diagnostics can only be reused after actual assessment or read-only replay. */
    public static final class Validated {
        private final SixMaxHistoryPhysicalFlopGame game;
        private final Checkpoint checkpoint;
        private final Report report;

        private Validated(
                SixMaxHistoryPhysicalFlopGame game, Checkpoint checkpoint, Report report) {
            this.game = game;
            this.checkpoint = checkpoint;
            this.report = report;
        }

        SixMaxOneBetFlopGame core() {
            return game.core();
        }

        public Checkpoint checkpoint() {
            return checkpoint;
        }

        public Report report() {
            return report;
        }
    }

    public static Validated validate(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        var report = assess(source, parent, table, cp);
        return new Validated(new SixMaxHistoryPhysicalFlopGame(source, parent, table), cp, report);
    }

    public static Validated replayValidated(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        var report = replay(path, source, parent, table, cp);
        return new Validated(new SixMaxHistoryPhysicalFlopGame(source, parent, table), cp, report);
    }

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        var game = rebuild(source, parent, table, cp);
        var baseline = game.checkdownBaseline(source.solution());
        var before =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(game, baseline));
        var original = MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution());
        var errors = new ArrayList<Double>();
        for (int p = 0; p < 6; p++) errors.add(before.profileUtilitiesBb().get(p) - original[p]);
        var diagnostics = SixMaxFlopConditionalDiagnostics.assess(game.core(), cp.solution());
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        if (!pre.strategy().keySet().equals(source.solution().strategy().keySet()))
            throw new IllegalStateException("History model changed preflop support");
        var feasibility =
                SixMaxMaterialContinuationFeasibility.assess(
                        game.sourceGame(),
                        pre,
                        SixMaxMaterialContinuationFeasibility.Settings.researchDefault());
        double reach = 0, materialReach = 0;
        int reached = 0, material = 0;
        for (var h : diagnostics.histories())
            for (var signal : h.signals()) {
                if (!table.artifact().observations().get(signal.observation()).physical()
                        || signal.signalProbabilityGivenHistory() == 0) continue;
                double weight = h.historyProbability() * signal.signalProbabilityGivenHistory();
                reached++;
                reach += weight;
                if (h.historyProbability() >= .0001
                        && signal.firstCombosAtFivePercent() >= 2
                        && signal.secondCombosAtFivePercent() >= 2) {
                    material++;
                    materialReach += weight;
                }
            }
        return new Report(
                REPORT_SCHEMA,
                "VALIDATION_ONLY",
                false,
                cp.binding(),
                cp.solutionHash(),
                cp.algorithm(),
                cp.chanceTraversal(),
                cp.solution().iterations(),
                table.artifact().observations().size(),
                table.artifact().menu().revelations().size(),
                pre.strategy().size(),
                cp.solution().strategy().size() - pre.strategy().size(),
                errors,
                before,
                diagnostics,
                feasibility,
                new PhysicalCoverage(
                        reach,
                        materialReach,
                        feasibility.headsUpProbability() == 0
                                ? 0
                                : materialReach / feasibility.headsUpProbability(),
                        reached,
                        material));
    }

    public static Checkpoint read(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table)
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
            SixMaxHistoryPhysicalPayoffTable.Verified table)
            throws Exception {
        rebuild(source, parent, table, cp);
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(cp).getBytes(StandardCharsets.UTF_8),
                MAX_CHECKPOINT_BYTES);
    }

    public static void writeReport(Path path, Report report) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8),
                MAX_REPORT_BYTES);
    }

    public static Report replay(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified table,
            Checkpoint cp)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_REPORT_BYTES),
                                Report.class);
        var expected = assess(source, parent, table, cp);
        if (!expected.equals(saved))
            throw new IllegalArgumentException("History study replay differs");
        return expected;
    }
}
