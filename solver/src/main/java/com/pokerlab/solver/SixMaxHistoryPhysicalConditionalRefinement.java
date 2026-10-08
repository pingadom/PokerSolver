package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuitConditionalRefinement.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Physical-board derivative, never extra joint iterations or trainer admission. */
public final class SixMaxHistoryPhysicalConditionalRefinement {
    public static final String ALGORITHM = SixMaxSuitConditionalRefinement.ALGORITHM;
    public static final String ARTIFACT_SCHEMA = "six-max-history-physical-derived-policy/v1";
    public static final String REPORT_SCHEMA = "six-max-history-physical-conditional-refinement/v1";
    public static final String SELECTION = "PHYSICAL_MATERIAL_GAP_AND_REACH/v1";
    public static final String ACCURATE_SELECTION =
            "PRIMARY_ACCURATE_PHYSICAL_MATERIAL_GAP_AND_REACH/v1";

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String algorithm,
            String selection,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String predecessorCheckpointHash,
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
                    predecessorReportHash,
                    frozenPreflopHash,
                    solutionHash);
            validateSettings(settings);
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
                    predecessorReportHash,
                    frozenPreflopHash,
                    candidateSolutionHash);
            validateSettings(settings);
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
                throw new IllegalArgumentException("Invalid physical refinement counts");
        }
    }

    /** Only complete optimization or exact replay can produce an accepted exportable result. */
    public static final class Result {
        private final Artifact artifact;
        private final Report report;
        private final SixMaxHistoryPhysicalStudy.Validated predecessor;

        private Result(
                Artifact artifact,
                Report report,
                SixMaxHistoryPhysicalStudy.Validated predecessor) {
            this.artifact = artifact;
            this.report = report;
            this.predecessor = predecessor;
        }

        public Optional<Artifact> artifact() {
            return Optional.ofNullable(artifact);
        }

        public Report report() {
            return report;
        }

        SixMaxOneBetFlopGame core() {
            return predecessor.core();
        }
    }

    private SixMaxHistoryPhysicalConditionalRefinement() {}

    public static Result refine(
            SixMaxHistoryPhysicalStudy.Validated predecessor,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        return refine(predecessor, settings, false, progress);
    }

    /** The accurate subset explicitly excludes weak primary cases; it never certifies them. */
    public static Result refine(
            SixMaxHistoryPhysicalStudy.Validated predecessor,
            Settings settings,
            boolean primaryAccurateOnly,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(predecessor, "predecessor");
        Objects.requireNonNull(progress, "progress");
        validateSettings(settings);
        var cp = predecessor.checkpoint();
        var before = predecessor.report().jointlySolvedDiagnostics();
        var game = predecessor.core();
        // Include already accurate material cases: root accuracy alone does not ensure stable EVs.
        var selected =
                SixMaxConditionalRefinementEngine.select(
                        before,
                        settings,
                        c ->
                                game.payoffView().key(c.signal().observation()).startsWith("board:")
                                        && c.history().historyProbability()
                                                >= SixMaxSuitDecisionStability.MIN_HISTORY_REACH
                                        && c.signal().firstCombosAtFivePercent() >= 2
                                        && c.signal().secondCombosAtFivePercent() >= 2
                                        && (!primaryAccurateOnly
                                                || c.signal().quality().nashConvBb()
                                                        <= SixMaxSuitDecisionStability
                                                                .LOCAL_GAP_BB),
                        true);
        var computed =
                SixMaxConditionalRefinementEngine.refine(
                        game, cp.solution(), before, selected, settings, progress);
        String cpHash = hash(cp),
                reportHash = hash(predecessor.report()),
                preHash =
                        SixMaxConnectedPostflopAudit.solutionHash(
                                SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution())),
                solutionHash = SixMaxConnectedPostflopAudit.solutionHash(computed.candidate());
        String selection = primaryAccurateOnly ? ACCURATE_SELECTION : SELECTION;
        var report =
                new Report(
                        REPORT_SCHEMA,
                        "VALIDATION_ONLY",
                        false,
                        ALGORITHM,
                        selection,
                        cp.binding(),
                        cpHash,
                        reportHash,
                        preHash,
                        settings,
                        solutionHash,
                        computed.reasons().isEmpty(),
                        computed.reasons(),
                        cp.solution().strategy().size(),
                        computed.changedKeys().size(),
                        cp.solution().strategy().size() - computed.changedKeys().size(),
                        computed.branches(),
                        before,
                        computed.after());
        var artifact =
                report.accepted()
                        ? new Artifact(
                                ARTIFACT_SCHEMA,
                                "VALIDATION_ONLY",
                                false,
                                ALGORITHM,
                                selection,
                                cp.binding(),
                                cpHash,
                                reportHash,
                                preHash,
                                settings,
                                solutionHash,
                                computed.candidate())
                        : null;
        return new Result(artifact, report, predecessor);
    }

    private static void validateSettings(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        if (settings.priority() != Priority.BALANCED_GAP_AND_REACH)
            throw new IllegalArgumentException(
                    "Physical refinement requires balanced gap/reach priority");
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
                || !(SELECTION.equals(selection) || ACCURATE_SELECTION.equals(selection)))
            throw new IllegalArgumentException("Unsupported physical derivative identity");
        Objects.requireNonNull(binding, "binding");
        for (String hash : hashes)
            if (!hash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid physical derivative hash");
    }

    static String hash(Object value) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(value)));
    }

    public static void write(Path policyPath, Path reportPath, Result result) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(policyPath, reportPath));
        if (Files.exists(policyPath) || Files.exists(reportPath))
            throw new IllegalArgumentException("Physical derivative outputs must be new paths");
        byte[] report = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        byte[] policy =
                result.artifact().isPresent()
                        ? SixMaxTextureStudy.json(result.artifact().orElseThrow())
                                .getBytes(StandardCharsets.UTF_8)
                        : null;
        if (report.length > SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES
                || policy != null
                        && policy.length > SixMaxHistoryPhysicalStudy.MAX_CHECKPOINT_BYTES)
            throw new IllegalArgumentException("Physical derivative byte cap exceeded");
        SixMaxRankTexturePayoffTable.writeBytes(
                reportPath, report, SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES);
        if (policy != null)
            SixMaxRankTexturePayoffTable.writeBytes(
                    policyPath, policy, SixMaxHistoryPhysicalStudy.MAX_CHECKPOINT_BYTES);
    }

    public static Result replay(
            Path policyPath, Path reportPath, SixMaxHistoryPhysicalStudy.Validated predecessor)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(policyPath, reportPath));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        reportPath, SixMaxHistoryPhysicalStudy.MAX_REPORT_BYTES),
                                Report.class);
        if (!saved.predecessorCheckpointHash().equals(hash(predecessor.checkpoint()))
                || !saved.predecessorReportHash().equals(hash(predecessor.report()))
                || !saved.binding().equals(predecessor.checkpoint().binding()))
            throw new IllegalArgumentException("Physical derivative predecessor differs");
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
            throw new IllegalArgumentException("Rejected derivative must not export a policy");
        var expected =
                refine(
                        predecessor,
                        saved.settings(),
                        ACCURATE_SELECTION.equals(saved.selection()),
                        b -> {});
        if (!saved.equals(expected.report())
                || !Objects.equals(artifact, expected.artifact().orElse(null)))
            throw new IllegalArgumentException("Physical derivative exact replay differs");
        return expected;
    }
}
