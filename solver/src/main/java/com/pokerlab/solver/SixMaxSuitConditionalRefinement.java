package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Bounded derived policies: frozen preflop, fresh local solves, complete parent re-audit. */
public final class SixMaxSuitConditionalRefinement {
    public static final String ARTIFACT_SCHEMA = "six-max-suit-conditional-derived-policy/v1";
    public static final String REPORT_SCHEMA = "six-max-suit-conditional-refinement/v1";
    public static final String ALGORITHM =
            "FROZEN_PREFLOP_FRESH_CONDITIONAL_CFR_PLUS_FIXED_UTILITY/v1";
    public static final double COMPARISON_TOLERANCE_BB = 1e-9;
    public static final int MAX_CASES = 64;

    public enum Priority {
        LARGEST_GAP,
        REACH_WEIGHTED_GAP,
        BALANCED_GAP_AND_REACH
    }

    public record Settings(
            Priority priority,
            int maximumCases,
            List<Integer> iterationBudgets,
            double targetGapBb) {
        public Settings {
            Objects.requireNonNull(priority, "priority");
            iterationBudgets = List.copyOf(iterationBudgets);
            if (maximumCases < 1
                    || maximumCases > MAX_CASES
                    || iterationBudgets.isEmpty()
                    || iterationBudgets.size() > 8
                    || !Double.isFinite(targetGapBb)
                    || targetGapBb < 1e-6
                    || targetGapBb > .01)
                throw new IllegalArgumentException("Conditional refinement settings exceed caps");
            int previous = 0;
            for (int budget : iterationBudgets) {
                if (budget <= previous || budget > 1000)
                    throw new IllegalArgumentException("Fresh budgets must increase within 1–1000");
                previous = budget;
            }
        }
    }

