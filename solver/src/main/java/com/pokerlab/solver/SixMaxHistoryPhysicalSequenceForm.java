package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Frozen-preflop sequence-form derivative with its own lineage, complete diagnostics and export
 * gate.
 */
public final class SixMaxHistoryPhysicalSequenceForm {
    public static final String ALGORITHM = FiniteTwoPlayerSequenceForm.ALGORITHM;
    public static final String SELECTION = "MATERIAL_PHYSICAL_ABOVE_TARGET_GAP_DESCENDING/v1";
    public static final String ARTIFACT_SCHEMA = "six-max-history-physical-sequence-form-policy/v1";
    public static final String REPORT_SCHEMA =
            "six-max-history-physical-sequence-form-refinement/v1";
    public static final String JOINT_PREDECESSOR = "FRESH_JOINT_STUDY/v1";
    public static final String CFR_PREDECESSOR = "ACCEPTED_CFR_DERIVATIVE/v1";

    public record Settings(int maximumCases, double targetGapBb) {
        public Settings {
            if (maximumCases < 1
                    || maximumCases > 64
                    || !Double.isFinite(targetGapBb)
                    || targetGapBb < 1e-6
                    || targetGapBb > .01)
                throw new IllegalArgumentException("Sequence-form settings exceed bounds");
        }
    }

    public record Branch(
            List<PublicAction> history,
            int observation,
            String observationKey,
            double historyProbability,
            double observationProbabilityGivenHistory,
            int posteriorJointDeals,
            int replacedInformationSets,
            SixMaxConnectedPreflopAudit.Quality before,
            SixMaxConnectedPreflopAudit.Quality after,
            FiniteTwoPlayerSequenceForm.Audit solve) {
        public Branch {
            history = List.copyOf(history);
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String algorithm,
            String selection,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String predecessorCheckpointHash,
            String predecessorKind,
            String predecessorPolicyHash,
            String predecessorReportHash,
            String frozenPreflopHash,
            Settings settings,
            String solutionHash,
            CfrSolution solution) {
        public Artifact {
            identity(
                    schemaVersion,
                    ARTIFACT_SCHEMA,
                    publicationStatus,
                    trainerAdmission,
                    algorithm,
                    selection,
                    binding,
                    predecessorCheckpointHash,
                    predecessorPolicyHash,
                    predecessorReportHash,
                    frozenPreflopHash,
                    solutionHash);
            validatePredecessorKind(predecessorKind);
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(solution, "solution");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String algorithm,
            String selection,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String predecessorCheckpointHash,
            String predecessorKind,
            String predecessorPolicyHash,
            String predecessorReportHash,
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
                    trainerAdmission,
                    algorithm,
                    selection,
                    binding,
                    predecessorCheckpointHash,
                    predecessorPolicyHash,
                    predecessorReportHash,
                    frozenPreflopHash,
                    candidateSolutionHash);
            validatePredecessorKind(predecessorKind);
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            branches = List.copyOf(branches);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (accepted != rejectionReasons.isEmpty()
                    || branches.size() > settings.maximumCases()
                    || inputInformationSets < 1
                    || replacedInformationSets < 0
                    || preservedInformationSets < 0
                    || replacedInformationSets + preservedInformationSets != inputInformationSets)
                throw new IllegalArgumentException("Invalid sequence-form report counts");
        }
    }

    /** Only optimization and complete replay can produce an exportable result. */
    public static final class Result {
        private final Artifact artifact;
        private final Report report;
        private final SixMaxOneBetFlopGame game;

        private Result(Artifact artifact, Report report, SixMaxOneBetFlopGame game) {
            this.artifact = artifact;
            this.report = report;
            this.game = game;
        }

        public Optional<Artifact> artifact() {
            return Optional.ofNullable(artifact);
        }

        public Report report() {
            return report;
        }

        SixMaxOneBetFlopGame core() {
            return game;
        }
    }

    private SixMaxHistoryPhysicalSequenceForm() {}

    private record Input(
            SixMaxOneBetFlopGame game,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String rootCheckpointHash,
            String kind,
            String policyHash,
            String reportHash,
            CfrSolution solution,
            SixMaxFlopConditionalDiagnostics.Result diagnostics) {}

    private static Input input(SixMaxHistoryPhysicalStudy.Validated predecessor) throws Exception {
        var cp = predecessor.checkpoint();
        return new Input(
                predecessor.core(),
                cp.binding(),
                hash(cp),
                JOINT_PREDECESSOR,
                hash(cp),
                hash(predecessor.report()),
                cp.solution(),
                predecessor.report().jointlySolvedDiagnostics());
    }

    private static Input input(SixMaxHistoryPhysicalConditionalRefinement.Result predecessor)
            throws Exception {
        var a =
                predecessor
                        .artifact()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Accepted CFR derivative required"));
        return new Input(
                predecessor.core(),
                a.binding(),
                a.predecessorCheckpointHash(),
                CFR_PREDECESSOR,
                hash(a),
                hash(predecessor.report()),
                a.solution(),
                predecessor.report().after());
    }

