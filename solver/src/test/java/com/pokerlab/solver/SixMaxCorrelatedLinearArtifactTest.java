package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Reconstructs provenance and gate decisions for higher-budget resumed trials. */
class SixMaxCorrelatedLinearArtifactTest {
    static SixMaxAlternatingContinuationStudyMain.Artifact read(int seed) throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of(
                                        "../docs/data/sixmax-correlated-linear-alternating-seed-"
                                                + seed
                                                + ".json")
                                .toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }

    @Test
    void resumedLinearTrialsBindToTheUnchangedRejectedControls() throws Exception {
        for (int seed : new int[] {711, 712}) {
            var control = SixMaxCorrelatedContinuationArtifactTest.read(seed);
            var report = read(seed);
            assertEquals("six-max-alternating-continuation-study/v4", report.schemaVersion());
            assertEquals("VALIDATION_ONLY", report.publicationStatus());
            assertEquals("COMPLETED", report.executionStatus());
            assertEquals("RESUMED", report.executionMode());
            assertNull(report.freshTrainingSettings());
            assertNull(report.initialTraining());
            assertEquals(
                    control.rounds().retainedAudit().solutionHash(), report.resumedSolutionHash());
            assertEquals(
                    report.resumedSolutionHash(), report.rounds().initialAudit().solutionHash());
            assertEquals(
                    control.rounds().retainedAudit().parentQuality().nashConvBb(),
                    report.rounds().initialAudit().parentQuality().nashConvBb(),
                    1e-12);
            assertEquals(control.sourcePackHash(), report.sourcePackHash());
            assertEquals(control.sourceSpotHash(), report.sourceSpotHash());
            assertEquals(control.budget(), report.budget());
            assertEquals(control.cost(), report.cost());
            assertEquals(control.selectedHistories(), report.selectedHistories());
            assertEquals(control.selectionAudit(), report.selectionAudit());
            assertEquals(control.sourcePrivateSupport(), report.sourcePrivateSupport());
            assertEquals(control.sourcePrivateCorrelation(), report.sourcePrivateCorrelation());
            assertEquals(
                    new SixMaxAlternatingContinuationSolver.Settings(
                            1,
                            1000,
                            300,
                            .05,
                            .000001,
                            SixMaxPreflopContinuationFeedback.Algorithm.LINEAR_VANILLA),
                    report.settings());
            assertEquals(
                    report.rounds().retainedAudit().solutionHash(),
                    report.retainedPrivateSupport().solutionHash());
            assertEquals(
                    control.sourcePrivateSupport().sourceMarginals(),
                    report.retainedPrivateSupport().sourceMarginals());
        }
    }

    @Test
    void resumedGateAndTraversalAreMeasuredAtTheDeclaredHigherBudget() throws Exception {
        for (int seed : new int[] {711, 712}) {
            var report = read(seed);
            var rounds = report.rounds();
            assertEquals(1, rounds.rounds().size());
            var round = rounds.rounds().getFirst();
            var feedback = round.preflopFeedback();
            assertEquals("LINEAR_VANILLA", feedback.preflopAlgorithm());
            assertEquals("EXHAUSTIVE", feedback.preflopChanceTraversal());
            assertEquals(923_910_000L, feedback.preflopTraversal().visitedNodes());
            assertEquals(487_008_000L, feedback.preflopTraversal().terminalNodes());
            assertEquals(0, feedback.preflopTraversal().sampledChanceNodes());
            assertEquals(10_172, feedback.replacedPreflopInformationSets());
            assertEquals(24, feedback.frozenTerminalValues().size());
            for (var history : report.selectedHistories()) {
                assertEquals(
                        IntStream.range(0, 12).boxed().toList(),
                        feedback.frozenTerminalValues().stream()
                                .filter(
                                        v ->
                                                v.publicHistory()
                                                        .equals(history.coverage().publicHistory()))
                                .map(SixMaxFrozenContinuationPreflopGame.TerminalValue::dealIndex)
                                .sorted()
                                .toList());
            }
            for (var value : feedback.frozenTerminalValues()) {
                assertEquals(6, value.utilitiesBb().size());
                assertEquals(
                        0,
                        value.utilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                        1e-9);
            }
            assertEquals(report.resumedSolutionHash(), round.acceptedInputHash());
            assertEquals(round.acceptedInputHash(), feedback.originalSolutionHash());
            var decision =
                    SixMaxAlternatingQualityGate.assess(
                            rounds.initialAudit().parentQuality().nashConvBb(),
                            round.candidateAudit().parentQuality().nashConvBb(),
                            round.candidateAudit().maximumConditionalGapBb(),
                            round.candidateAudit().everySelectedBranchReached(),
                            .05,
                            .000001);
            assertEquals(decision, round.decision());
            assertTrue(decision.accepted());
            assertEquals("ACCEPTED", decision.status());
            assertEquals("ROUND_LIMIT_REACHED", rounds.stopReason());
            assertEquals(decision.accepted() ? 1 : 0, rounds.acceptedRounds());
            assertEquals(
                    decision.accepted() ? round.candidateAudit() : rounds.initialAudit(),
                    rounds.retainedAudit());
            assertEquals(rounds.retainedAudit().solutionHash(), round.retainedSolutionHash());
            assertTrue(rounds.retainedAudit().everySelectedBranchReached());
            assertTrue(rounds.retainedAudit().maximumConditionalGapBb() <= .05);
            for (var audit :
                    new SixMaxAlternatingContinuationSolver.Audit[] {
                        rounds.initialAudit(), round.candidateAudit(), rounds.retainedAudit()
                    }) {
                var quality = audit.parentQuality();
                assertEquals(6, quality.deviationGainsBb().size());
                assertEquals(
                        quality.nashConvBb(),
                        quality.deviationGainsBb().stream().mapToDouble(Double::doubleValue).sum(),
                        1e-9);
                assertEquals(
                        0,
                        quality.profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-9);
            }
            for (int i = 0; i < report.sourcePrivateSupport().boards().size(); i++) {
                var original = report.sourcePrivateSupport().boards().get(i);
                var retained = report.retainedPrivateSupport().boards().get(i);
                assertEquals(
                        original.counterfactualJointDeals(), retained.counterfactualJointDeals());
                assertEquals(
                        original.counterfactualMarginals(), retained.counterfactualMarginals());
                assertTrue(retained.reachedJointDeals() > 0);
                for (var marginal : retained.reachedMarginals().values())
                    assertEquals(
                            1,
                            marginal.values().stream().mapToDouble(Double::doubleValue).sum(),
                            1e-12);
            }
        }
    }
}
