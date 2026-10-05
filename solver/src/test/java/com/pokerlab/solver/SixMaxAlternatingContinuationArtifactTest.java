package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Checks committed evidence without repeating long CFR runs in CI. */
class SixMaxAlternatingContinuationArtifactTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void higherBudgetResumeStartsFromRetainedPolicyWithoutRepeatingJointTraining()
            throws Exception {
        var fresh =
                mapper.readValue(
                        Path.of("../docs/data/sixmax-alternating-seed-711.json").toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
        var resumed =
                mapper.readValue(
                        Path.of("../docs/data/sixmax-alternating-resumed-seed-711.json").toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
        assertEquals("RESUMED", resumed.executionMode());
        assertEquals("COMPLETED", resumed.executionStatus());
        assertEquals("VALIDATION_ONLY", resumed.publicationStatus());
        assertNull(resumed.freshTrainingSettings());
        assertNull(resumed.initialTraining());
        assertEquals(fresh.sourcePackHash(), resumed.sourcePackHash());
        assertEquals(fresh.sourceSpotHash(), resumed.sourceSpotHash());
        assertEquals(fresh.selectedHistories(), resumed.selectedHistories());
        assertEquals(fresh.cost(), resumed.cost());
        assertEquals(fresh.rounds().retainedAudit().solutionHash(), resumed.resumedSolutionHash());
        assertEquals(fresh.rounds().retainedAudit(), resumed.rounds().initialAudit());
        assertEquals(1000, resumed.settings().preflopIterations());
        assertEquals(300, resumed.settings().postflopIterations());
        assertEquals(1, resumed.settings().maximumRounds());
        assertEquals(1, resumed.rounds().acceptedRounds());
        assertEquals(
                307_974_000L,
                resumed.rounds()
                        .rounds()
                        .getFirst()
                        .preflopFeedback()
                        .preflopTraversal()
                        .visitedNodes());
        assertEquals(
                162_336_000L,
                resumed.rounds()
                        .rounds()
                        .getFirst()
                        .preflopFeedback()
                        .preflopTraversal()
                        .terminalNodes());
        checkRounds(resumed);
    }

    @Test
    void freshRunsReproducePriorPoliciesAndRetainOnlyPassingWholeRounds() throws Exception {
        var source =
                MultiwayPackJson.readFullRound(
                        Files.readString(Path.of("../docs/data/sixmax-diverse-source-pack.json")));
        for (int seed : new int[] {711, 712}) {
            var artifact =
                    mapper.readValue(
                            Path.of("../docs/data/sixmax-alternating-seed-" + seed + ".json")
                                    .toFile(),
                            SixMaxAlternatingContinuationStudyMain.Artifact.class);
            var priorArtifact =
                    mapper.readValue(
                            Path.of("../docs/data/sixmax-feedback-seed-" + seed + ".json").toFile(),
                            SixMaxContinuationFeedbackStudyMain.Artifact.class);
            var prior = priorArtifact.runs().getFirst();
            assertEquals("six-max-alternating-continuation-study/v1", artifact.schemaVersion());
            assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
            assertEquals("COMPLETED", artifact.executionStatus());
            assertEquals("FRESH", artifact.executionMode());
            assertEquals(MultiwayPackJson.fullRoundContentHash(source), artifact.sourcePackHash());
            assertEquals(source.spotHash(), artifact.sourceSpotHash());
            assertEquals(priorArtifact.selectedHistories(), artifact.selectedHistories());
            assertEquals(priorArtifact.budget(), artifact.budget());
            assertEquals(12, artifact.cost().compatibleDealFlops());
            assertEquals(1_358_137, artifact.cost().completeTreeStates());
            assertEquals(seed, artifact.initialTraining().seed());
            assertEquals(500, artifact.freshTrainingSettings().jointIterations());
            assertEquals(300, artifact.freshTrainingSettings().initialPostflopIterations());
            assertNull(artifact.resumedSolutionHash());
            assertEquals(prior.jointTraversal(), artifact.initialTraining().traversal());
            assertEquals(
                    prior.initialPostflopRefinement().originalSolutionHash(),
                    artifact.initialTraining().refinement().originalSolutionHash());
            assertEquals(
                    prior.initialPostflopRefinement().candidateSolutionHash(),
                    artifact.rounds().initialAudit().solutionHash());
            var first = artifact.rounds().rounds().getFirst();
            assertTrue(first.decision().accepted());
            assertEquals(prior.finalSolutionHash(), first.candidateAudit().solutionHash());
            assertEquals(
                    prior.finalMaximumConditionalGapBb(),
                    first.candidateAudit().maximumConditionalGapBb(),
                    1e-12);
            checkRounds(artifact);
        }
    }

    private static void checkRounds(SixMaxAlternatingContinuationStudyMain.Artifact artifact) {
        var report = artifact.rounds();
        var retained = report.initialAudit();
        int accepted = 0;
        assertTrue(retained.everySelectedBranchReached());
        assertTrue(
                retained.maximumConditionalGapBb() <= report.settings().conditionalGapTargetBb());
        for (int n = 0; n < report.rounds().size(); n++) {
            var round = report.rounds().get(n);
            assertEquals(n + 1, round.number());
            assertEquals(retained.solutionHash(), round.acceptedInputHash());
            assertEquals(retained.solutionHash(), round.preflopFeedback().originalSolutionHash());
            assertEquals(
                    round.preflopFeedback().candidateSolutionHash(),
                    round.postflopRefinement().originalSolutionHash());
            assertEquals(
                    round.postflopRefinement().candidateSolutionHash(),
                    round.candidateAudit().solutionHash());
            assertEquals(
                    round.postflopRefinement().candidateQuality(),
                    round.candidateAudit().parentQuality());
            assertEquals(round.preflopFeedback().candidateReach(), round.candidateAudit().reach());
            assertEquals(8305, round.preflopFeedback().replacedPreflopInformationSets());
            assertEquals(364480, round.preflopFeedback().preservedPostflopInformationSets());
            assertEquals(
                    round.postflopRefinement().bettingContinuationProbability(),
                    round.candidateAudit().reach().selectedPhysicalFlopProbability(),
                    1e-15);
            assertEquals(
                    round.postflopRefinement().branches().stream()
                            .filter(b -> b.after() != null)
                            .mapToDouble(b -> b.after().gap())
                            .max()
                            .orElse(0),
                    round.candidateAudit().maximumConditionalGapBb(),
                    1e-12);
            var expected =
                    SixMaxAlternatingQualityGate.assess(
                            retained.parentQuality().nashConvBb(),
                            round.candidateAudit().parentQuality().nashConvBb(),
                            round.candidateAudit().maximumConditionalGapBb(),
                            round.candidateAudit().everySelectedBranchReached(),
                            report.settings().conditionalGapTargetBb(),
                            report.settings().minimumParentImprovementBb());
            assertEquals(expected, round.decision());
            assertEquals(
                    round.candidateAudit().parentQuality().deviationGainsBb().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    round.candidateAudit().parentQuality().nashConvBb(),
                    1e-12);
            if (expected.accepted()) {
                retained = round.candidateAudit();
                accepted++;
            } else {
                assertEquals(report.rounds().size() - 1, n);
                assertEquals(expected.status(), report.stopReason());
            }
            assertEquals(retained.solutionHash(), round.retainedSolutionHash());
        }
        assertEquals(accepted, report.acceptedRounds());
        assertEquals(retained, report.retainedAudit());
        assertFalse(report.rounds().isEmpty());
        assertTrue(report.rounds().size() <= report.settings().maximumRounds());
        if (accepted == report.rounds().size()) {
            assertEquals(report.settings().maximumRounds(), accepted);
            assertEquals("ROUND_LIMIT_REACHED", report.stopReason());
        }
    }
}