    public static Result refine(
            SixMaxHistoryPhysicalStudy.Validated predecessor,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(predecessor, "predecessor");
        return refine(input(predecessor), settings, progress);
    }

    public static Result refine(
            SixMaxHistoryPhysicalConditionalRefinement.Result predecessor,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(predecessor, "predecessor");
        return refine(input(predecessor), settings, progress);
    }

    private static Result refine(Input input, Settings settings, Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        var game = input.game();
        var original = input.solution();
        var before = input.diagnostics();
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(original);
        var selected = new ArrayList<SixMaxConditionalRefinementEngine.Candidate>();
        for (var h : before.histories())
            for (var s : h.signals())
                if (s.quality() != null
                        && s.quality().nashConvBb() > settings.targetGapBb()
                        && game.payoffView().key(s.observation()).startsWith("board:")
                        && h.historyProbability() >= SixMaxSuitDecisionStability.MIN_HISTORY_REACH
                        && s.firstCombosAtFivePercent() >= 2
                        && s.secondCombosAtFivePercent() >= 2)
                    selected.add(new SixMaxConditionalRefinementEngine.Candidate(h, s));
        selected.sort(
                Comparator.comparingDouble(
                                (SixMaxConditionalRefinementEngine.Candidate c) ->
                                        c.signal().quality().nashConvBb())
                        .reversed());
        var updated = new LinkedHashMap<>(original.strategy());
        var changed = new HashSet<String>();
        var touched = new HashSet<String>();
        var branches = new ArrayList<Branch>();
        for (var c : selected.subList(0, Math.min(settings.maximumCases(), selected.size()))) {
            var h = c.history();
            var s = c.signal();
            var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, h.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(game, transition, s.observation());
            if (posterior.roots().isEmpty()
                    || posterior.signalProbability() != s.signalProbabilityGivenHistory())
                throw new IllegalStateException("Selected sequence-form posterior differs");
            var local =
                    new SixMaxRankTextureConditionalAudit.ConditionalGame(game, posterior.roots());
            var solved = FiniteTwoPlayerSequenceForm.solve(local);
            int replaced = 0;
            for (var row : solved.strategy().entrySet()) {
                if (!row.getKey().contains(":postflop:" + game.payoffView().namespace() + ":")
                        || !original.strategy().containsKey(row.getKey())
                        || !touched.add(row.getKey()))
                    throw new IllegalStateException("Sequence-form rows are foreign or overlap");
                if (!row.getValue().equals(original.strategy().get(row.getKey()))) {
                    changed.add(row.getKey());
                    replaced++;
                }
                updated.put(row.getKey(), row.getValue());
            }
            var branch =
                    new Branch(
                            h.history(),
                            s.observation(),
                            s.observationKey(),
                            h.historyProbability(),
                            s.signalProbabilityGivenHistory(),
                            posterior.roots().size(),
                            replaced,
                            s.quality(),
                            SixMaxConnectedPreflopAudit.Quality.of(
                                    solved.audit().behavioralQuality()),
                            solved.audit());
            branches.add(branch);
            progress.accept(branch);
        }
        var candidate = new CfrSolution(original.iterations(), updated);
        if (!candidate.strategy().keySet().equals(original.strategy().keySet())
                || !pre.equals(SixMaxPreflopContinuationFeedback.preflopPolicy(candidate)))
            throw new IllegalStateException("Sequence-form changed support or frozen preflop");
        for (var row : original.strategy().entrySet())
            if (!changed.contains(row.getKey())
                    && !row.getValue().equals(candidate.strategy().get(row.getKey())))
                throw new IllegalStateException("Sequence-form changed an unselected row");
        var after = SixMaxFlopConditionalDiagnostics.assess(game, candidate);
        verifyCases(before, after, branches);
        var reasons = new ArrayList<String>();
        if (branches.isEmpty()) reasons.add("NO_MATERIAL_CASES_ABOVE_TARGET");
        if (branches.stream().anyMatch(b -> b.after().nashConvBb() > settings.targetGapBb()))
            reasons.add("SELECTED_LOCAL_TARGET_NOT_MET");
        if (after.parentWitness().parentQuality().nashConvBb()
                > before.parentWitness().parentQuality().nashConvBb() + 1e-9)
            reasons.add("PARENT_NASHCONV_REGRESSION");
        if (after.parentWitness().reachWeightedLocalNashConvBb()
                > before.parentWitness().reachWeightedLocalNashConvBb() + 1e-9)
            reasons.add("REACH_WEIGHTED_LOCAL_REGRESSION");
        if (after.summary().largestConditionalGapBb()
                > before.summary().largestConditionalGapBb() + 1e-9)
            reasons.add("MAXIMUM_LOCAL_REGRESSION");
        String preHash = SixMaxConnectedPostflopAudit.solutionHash(pre),
                candidateHash = SixMaxConnectedPostflopAudit.solutionHash(candidate);
        var report =
                new Report(
                        REPORT_SCHEMA,
                        "VALIDATION_ONLY",
                        false,
                        ALGORITHM,
                        SELECTION,
                        input.binding(),
                        input.rootCheckpointHash(),
                        input.kind(),
                        input.policyHash(),
                        input.reportHash(),
                        preHash,
                        settings,
                        candidateHash,
                        reasons.isEmpty(),
                        reasons,
                        original.strategy().size(),
                        changed.size(),
                        original.strategy().size() - changed.size(),
                        branches,
                        before,
                        after);
        var artifact =
                reasons.isEmpty()
                        ? new Artifact(
                                ARTIFACT_SCHEMA,
                                "VALIDATION_ONLY",
                                false,
                                ALGORITHM,
                                SELECTION,
                                input.binding(),
                                input.rootCheckpointHash(),
                                input.kind(),
                                input.policyHash(),
                                input.reportHash(),
                                preHash,
                                settings,
                                candidateHash,
                                candidate)
                        : null;
        return new Result(artifact, report, game);
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
                } else if (a.signalProbabilityGivenHistory() != b.signalProbabilityGivenHistory()
                        || !a.firstMarginal().equals(b.firstMarginal())
                        || !a.secondMarginal().equals(b.secondMarginal())
                        || !branch.orElseThrow().after().equals(b.quality()))
                    throw new IllegalStateException("Assembled sequence-form case differs");
            }
        }
    }

    private static void identity(
            String schema,
            String expected,
            String status,
            boolean admission,
            String algorithm,
            String selection,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String... hashes) {
        if (!expected.equals(schema)
                || !"VALIDATION_ONLY".equals(status)
                || admission
                || !ALGORITHM.equals(algorithm)
                || !SELECTION.equals(selection))
            throw new IllegalArgumentException("Unsupported physical sequence-form identity");
        Objects.requireNonNull(binding, "binding");
        for (String hash : hashes)
            if (!hash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid sequence-form lineage hash");
    }

    private static void validatePredecessorKind(String kind) {
        if (!JOINT_PREDECESSOR.equals(kind) && !CFR_PREDECESSOR.equals(kind))
            throw new IllegalArgumentException("Unsupported sequence-form predecessor kind");
    }

    static String hash(Object value) throws Exception {
        return SixMaxHistoryPhysicalConditionalRefinement.hash(value);
    }

    public static void write(Path policyPath, Path reportPath, Result result) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(policyPath, reportPath));
        if (Files.exists(policyPath) || Files.exists(reportPath))
            throw new IllegalArgumentException("Sequence-form outputs must be new paths");
        byte[] report = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        byte[] policy =
                result.artifact().isPresent()
                        ? SixMaxTextureStudy.json(result.artifact().orElseThrow())
                                .getBytes(StandardCharsets.UTF_8)
                        : null;
        if (report.length > SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES
                || policy != null
                        && policy.length > SixMaxHistoryPhysicalStudy.MAX_CHECKPOINT_BYTES)
            throw new IllegalArgumentException("Sequence-form artifact byte cap exceeded");
        SixMaxRankTexturePayoffTable.writeBytes(
                reportPath, report, SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES);
        if (policy != null)
            SixMaxRankTexturePayoffTable.writeBytes(
                    policyPath, policy, SixMaxHistoryPhysicalStudy.MAX_CHECKPOINT_BYTES);
    }

    public static Result replay(
            Path policyPath, Path reportPath, SixMaxHistoryPhysicalStudy.Validated predecessor)
            throws Exception {
        return replay(policyPath, reportPath, input(predecessor));
    }

    public static Result replay(
            Path policyPath,
            Path reportPath,
            SixMaxHistoryPhysicalConditionalRefinement.Result predecessor)
            throws Exception {
        return replay(policyPath, reportPath, input(predecessor));
    }

    private static Result replay(Path policyPath, Path reportPath, Input input) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(policyPath, reportPath));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        reportPath, SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES),
                                Report.class);
        if (!saved.predecessorCheckpointHash().equals(input.rootCheckpointHash())
                || !saved.predecessorKind().equals(input.kind())
                || !saved.predecessorPolicyHash().equals(input.policyHash())
                || !saved.predecessorReportHash().equals(input.reportHash())
                || !saved.binding().equals(input.binding()))
            throw new IllegalArgumentException("Sequence-form predecessor differs");
        Artifact artifact = null;
        if (saved.accepted())
            artifact =
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(
                                            policyPath,
                                            SixMaxHistoryPhysicalStudy.MAX_CHECKPOINT_BYTES),
                                    Artifact.class);
        else if (Files.exists(policyPath))
            throw new IllegalArgumentException("Rejected sequence-form must not export policy");
        var expected = refine(input, saved.settings(), b -> {});
        if (!saved.equals(expected.report())
                || !Objects.equals(artifact, expected.artifact().orElse(null)))
            throw new IllegalArgumentException("Physical sequence-form exact replay differs");
        return expected;
    }
}
