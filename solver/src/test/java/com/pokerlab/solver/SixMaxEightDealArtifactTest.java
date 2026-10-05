package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks published support and quality evidence without repeating long connected CFR runs. */
class SixMaxEightDealArtifactTest {
    @Test
    void pairedStudiesBindTheEightDealSourceAndLiteralPhysicalSupport() throws Exception {
        var source =
                MultiwayPackJson.readFullRound(
                        Files.readString(
                                Path.of("../docs/data/sixmax-eight-deal-source-pack.json")));
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var plan =
                SixMaxReachedContinuationStudy.select(
                        source.rebuildGame(), source.solution(), 2, 1, 711, budget);
        var support = SixMaxPrivateSupportAudit.assess(plan.game(), source.solution());
        for (int seed : new int[] {711, 712}) {
            var report = read(seed);
            assertEquals("six-max-alternating-continuation-study/v2", report.schemaVersion());
            assertEquals("VALIDATION_ONLY", report.publicationStatus());
            assertEquals("COMPLETED", report.executionStatus());
            assertEquals("FRESH", report.executionMode());
            assertNull(report.resumedSolutionHash());
            assertEquals(MultiwayPackJson.fullRoundContentHash(source), report.sourcePackHash());
            assertEquals(source.spotHash(), report.sourceSpotHash());
            assertEquals(plan.selectedHistories(), report.selectedHistories());
            assertEquals(new SixMaxContinuationStudyBudget.Cost(16, 1_845_073), report.cost());
            assertEquals(budget, report.budget());
            assertEquals(711, report.flopSelectionSeed());
            assertEquals(1, report.flopsPerHistory());
            assertEquals(support, report.sourcePrivateSupport());
            assertEquals(seed, report.initialTraining().seed());
            assertEquals(500, report.initialTraining().jointIterations());
            assertEquals(300, report.initialTraining().initialPostflopIterations());
            assertEquals(500, report.settings().preflopIterations());
            assertEquals(300, report.settings().postflopIterations());
            assertEquals(1, report.settings().maximumRounds());
            assertTrue(report.initialTraining().traversal().sampledChanceNodes() > 0);
            var retained = report.retainedPrivateSupport();
            assertEquals(report.rounds().retainedAudit().solutionHash(), retained.solutionHash());
            assertEquals(8, retained.sourceJointDeals());
            assertEquals(support.sourceMarginals(), retained.sourceMarginals());
            assertEquals(List.of(Seat.CO, Seat.BTN, Seat.BB), retained.uncertainSeats());
            assertEquals(2, retained.boards().size());
            double reached = 0;
            for (int i = 0; i < retained.boards().size(); i++) {
                var board = retained.boards().get(i);
                var before = support.boards().get(i);
                assertEquals(before.publicHistory(), board.publicHistory());
                assertEquals(before.flop(), board.flop());
                assertEquals(8, board.counterfactualJointDeals());
                assertEquals(1, board.compatiblePriorMass(), 1e-12);
                assertEquals(1.0 / 9880, board.priorPhysicalFlopProbability(), 1e-15);
                assertEquals(before.counterfactualMarginals(), board.counterfactualMarginals());
                assertEquals(List.of(Seat.CO), board.uncertainFoldedSeats());
                assertEquals(2, board.firstPlayerRootInformationSets());
                assertEquals(2, board.secondPlayerRootInformationSets());
                assertTrue(board.reachedJointDeals() > 0 && board.reachedJointDeals() <= 8);
                assertEquals(
                        board.policyHistoryReach() / 9880,
                        board.reachedPhysicalFlopProbability(),
                        1e-15);
                checkMarginals(board.reachedMarginals());
                reached += board.reachedPhysicalFlopProbability();
            }
            assertEquals(
                    report.rounds().retainedAudit().reach().selectedPhysicalFlopProbability(),
                    reached,
                    1e-15);
        }
    }

    @Test
    void acceptedPolicyChainsRequireSameGameParentAndConditionalQuality() throws Exception {
        for (int seed : new int[] {711, 712}) {
            var report = read(seed);
            var rounds = report.rounds();
            assertTrue(rounds.initialAudit().everySelectedBranchReached());
            assertTrue(rounds.initialAudit().maximumConditionalGapBb() <= .05);
            String retainedHash = rounds.initialAudit().solutionHash();
            double retainedGap = rounds.initialAudit().parentQuality().nashConvBb();
            int accepted = 0;
            for (var round : rounds.rounds()) {
                assertEquals(retainedHash, round.acceptedInputHash());
                assertEquals(retainedHash, round.preflopFeedback().originalSolutionHash());
                assertEquals(16, round.preflopFeedback().frozenTerminalValues().size());
                assertEquals("EXHAUSTIVE", round.preflopFeedback().preflopChanceTraversal());
                assertEquals(
                        307_971_000L, round.preflopFeedback().preflopTraversal().visitedNodes());
                assertEquals(
                        162_336_000L, round.preflopFeedback().preflopTraversal().terminalNodes());
                assertEquals(0, round.preflopFeedback().preflopTraversal().sampledChanceNodes());
                assertEquals(0, round.preflopFeedback().preflopTraversal().baselineCorrections());
                assertEquals(2, round.postflopRefinement().branches().size());
                double after = round.candidateAudit().parentQuality().nashConvBb();
                var computed =
                        SixMaxAlternatingQualityGate.assess(
                                retainedGap,
                                after,
                                round.candidateAudit().maximumConditionalGapBb(),
                                round.candidateAudit().everySelectedBranchReached(),
                                .05,
                                .000001);
                assertEquals(computed, round.decision());
                if (computed.accepted()) {
                    retainedGap = after;
                    retainedHash = round.candidateAudit().solutionHash();
                    accepted++;
                }
                assertEquals(retainedHash, round.retainedSolutionHash());
            }
            assertEquals(accepted, rounds.acceptedRounds());
            assertEquals(retainedHash, rounds.retainedAudit().solutionHash());
            assertEquals(retainedGap, rounds.retainedAudit().parentQuality().nashConvBb());
            assertTrue(rounds.retainedAudit().maximumConditionalGapBb() <= .05);
            var quality = rounds.retainedAudit().parentQuality();
            assertEquals(6, quality.deviationGainsBb().size());
            assertEquals(
                    retainedGap,
                    quality.deviationGainsBb().stream().mapToDouble(Double::doubleValue).sum(),
                    1e-9);
            assertEquals(
                    0,
                    quality.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                    1e-9);
        }
    }

    private static void checkMarginals(Map<Seat, Map<String, Double>> marginals) {
        assertEquals(java.util.Set.of(Seat.values()), marginals.keySet());
        for (var weights : marginals.values()) {
            assertTrue(
                    weights.values().stream()
                            .allMatch(p -> Double.isFinite(p) && p > 0 && p <= 1 + 1e-12));
            assertEquals(
                    1, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        }
    }

    private static SixMaxAlternatingContinuationStudyMain.Artifact read(int seed) throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of("../docs/data/sixmax-eight-deal-alternating-seed-" + seed + ".json")
                                .toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }
}
