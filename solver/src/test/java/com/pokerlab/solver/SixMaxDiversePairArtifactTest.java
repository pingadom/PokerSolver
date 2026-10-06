package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Independently reconstructs the published menu and gate decisions, without long CFR reruns. */
class SixMaxDiversePairArtifactTest {
    static SixMaxAlternatingContinuationStudyMain.Artifact read(int seed) throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of(
                                        "../docs/data/sixmax-diverse-pair-alternating-seed-"
                                                + seed
                                                + ".json")
                                .toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }

    @Test
    void reportsBindDifferentActivePairsToTheExactSourceAndBoardConditionedBeliefs()
            throws Exception {
        var source = SixMaxReachedContinuationStudyTest.eightDealSource();
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var plan =
                SixMaxReachedContinuationStudy.select(
                        source.rebuildGame(),
                        source.solution(),
                        2,
                        1,
                        711,
                        budget,
                        SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05));
        var privateSupport = SixMaxPrivateSupportAudit.assess(plan.game(), source.solution());
        for (int seed : new int[] {711, 712}) {
            var report = read(seed);
            assertEquals("six-max-alternating-continuation-study/v3", report.schemaVersion());
            assertNull(report.sourcePrivateCorrelation());
            assertEquals("VALIDATION_ONLY", report.publicationStatus());
            assertEquals("COMPLETED", report.executionStatus());
            assertEquals("FRESH", report.executionMode());
            assertEquals(MultiwayPackJson.fullRoundContentHash(source), report.sourcePackHash());
            assertEquals(source.spotHash(), report.sourceSpotHash());
            assertEquals(budget, report.budget());
            assertEquals(budget.validate(plan.game()), report.cost());
            assertEquals(plan.selectedHistories(), report.selectedHistories());
            assertEquals(plan.selectionAudit(), report.selectionAudit());
            assertEquals(privateSupport, report.sourcePrivateSupport());
            assertEquals(seed, report.initialTraining().seed());
            assertEquals(500, report.initialTraining().jointIterations());
            assertEquals(300, report.initialTraining().initialPostflopIterations());
            assertEquals(
                    234_256,
                    report.initialTraining().visitedInformationSets()
                            + report.initialTraining().uniformlyCompletedInformationSets());
            var retained = report.retainedPrivateSupport();
            assertEquals(report.rounds().retainedAudit().solutionHash(), retained.solutionHash());
            assertEquals(8, retained.sourceJointDeals());
            assertEquals(privateSupport.sourceMarginals(), retained.sourceMarginals());
            assertEquals(2, retained.boards().size());
            assertEquals(
                    List.of(8, 4),
                    retained.boards().stream()
                            .map(SixMaxPrivateSupportAudit.BoardSupport::counterfactualJointDeals)
                            .toList());
            assertEquals(2, retained.boards().get(1).firstPlayerRootInformationSets());
            assertEquals(2, retained.boards().get(1).secondPlayerRootInformationSets());
            assertEquals(1, retained.boards().get(1).counterfactualMarginals().get(Seat.BB).size());
            double reach = 0;
            for (int i = 0; i < 2; i++) {
                var before = privateSupport.boards().get(i);
                var after = retained.boards().get(i);
                assertEquals(before.publicHistory(), after.publicHistory());
                assertEquals(before.flop(), after.flop());
                assertEquals(before.counterfactualMarginals(), after.counterfactualMarginals());
                assertTrue(after.reachedJointDeals() > 0);
                for (var marginal : after.reachedMarginals().values())
                    assertEquals(
                            1,
                            marginal.values().stream().mapToDouble(Double::doubleValue).sum(),
                            1e-12);
                reach += after.reachedPhysicalFlopProbability();
            }
            assertEquals(
                    report.rounds().retainedAudit().reach().selectedPhysicalFlopProbability(),
                    reach,
                    1e-15);
        }
    }

    @Test
    void wholeRoundEvidenceRecomputesTheGateAndPreservesEveryPrivatePreflopWorld()
            throws Exception {
        for (int seed : new int[] {711, 712}) {
            var report = read(seed);
            assertEquals(500, report.settings().preflopIterations());
            assertEquals(300, report.settings().postflopIterations());
            assertEquals(1, report.settings().maximumRounds());
            assertEquals(.05, report.settings().conditionalGapTargetBb());
            assertEquals(.000001, report.settings().minimumParentImprovementBb());
            var rounds = report.rounds();
            assertEquals(1, rounds.rounds().size());
            var round = rounds.rounds().getFirst();
            assertEquals(rounds.initialAudit().solutionHash(), round.acceptedInputHash());
            assertEquals(round.acceptedInputHash(), round.preflopFeedback().originalSolutionHash());
            assertEquals(16, round.preflopFeedback().frozenTerminalValues().size());
            assertEquals("EXHAUSTIVE", round.preflopFeedback().preflopChanceTraversal());
            assertEquals(307_971_000L, round.preflopFeedback().preflopTraversal().visitedNodes());
            assertEquals(162_336_000L, round.preflopFeedback().preflopTraversal().terminalNodes());
            assertEquals(0, round.preflopFeedback().preflopTraversal().sampledChanceNodes());
            assertEquals(2, round.postflopRefinement().branches().size());
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
            assertEquals(1, rounds.acceptedRounds());
            assertEquals(round.candidateAudit(), rounds.retainedAudit());
            assertEquals(round.candidateAudit().solutionHash(), round.retainedSolutionHash());
            var quality = rounds.retainedAudit().parentQuality();
            assertEquals(6, quality.deviationGainsBb().size());
            assertEquals(
                    quality.nashConvBb(),
                    quality.deviationGainsBb().stream().mapToDouble(Double::doubleValue).sum(),
                    1e-9);
            assertEquals(
                    0,
                    quality.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                    1e-9);
        }
    }
}
