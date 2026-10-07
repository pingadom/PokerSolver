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
    private static final int MAX_LOCAL_STATES = 1000;

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

    private record Candidate(
            SixMaxFlopConditionalDiagnostics.History history,
            SixMaxFlopConditionalDiagnostics.Case signal) {}

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
        var selected = select(before, settings);
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(original);
        var updated = new LinkedHashMap<>(original.strategy());
        var changedKeys = new HashSet<String>();
        var branches = new ArrayList<Branch>();
        for (var candidate : selected) {
            var h = candidate.history();
            var s = candidate.signal();
            var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, h.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(
                            game.core(), transition, s.observation());
            if (posterior.roots().isEmpty()
                    || posterior.signalProbability() != s.signalProbabilityGivenHistory())
                throw new IllegalStateException("Selected conditional posterior differs");
            var local =
                    new SixMaxRankTextureConditionalAudit.ConditionalGame(game, posterior.roots());
            var trials = new ArrayList<Trial>();
            CfrSolution best = null;
            var bestQuality = s.quality();
            for (int iterations : settings.iterationBudgets()) {
                var solver =
                        new MultiPlayerCfrSolver<>(
                                local,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                var solved = solver.solve(iterations);
                if (MultiPlayerStrategyCompletion.uniformAtUnseen(local, solved, MAX_LOCAL_STATES)
                                .addedInformationSets()
                        != 0) throw new IllegalStateException("Local solve has incomplete support");
                var quality =
                        SixMaxConnectedPreflopAudit.Quality.of(
                                MultiPlayerInformationSetBestResponse.assess(local, solved));
                trials.add(
                        new Trial(
                                iterations,
                                SixMaxConnectedPostflopAudit.solutionHash(solved),
                                quality,
                                solver.statistics(),
                                solver.inactiveUtilityPrunedNodes()));
                if (quality.nashConvBb() < bestQuality.nashConvBb()) {
                    best = solved;
                    bestQuality = quality;
                }
                if (bestQuality.nashConvBb() <= settings.targetGapBb()) break;
            }
            int replaced = 0;
            if (best != null) {
                for (var row : best.strategy().entrySet()) {
                    if (!row.getKey().contains(":postflop:suit-refinement:")
                            || !updated.containsKey(row.getKey())
                            || !changedKeys.add(row.getKey()))
                        throw new IllegalStateException("Local rows are foreign or overlap");
                    updated.put(row.getKey(), row.getValue());
                    replaced++;
                }
            }
            var branch =
                    new Branch(
                            h.history(),
                            s.observation(),
                            s.observationKey(),
                            h.historyProbability(),
                            s.signalProbabilityGivenHistory(),
                            posterior.roots().size(),
                            bestQuality.nashConvBb() <= settings.targetGapBb()
                                    ? "TARGET_MET"
                                    : best == null ? "NO_LOCAL_IMPROVEMENT" : "BUDGET_EXHAUSTED",
                            replaced,
                            best == null ? 0 : best.iterations(),
                            s.quality(),
                            bestQuality,
                            trials);
            branches.add(branch);
            progress.accept(branch);
        }
        var candidate = new CfrSolution(original.iterations(), updated);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, candidate, SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || !candidate.strategy().keySet().equals(original.strategy().keySet())
                || !pre.equals(SixMaxPreflopContinuationFeedback.preflopPolicy(candidate)))
            throw new IllegalStateException(
                    "Refinement changed complete support or frozen preflop");
        for (var row : original.strategy().entrySet())
            if (!changedKeys.contains(row.getKey())
                    && !row.getValue().equals(candidate.strategy().get(row.getKey())))
                throw new IllegalStateException("Refinement changed an unselected row");
        var after = SixMaxFlopConditionalDiagnostics.assess(game.core(), candidate);
        verifyCases(before, after, branches);
        var reasons = rejectionReasons(before, after, branches, settings);
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

    private static List<Candidate> select(
            SixMaxFlopConditionalDiagnostics.Result before, Settings settings) {
        var candidates = new ArrayList<Candidate>();
        for (var history : before.histories())
            for (var signal : history.signals())
                if (signal.quality() != null
                        && signal.quality().nashConvBb() > settings.targetGapBb())
                    candidates.add(new Candidate(history, signal));
        // Stable order of the bound public menu then observation index resolves equal priorities.
        var largest = new ArrayList<>(candidates);
        largest.sort(
                Comparator.comparingDouble((Candidate c) -> c.signal().quality().nashConvBb())
                        .reversed());
        candidates.sort(
                Comparator.comparingDouble(
                                (Candidate c) ->
                                        c.signal().quality().nashConvBb()
                                                * c.history().historyProbability()
                                                * c.signal().signalProbabilityGivenHistory())
                        .reversed());
        int count = Math.min(settings.maximumCases(), candidates.size());
        if (settings.priority() != Priority.BALANCED_GAP_AND_REACH) {
            var ordered = settings.priority() == Priority.LARGEST_GAP ? largest : candidates;
            return List.copyOf(ordered.subList(0, count));
        }
        var balanced = new ArrayList<Candidate>();
        var used = new HashSet<String>();
        int gapIndex = 0, reachIndex = 0;
        while (balanced.size() < count) {
            var queue = balanced.size() % 2 == 0 ? largest : candidates;
            int index = balanced.size() % 2 == 0 ? gapIndex : reachIndex;
            Candidate next;
            do {
                next = queue.get(index++);
            } while (!used.add(
                    next.history().history().toString() + ":" + next.signal().observation()));
            if (balanced.size() % 2 == 0) gapIndex = index;
            else reachIndex = index;
            balanced.add(next);
        }
        return List.copyOf(balanced);
    }

    private static void verifyCases(
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<Branch> branches) {
        if (before.histories().size() != after.histories().size())
            throw new IllegalStateException("Conditional support changed");
        for (int i = 0; i < before.histories().size(); i++) {
            var old = before.histories().get(i);
            var now = after.histories().get(i);
            if (!old.history().equals(now.history())
                    || !old.status().equals(now.status())
                    || old.historyProbability() != now.historyProbability()
                    || old.signals().size() != now.signals().size())
                throw new IllegalStateException("Frozen preflop posterior changed");
            for (int j = 0; j < old.signals().size(); j++) {
                var a = old.signals().get(j);
                var b = now.signals().get(j);
                var branch =
                        branches.stream()
                                .filter(
                                        c ->
                                                c.history().equals(old.history())
                                                        && c.observation() == a.observation())
                                .findFirst();
                if (branch.isEmpty()) {
                    if (!a.equals(b)) throw new IllegalStateException("Unselected case changed");
                } else {
                    if (a.signalProbabilityGivenHistory() != b.signalProbabilityGivenHistory()
                            || !a.firstMarginal().equals(b.firstMarginal())
                            || !a.secondMarginal().equals(b.secondMarginal())
                            || !branch.orElseThrow().after().equals(b.quality()))
                        throw new IllegalStateException("Assembled case differs from local solve");
                }
            }
        }
    }

    static List<String> rejectionReasons(
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<Branch> branches,
            Settings settings) {
        var reasons = new ArrayList<String>();
        if (branches.isEmpty()) reasons.add("NO_CASES_ABOVE_TARGET");
        if (branches.stream().anyMatch(b -> b.after().nashConvBb() > settings.targetGapBb()))
            reasons.add("SELECTED_LOCAL_TARGET_NOT_MET");
        if (after.parentWitness().parentQuality().nashConvBb()
                > before.parentWitness().parentQuality().nashConvBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("PARENT_NASHCONV_REGRESSION");
        if (after.parentWitness().reachWeightedLocalNashConvBb()
                > before.parentWitness().reachWeightedLocalNashConvBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("REACH_WEIGHTED_LOCAL_REGRESSION");
        if (after.summary().largestConditionalGapBb()
                > before.summary().largestConditionalGapBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("MAXIMUM_LOCAL_REGRESSION");
        return List.copyOf(reasons);
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
