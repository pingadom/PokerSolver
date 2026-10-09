package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuitDecisionStability.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Model-bound decision screen; bounded passing samples do not admit training content. */
public final class SixMaxHistoryPhysicalSequenceFormDecisionStability {
    public static final String SCHEMA =
            "six-max-history-physical-sequence-form-decision-stability/v1";
    public static final String COMPARISON_SCOPE =
            "FIXED_PRIMARY_QUESTION_POSTERIOR_POLICY_TRANSFER/v1";

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String posteriorScope,
            String comparisonScope,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String derivedArtifactHash,
            String derivedReportHash,
            String predecessorCheckpointHash,
            String solutionHash,
            String frozenPreflopHash,
            Settings settings,
            Map<String, Integer> eligibilityCounts,
            double selectedHistoryReach,
            double eligiblePhysicalReach,
            double retainedPhysicalReach,
            double allHeadsUpReach,
            double retainedAllHeadsUpFraction,
            List<Branch> branches) {
        public Report {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || trainerAdmission
                    || !SixMaxOneBetDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxOneBetDecisionValues.POSTERIOR_SCOPE.equals(posteriorScope)
                    || !COMPARISON_SCOPE.equals(comparisonScope))
                throw new IllegalArgumentException("Unsupported history physical decision screen");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(settings, "settings");
            for (String hash :
                    List.of(
                            derivedArtifactHash,
                            derivedReportHash,
                            predecessorCheckpointHash,
                            solutionHash,
                            frozenPreflopHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid decision screen hash");
            eligibilityCounts = Map.copyOf(eligibilityCounts);
            branches = List.copyOf(branches);
            for (double reach :
                    List.of(
                            selectedHistoryReach,
                            eligiblePhysicalReach,
                            retainedPhysicalReach,
                            allHeadsUpReach,
                            retainedAllHeadsUpFraction))
                if (!Double.isFinite(reach) || reach < 0 || reach > 1 + 1e-12)
                    throw new IllegalArgumentException("Invalid decision screen reach");
            if (branches.size() > settings.maximumCases()
                    || retainedPhysicalReach > eligiblePhysicalReach + 1e-12
                    || eligiblePhysicalReach > selectedHistoryReach + 1e-12
                    || selectedHistoryReach > allHeadsUpReach + 1e-12
                    || Math.abs(
                                    retainedAllHeadsUpFraction
                                            - (allHeadsUpReach == 0
                                                    ? 0
                                                    : retainedPhysicalReach / allHeadsUpReach))
                            > 1e-12)
                throw new IllegalArgumentException("Inconsistent decision screen coverage");
        }
    }

    private SixMaxHistoryPhysicalSequenceFormDecisionStability() {}

    public static Report screen(
            SixMaxHistoryPhysicalSequenceForm.Result validated,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        var artifact =
                validated
                        .artifact()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Accepted physical derivative required"));
        var computed =
                SixMaxDecisionStabilityEngine.screen(
                        validated.core(),
                        artifact.solution(),
                        validated.report().after(),
                        settings,
                        progress);
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(artifact.solution());
        double allHu =
                SixMaxMaterialContinuationFeasibility.assess(
                                validated.core().sourceGame(),
                                pre,
                                SixMaxMaterialContinuationFeasibility.Settings.researchDefault())
                        .headsUpProbability();
        return new Report(
                SCHEMA,
                "VALIDATION_ONLY",
                false,
                SixMaxOneBetDecisionValues.EV_SCOPE,
                SixMaxOneBetDecisionValues.POSTERIOR_SCOPE,
                COMPARISON_SCOPE,
                artifact.binding(),
                SixMaxHistoryPhysicalConditionalRefinement.hash(artifact),
                SixMaxHistoryPhysicalConditionalRefinement.hash(validated.report()),
                artifact.predecessorCheckpointHash(),
                artifact.solutionHash(),
                artifact.frozenPreflopHash(),
                settings,
                computed.counts(),
                computed.historyReach(),
                computed.eligibleReach(),
                computed.retainedReach(),
                allHu,
                allHu == 0 ? 0 : computed.retainedReach() / allHu,
                computed.branches());
    }

    public static void write(Path path, Report report) throws Exception {
        if (java.nio.file.Files.exists(path))
            throw new IllegalArgumentException("Decision screen output must be a new path");
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8),
                SixMaxSuitDecisionStability.MAX_REPORT_BYTES);
    }

    public static Report replay(Path path, SixMaxHistoryPhysicalSequenceForm.Result validated)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        path, SixMaxSuitDecisionStability.MAX_REPORT_BYTES),
                                Report.class);
        if (!saved.derivedArtifactHash()
                        .equals(
                                SixMaxHistoryPhysicalConditionalRefinement.hash(
                                        validated.artifact().orElseThrow()))
                || !saved.derivedReportHash()
                        .equals(
                                SixMaxHistoryPhysicalConditionalRefinement.hash(
                                        validated.report())))
            throw new IllegalArgumentException("Decision screen derivative lineage differs");
        var expected = screen(validated, saved.settings(), b -> {});
        if (!saved.equals(expected))
            throw new IllegalArgumentException("History physical decision screen replay differs");
        return expected;
    }
}