    /** Each trial starts at zero regrets; budgets are not cumulative or resumed training. */
    public record Trial(
            int iterations,
            String localSolutionHash,
            SixMaxConnectedPreflopAudit.Quality quality,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    public record Branch(
            List<PublicAction> history,
            int observation,
            String observationKey,
            double historyProbability,
            double observationProbabilityGivenHistory,
            int posteriorJointDeals,
            String status,
            int replacedInformationSets,
            int chosenIterations,
            SixMaxConnectedPreflopAudit.Quality before,
            SixMaxConnectedPreflopAudit.Quality after,
            List<Trial> trials) {
        public Branch {
            history = List.copyOf(history);
            trials = List.copyOf(trials);
        }
    }

    /** A different schema from a fresh joint checkpoint; predecessor iterations stay unchanged. */
    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String model,
            String algorithm,
            String predecessorCheckpointHash,
            String predecessorSolutionHash,
            String gameHash,
            String frozenPreflopHash,
            Settings settings,
            String solutionHash,
            CfrSolution solution) {
        public Artifact {
            identity(
                    schemaVersion,
                    ARTIFACT_SCHEMA,
                    publicationStatus,
                    model,
                    algorithm,
                    predecessorCheckpointHash,
                    predecessorSolutionHash,
                    gameHash,
                    frozenPreflopHash,
                    solutionHash);
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(solution, "solution");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String model,
            String algorithm,
            String predecessorCheckpointHash,
            String predecessorSolutionHash,
            String gameHash,
            String frozenPreflopHash,
            Settings settings,
            String candidateSolutionHash,
            boolean accepted,
            List<String> rejectionReasons,
            int inputInformationSets,
            int replacedInformationSets,
            int preservedInformationSets,
            List<Branch> branches,
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after) {
        public Report {
            identity(
                    schemaVersion,
                    REPORT_SCHEMA,
                    publicationStatus,
                    model,
                    algorithm,
                    predecessorCheckpointHash,
                    predecessorSolutionHash,
                    gameHash,
                    frozenPreflopHash,
                    candidateSolutionHash);
            Objects.requireNonNull(settings, "settings");
            rejectionReasons = List.copyOf(rejectionReasons);
            branches = List.copyOf(branches);
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            if (accepted != rejectionReasons.isEmpty()
                    || branches.size() > settings.maximumCases()
                    || inputInformationSets < 1
                    || replacedInformationSets < 0
                    || preservedInformationSets < 0
                    || replacedInformationSets + preservedInformationSets != inputInformationSets)
                throw new IllegalArgumentException("Inconsistent conditional refinement report");
        }
    }

    /** Only this class can construct a result that the writer will export. */
    public static final class Result {
        private final Artifact artifact;
        private final Report report;

        private Result(Artifact artifact, Report report) {
            this.artifact = artifact;
            this.report = report;
        }

        public Optional<Artifact> artifact() {
            return Optional.ofNullable(artifact);
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxSuitConditionalRefinement() {}

    public static Result refine(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint predecessor,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        var game = SixMaxSuitRefinementStudy.rebuild(source, parent, table, predecessor);
        var original = predecessor.solution();
        var before = SixMaxFlopConditionalDiagnostics.assess(game.core(), original);
        var selected =
                SixMaxConditionalRefinementEngine.select(
                        before,
                        settings,
                        c -> c.signal().quality().nashConvBb() > settings.targetGapBb());
        var computed =
                SixMaxConditionalRefinementEngine.refine(
                        game.core(), original, before, selected, settings, progress);
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(original);
        var candidate = computed.candidate();
        var changedKeys = computed.changedKeys();
        var branches = computed.branches();
        var after = computed.after();
        var reasons = computed.reasons();
        String predecessorHash = checkpointHash(predecessor);
        String preHash = SixMaxConnectedPostflopAudit.solutionHash(pre);
        String candidateHash = SixMaxConnectedPostflopAudit.solutionHash(candidate);
        var report =
                new Report(
                        REPORT_SCHEMA,
                        "VALIDATION_ONLY",
                        SixMaxSuitRefinementStudy.MODEL,
                        ALGORITHM,
                        predecessorHash,
                        predecessor.solutionHash(),
                        predecessor.gameHash(),
                        preHash,
                        settings,
                        candidateHash,
                        reasons.isEmpty(),
                        reasons,
                        original.strategy().size(),
                        changedKeys.size(),
                        original.strategy().size() - changedKeys.size(),
                        branches,
                        before,
                        after);
        var artifact =
                reasons.isEmpty()
                        ? new Artifact(
                                ARTIFACT_SCHEMA,
                                "VALIDATION_ONLY",
                                SixMaxSuitRefinementStudy.MODEL,
                                ALGORITHM,
                                predecessorHash,
                                predecessor.solutionHash(),
                                predecessor.gameHash(),
                                preHash,
                                settings,
                                candidateHash,
                                candidate)
                        : null;
        return new Result(artifact, report);
    }

    static List<String> rejectionReasons(
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<Branch> branches,
            Settings settings) {
        return SixMaxConditionalRefinementEngine.rejectionReasons(
                before, after, branches, settings);
    }

    public static String checkpointHash(SixMaxSuitRefinementStudy.Checkpoint cp) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(SixMaxTexturePayoffTable.mapper().writeValueAsBytes(cp)));
    }

    private static void identity(
            String schema,
            String expected,
            String status,
            String model,
            String algorithm,
            String... hashes) {
        if (!expected.equals(schema)
                || !"VALIDATION_ONLY".equals(status)
                || !SixMaxSuitRefinementStudy.MODEL.equals(model)
                || !ALGORITHM.equals(algorithm))
            throw new IllegalArgumentException("Unsupported derived refinement identity");
        for (String hash : hashes)
            if (!hash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid refinement hash");
    }

    /** A rejected candidate exports diagnostics only. Never overwrite a previous derived policy. */
    public static void write(Path artifactPath, Path reportPath, Result result) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(artifactPath, reportPath));
        if (Files.exists(artifactPath))
            throw new IllegalArgumentException("Derived policy output must be a new path");
        byte[] report = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (report.length > SixMaxSuitRefinementStudy.MAX_REPORT_BYTES)
            throw new IllegalArgumentException("Refinement report cap exceeded");
        byte[] artifact =
                result.artifact().isPresent()
                        ? SixMaxTextureStudy.json(result.artifact().orElseThrow())
                                .getBytes(StandardCharsets.UTF_8)
                        : null;
        if (artifact != null && artifact.length > SixMaxSuitRefinementStudy.MAX_CHECKPOINT_BYTES)
            throw new IllegalArgumentException("Derived policy cap exceeded");
        SixMaxRankTexturePayoffTable.writeBytes(
                reportPath, report, SixMaxSuitRefinementStudy.MAX_REPORT_BYTES);
        if (artifact != null)
            SixMaxRankTexturePayoffTable.writeBytes(
                    artifactPath, artifact, SixMaxSuitRefinementStudy.MAX_CHECKPOINT_BYTES);
    }

    /**
     * Read-only replay reproduces selection, every fresh local trial and all parent diagnostics.
     */
    public static Result replay(
            Path artifactPath,
            Path reportPath,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint predecessor)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(artifactPath, reportPath));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        reportPath, SixMaxSuitRefinementStudy.MAX_REPORT_BYTES),
                                Report.class);
        if (!saved.predecessorCheckpointHash().equals(checkpointHash(predecessor))
                || !saved.predecessorSolutionHash().equals(predecessor.solutionHash())
                || !saved.gameHash().equals(predecessor.gameHash()))
            throw new IllegalArgumentException("Refinement predecessor lineage differs");
        Artifact artifact = null;
        if (saved.accepted())
            artifact =
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(
                                            artifactPath,
                                            SixMaxSuitRefinementStudy.MAX_CHECKPOINT_BYTES),
                                    Artifact.class);
        else if (Files.exists(artifactPath))
            throw new IllegalArgumentException(
                    "Rejected refinement must not have a policy artifact");
        var expected = refine(source, parent, table, predecessor, saved.settings(), b -> {});
        if (!saved.equals(expected.report())
                || !Objects.equals(artifact, expected.artifact().orElse(null)))
            throw new IllegalArgumentException("Conditional refinement replay differs");
        return expected;
    }
}
