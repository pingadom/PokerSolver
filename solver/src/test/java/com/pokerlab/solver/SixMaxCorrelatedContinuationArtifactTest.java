package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Checks saved experiments without rerunning their expensive training stages in CI. */
class SixMaxCorrelatedContinuationArtifactTest {
    static SixMaxAlternatingContinuationStudyMain.Artifact read(int seed) throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of("../docs/data/sixmax-correlated-alternating-seed-" + seed + ".json")
                                .toFile(),
                        SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }

    @Test
    void artifactsBindFullPhysicalSupportAndTheDeclaredCostSelectedMenu() throws Exception {
        var source = SixMaxCorrelatedSourcePackTest.source();
        var base = source.rebuildGame();
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var plan =
                SixMaxReachedContinuationStudy.select(
                        base,
                        source.solution(),
                        2,
                        1,
                        715,
                        budget,
                        SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05));
        var support = SixMaxPrivateSupportAudit.assess(plan.game(), source.solution());
        var correlation = SixMaxPrivateRangeCorrelationAudit.assess(base);
        for (int seed : new int[] {711, 712}) {
            var artifact = read(seed);
            assertEquals("six-max-alternating-continuation-study/v4", artifact.schemaVersion());
            assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
            assertEquals("COMPLETED", artifact.executionStatus());
            assertEquals("FRESH", artifact.executionMode());
            assertEquals(MultiwayPackJson.fullRoundContentHash(source), artifact.sourcePackHash());
            assertEquals(source.spotHash(), artifact.sourceSpotHash());
            assertEquals(budget, artifact.budget());
            assertEquals(budget.validate(plan.game()), artifact.cost());
            assertEquals(plan.selectedHistories(), artifact.selectedHistories());
            assertEquals(plan.selectionAudit(), artifact.selectionAudit());
            assertEquals(support, artifact.sourcePrivateSupport());
            assertEquals(correlation, artifact.sourcePrivateCorrelation());
            assertEquals(
                    new SixMaxAlternatingContinuationStudyMain.FreshTrainingSettings(
                            seed, 500, 300),
                    artifact.freshTrainingSettings());
            var retained = artifact.retainedPrivateSupport();
            assertEquals(artifact.rounds().retainedAudit().solutionHash(), retained.solutionHash());
            assertEquals(support.sourceMarginals(), retained.sourceMarginals());
            assertEquals(
                    List.of(4, 12),
                    retained.boards().stream()
                            .map(SixMaxPrivateSupportAudit.BoardSupport::counterfactualJointDeals)
                            .toList());
            double reach = 0;
            for (int i = 0; i < 2; i++) {
                var original = support.boards().get(i);
                var after = retained.boards().get(i);
                assertEquals(original.publicHistory(), after.publicHistory());
                assertEquals(original.flop(), after.flop());
                assertEquals(original.counterfactualMarginals(), after.counterfactualMarginals());
                assertTrue(after.reachedJointDeals() > 0);
                for (var marginal : after.reachedMarginals().values())
                    assertEquals(
                            1,
                            marginal.values().stream().mapToDouble(Double::doubleValue).sum(),
                            1e-12);
                reach += after.reachedPhysicalFlopProbability();
            }
            assertEquals(
                    artifact.rounds().retainedAudit().reach().selectedPhysicalFlopProbability(),
                    reach,
                    1e-15);
        }
    }

    @Test
    void gateAndFullPreflopTraversalAreRecomputedFromRecordedEvidence() throws Exception {
        for (int seed : new int[] {711, 712}) {
            var artifact = read(seed);
            assertEquals(
                    new SixMaxAlternatingContinuationSolver.Settings(1, 500, 300, .05, .000001),
                    artifact.settings());
            var rounds = artifact.rounds();
            assertEquals(1, rounds.rounds().size());
            var round = rounds.rounds().getFirst();
            assertEquals(rounds.initialAudit().solutionHash(), round.acceptedInputHash());
            var feedback = round.preflopFeedback();
            assertEquals(round.acceptedInputHash(), feedback.originalSolutionHash());
            assertEquals(10_172, feedback.replacedPreflopInformationSets());
            assertEquals(24, feedback.frozenTerminalValues().size());
            for (var history : artifact.selectedHistories()) {
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
            assertEquals("EXHAUSTIVE", feedback.preflopChanceTraversal());
            assertEquals(461_955_000L, feedback.preflopTraversal().visitedNodes());
            assertEquals(243_504_000L, feedback.preflopTraversal().terminalNodes());
            assertEquals(0, feedback.preflopTraversal().sampledChanceNodes());
            assertEquals(
                    artifact.initialTraining().visitedInformationSets()
                            + artifact.initialTraining().uniformlyCompletedInformationSets(),
                    feedback.replacedPreflopInformationSets()
                            + feedback.preservedPostflopInformationSets());
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
            assertEquals(decision.accepted() ? 1 : 0, rounds.acceptedRounds());
            assertEquals(
                    decision.accepted() ? round.candidateAudit() : rounds.initialAudit(),
                    rounds.retainedAudit());
            assertEquals(rounds.retainedAudit().solutionHash(), round.retainedSolutionHash());
            assertTrue(rounds.retainedAudit().everySelectedBranchReached());
            assertTrue(rounds.retainedAudit().maximumConditionalGapBb() <= .05);
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
