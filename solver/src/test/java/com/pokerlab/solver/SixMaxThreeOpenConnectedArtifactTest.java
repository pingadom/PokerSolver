package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Source-bound evidence checks without depending on ignored multi-megabyte policy checkpoints. */
class SixMaxThreeOpenConnectedArtifactTest {
    static SixMaxAlternatingContinuationStudyMain.Artifact read(int seed) throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of("../docs/data/sixmax-three-open-alternating-seed-" + seed + ".json")
                                .toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }

    @Test
    void freshTrialsKeepTheSameCompleteGameAndReproduceWholeRoundGateDecisions() throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack("three-open");
        var search =
                SixMaxPreflopPayoffReuseArtifactTest.report("three-open", "single-history-search")
                        .targetMenuSearch();
        var game =
                new SixMaxConnectedPreflopGame(source.rebuildGame(), search.proposedSelections());
        for (int seed : new int[] {711, 712}) {
            var trial = read(seed);
            assertEquals("six-max-alternating-continuation-study/v5", trial.schemaVersion());
            assertEquals("VALIDATION_ONLY", trial.publicationStatus());
            assertEquals("COMPLETED", trial.executionStatus());
            assertEquals("FRESH", trial.executionMode());
            assertNull(trial.resumedSolutionHash());
            assertEquals(MultiwayPackJson.fullRoundContentHash(source), trial.sourcePackHash());
            assertEquals(source.spotHash(), trial.sourceSpotHash());
            assertEquals(
                    new SixMaxAlternatingContinuationStudyMain.FreshTrainingSettings(
                            seed, 500, 300),
                    trial.freshTrainingSettings());
            assertEquals(
                    new SixMaxAlternatingContinuationSolver.Settings(
                            1,
                            1000,
                            300,
                            .05,
                            .000001,
                            SixMaxPreflopContinuationFeedback.Algorithm.LINEAR_VANILLA),
                    trial.settings());
            assertEquals(new SixMaxContinuationStudyBudget.Cost(12, 1_323_133), trial.cost());
            assertEquals(SixMaxContinuationStudyBudget.widerFlops(), trial.budget());
            assertEquals(
                    game.coverage().getFirst(), trial.selectedHistories().getFirst().coverage());
            assertEquals(
                    SixMaxRetainedContinuationCoverage.assess(
                            game,
                            source.solution(),
                            SixMaxRetainedContinuationCoverage.Settings.researchDefault()),
                    trial.sourceContentCoverage());
            assertTrue(trial.sourceContentCoverage().criteriaMet());
            var round = trial.rounds().rounds().getFirst();
            var candidate = round.candidateAudit();
            assertTrue(trial.rounds().initialAudit().maximumConditionalGapBb() < .05);
            assertTrue(
                    trial.rounds().rounds().getFirst().maximumConditionalGapAfterPreflopBb() > 4);
            assertEquals(126_200, round.postflopRefinement().replacedInformationSets());
            assertEquals(
                    SixMaxAlternatingQualityGate.assess(
                            trial.rounds().initialAudit().parentQuality().nashConvBb(),
                            candidate.parentQuality().nashConvBb(),
                            candidate.maximumConditionalGapBb(),
                            candidate.everySelectedBranchReached(),
                            .05,
                            .000001),
                    round.decision());
            assertEquals(round.decision().accepted() ? 1 : 0, trial.rounds().acceptedRounds());
            var retained = trial.rounds().retainedAudit();
            assertTrue(round.decision().accepted());
            assertEquals(1, trial.rounds().acceptedRounds());
            assertTrue(retained.parentQuality().nashConvBb() < .0001);
            assertTrue(retained.maximumConditionalGapBb() < .02);
            assertTrue(trial.retainedContentCoverage().criteriaMet());
            assertEquals(retained.solutionHash(), round.retainedSolutionHash());
            assertEquals(retained.solutionHash(), trial.retainedContentCoverage().solutionHash());
            assertEquals(retained.solutionHash(), trial.retainedPrivateSupport().solutionHash());
            assertEquals(retained.reach(), trial.retainedContentCoverage().reach());
            assertEquals(12, trial.retainedPrivateSupport().sourceJointDeals());
            var board = trial.retainedPrivateSupport().boards().getFirst();
            assertEquals(12, board.counterfactualJointDeals());
            assertEquals(List.of(Seat.HJ, Seat.BTN), board.uncertainFoldedSeats());
            var content =
                    trial.retainedContentCoverage().histories().getFirst().boards().getFirst();
            assertEquals(12, content.reachedJointDeals());
            assertEquals(2, content.first().materialReachedCombos());
            assertEquals(2, content.second().materialReachedCombos());
            assertEquals(board.reachedMarginals().get(Seat.BB), content.first().reachedMarginal());
            assertEquals(board.reachedMarginals().get(Seat.CO), content.second().reachedMarginal());
            var feedback = round.preflopFeedback();
            assertEquals("LINEAR_VANILLA", feedback.preflopAlgorithm());
            assertEquals("EXHAUSTIVE", feedback.preflopChanceTraversal());
            assertEquals(97_926_000L, feedback.preflopTraversal().visitedNodes());
            assertEquals(50_112_000L, feedback.preflopTraversal().terminalNodes());
            assertEquals(
                    source.solution().strategy().size(), feedback.replacedPreflopInformationSets());
            assertEquals(
                    IntStream.range(0, 12).boxed().toList(),
                    feedback.frozenTerminalValues().stream()
                            .map(SixMaxFrozenContinuationPreflopGame.TerminalValue::dealIndex)
                            .sorted()
                            .toList());
            for (var value : feedback.frozenTerminalValues()) {
                assertEquals(6, value.utilitiesBb().size());
                assertEquals(
                        0,
                        value.utilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                        1e-9);
            }
        }
    }

    @Test
    void independentCheckpointComparisonBindsTheRetainedPoliciesQualityAndContent()
            throws Exception {
        var report =
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-three-open-policy-stability.json")
                                        .toFile(),
                                SixMaxConnectedPolicyStabilityMain.Artifact.class);
        var first = read(711);
        var second = read(712);
        assertEquals("six-max-connected-policy-stability/v1", report.schemaVersion());
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals(first.sourcePackHash(), report.sourcePackHash());
        assertEquals(second.sourceSpotHash(), report.sourceSpotHash());
        assertEquals(first.budget(), report.budget());
        assertEquals(
                first.rounds().retainedAudit().solutionHash(),
                report.comparison().firstSolutionHash());
        assertEquals(
                second.rounds().retainedAudit().solutionHash(),
                report.comparison().secondSolutionHash());
        assertEquals(first.retainedContentCoverage(), report.firstContent());
        assertEquals(second.retainedContentCoverage(), report.secondContent());
        assertEquals(
                first.rounds().retainedAudit().parentQuality().nashConvBb(),
                report.firstQuality().parentQuality().nashConvBb(),
                1e-12);
        assertEquals(
                second.rounds().retainedAudit().maximumConditionalGapBb(),
                report.secondQuality().maximumConditionalGapBb(),
                1e-12);
        assertEquals(
                List.of("PREFLOP", "POSTFLOP"),
                report.comparison().stages().stream()
                        .map(SixMaxConnectedPolicyStability.Stage::stage)
                        .toList());
        assertTrue(
                report.comparison().visitedStates() <= report.budget().maximumCompleteTreeStates());
        for (var stage : report.comparison().stages()) {
            assertTrue(stage.informationSets() > 0);
            assertTrue(stage.reachedByEitherPolicy() > 0);
            assertTrue(stage.firstExpectedDecisionEncounters() > 0);
            assertTrue(stage.secondExpectedDecisionEncounters() > 0);
            assertNotNull(stage.reachWeightedTotalVariation());
            assertTrue(
                    stage.reachWeightedTotalVariation() >= 0
                            && stage.reachWeightedTotalVariation() <= 1);
            assertTrue(
                    stage.uniformMeanTotalVariation() >= 0
                            && stage.uniformMeanTotalVariation() <= 1);
            assertTrue(stage.maximumTotalVariation() >= stage.uniformMeanTotalVariation());
        }
    }
}
