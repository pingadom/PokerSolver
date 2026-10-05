package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the published evidence without repeating the expensive training runs in CI. */
class SixMaxContinuationFeedbackArtifactTest {
    @Test
    void publishedStudiesRetainSourceProvenanceAndReproduceThePriorJointPolicies()
            throws Exception {
        var mapper = new ObjectMapper();
        var pack =
                MultiwayPackJson.readFullRound(
                        Files.readString(Path.of("../docs/data/sixmax-diverse-source-pack.json")));
        for (int seed : List.of(711, 712)) {
            var artifact =
                    mapper.readValue(
                            Path.of("../docs/data/sixmax-feedback-seed-" + seed + ".json").toFile(),
                            SixMaxContinuationFeedbackStudyMain.Artifact.class);
            var prior =
                    mapper.readValue(
                            Path.of("../docs/data/sixmax-flop-width-seed-" + seed + ".json")
                                    .toFile(),
                            SixMaxFlopWidthStudyMain.Artifact.class);
            assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
            assertEquals(pack.spotHash(), artifact.sourceSpotHash());
            assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
            assertEquals("COMPLETED", artifact.executionStatus());
            assertEquals(prior.widths().getFirst().cost(), artifact.cost());
            assertEquals(
                    prior.widths().getFirst().selectedHistories(), artifact.selectedHistories());
            assertEquals(List.of((long) seed), artifact.trainingSeeds());
            assertEquals(500, artifact.jointIterations());
            assertEquals(300, artifact.initialPostflopIterations());
            assertEquals(500, artifact.preflopIterations());
            assertEquals(300, artifact.finalPostflopIterations());
            var run = artifact.runs().getFirst();
            var precedingRefinement =
                    prior.widths()
                            .getFirst()
                            .runs()
                            .getFirst()
                            .refinementAttempts()
                            .getFirst()
                            .refinement();
            assertEquals(
                    precedingRefinement.originalSolutionHash(),
                    run.initialPostflopRefinement().originalSolutionHash());
            assertEquals(
                    precedingRefinement.candidateSolutionHash(),
                    run.initialPostflopRefinement().candidateSolutionHash());
            assertEquals(
                    run.initialPostflopRefinement().originalSolutionHash(),
                    run.unrefinedContinuationControl().originalSolutionHash());
            assertEquals(
                    run.initialPostflopRefinement().candidateSolutionHash(),
                    run.preflopFeedback().originalSolutionHash());
            assertEquals(
                    run.preflopFeedback().candidateSolutionHash(),
                    run.finalPostflopRefinement().originalSolutionHash());
            assertEquals(
                    run.finalPostflopRefinement().candidateSolutionHash(), run.finalSolutionHash());
        }
    }

    @Test
    void publishedScoresAndFlagsAgreeWithTheirComponentAudits() throws Exception {
        var mapper = new ObjectMapper();
        for (int seed : List.of(711, 712)) {
            var a =
                    mapper.readValue(
                            Path.of("../docs/data/sixmax-feedback-seed-" + seed + ".json").toFile(),
                            SixMaxContinuationFeedbackStudyMain.Artifact.class);
            var run = a.runs().getFirst();
            for (var report : List.of(run.unrefinedContinuationControl(), run.preflopFeedback())) {
                assertEquals(8305, report.replacedPreflopInformationSets());
                assertEquals(364480, report.preservedPostflopInformationSets());
                assertEquals("CFR_PLUS", report.preflopAlgorithm());
                assertEquals("EXHAUSTIVE", report.preflopChanceTraversal());
                assertEquals(0, report.preflopTraversal().sampledChanceNodes());
                assertEquals(8, report.frozenTerminalValues().size());
                var identities = new HashSet<SixMaxPreflopCheckdownGame.State>();
                for (var value : report.frozenTerminalValues()) {
                    assertTrue(
                            identities.add(
                                    new SixMaxPreflopCheckdownGame.State(
                                            value.dealIndex(), value.publicHistory())));
                    assertTrue(value.utilitiesBb().stream().allMatch(Double::isFinite));
                    assertEquals(
                            0,
                            value.utilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                            1e-8);
                    for (int player = 0; player < 6; player++)
                        assertEquals(
                                value.utilitiesBb().get(player)
                                        - value.sourceCheckdownUtilitiesBb().get(player),
                                value.continuationMinusCheckdownBb().get(player),
                                1e-12);
                }
                for (var pair :
                        List.of(
                                List.of(
                                        report.originalParentQuality(),
                                        report.originalProjectedQuality()),
                                List.of(
                                        report.candidateParentQuality(),
                                        report.candidateProjectedQuality()))) {
                    var parent = pair.getFirst();
                    var projected = pair.getLast();
                    for (int player = 0; player < 6; player++) {
                        assertEquals(
                                parent.profileUtilitiesBb().get(player),
                                projected.profileUtilitiesBb().get(player),
                                1e-9);
                        assertTrue(
                                projected.bestResponseUtilitiesBb().get(player)
                                        <= parent.bestResponseUtilitiesBb().get(player) + 1e-9);
                    }
                    assertEquals(
                            parent.deviationGainsBb().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .sum(),
                            parent.nashConvBb(),
                            1e-12);
                }
                assertEquals(
                        report.candidateParentQuality().nashConvBb()
                                - report.originalParentQuality().nashConvBb(),
                        report.parentNashConvChangeBb(),
                        1e-12);
                assertEquals(
                        report.parentNashConvChangeBb() <= a.parentNashConvToleranceBb(),
                        report.parentNashConvDidNotIncrease());
            }
            assertEquals(
                    run.unrefinedContinuationControl().preflopTraversal(),
                    run.preflopFeedback().preflopTraversal());
            assertEquals(
                    run.unrefinedContinuationControl().originalReach(),
                    run.preflopFeedback().originalReach());
            var finalReport = run.finalPostflopRefinement();
            assertEquals(
                    run.preflopFeedback().candidateReach().selectedPhysicalFlopProbability(),
                    finalReport.bettingContinuationProbability(),
                    1e-15);
            assertEquals(
                    finalReport.branches().stream()
                            .filter(b -> b.after() != null)
                            .mapToDouble(b -> b.after().gap())
                            .max()
                            .orElse(0),
                    run.finalMaximumConditionalGapBb(),
                    1e-12);
            assertEquals(
                    finalReport.branches().stream().allMatch(b -> b.status().equals("REFINED")),
                    run.everySelectedBranchReached());
            assertEquals(
                    run.everySelectedBranchReached()
                            && run.finalMaximumConditionalGapBb() <= a.conditionalGapTargetBb(),
                    run.finalConditionalGapWithinTarget());
            assertEquals(
                    finalReport.candidateQuality().nashConvBb()
                            - run.initialPostflopRefinement().originalQuality().nashConvBb(),
                    run.finalParentNashConvChangeFromJointBb(),
                    1e-12);
            assertEquals(
                    run.finalParentNashConvChangeFromJointBb() <= a.parentNashConvToleranceBb(),
                    run.finalParentNashConvDidNotIncreaseFromJoint());
        }
    }
}
