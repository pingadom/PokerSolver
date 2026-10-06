package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Audits recorded policies and menus without repeating the 500-iteration research solves. */
class SixMaxStagedRaiseArtifactTest {
    private static final List<String> MODELS =
            List.of("staged-three-nine", "staged-button-weak-half", "staged-button-weak-double");

    @Test
    void scheduledPacksRetainAllExactPhysicalPayoffsAndChangeOnlyOneDeclaredWeight()
            throws Exception {
        var original = SixMaxCorrelatedSourcePackTest.source();
        double[] weights = {1, .5, 2};
        for (int index = 0; index < MODELS.size(); index++) {
            var pack = SixMaxPreflopPayoffReuseArtifactTest.pack(MODELS.get(index));
            assertEquals(
                    pack.spot(),
                    MultiwayPackJson.readFullRoundSpot(
                            Files.readString(
                                    Path.of(
                                            "../docs/data/sixmax-"
                                                    + MODELS.get(index)
                                                    + "-spot.json"))));
            var evidence =
                    SixMaxPreflopPayoffReuseArtifactTest.report(MODELS.get(index), "payoff-reuse");
            assertEquals(SixMaxPreflopSolutionPack.STAGED_SCHEMA_VERSION, pack.schemaVersion());
            assertEquals(
                    SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET,
                    pack.spot().rules().raiseSchedule());
            assertEquals(List.of(3.0, 9.0), pack.spot().rules().raiseToBb());
            assertEquals("VALIDATION_ONLY", pack.publicationStatus());
            assertEquals(500, pack.solution().iterations());
            assertEquals("EXACT_ENUMERATION", pack.payoffMethod());
            assertEquals(0, pack.maxTerminalPayoffSEBb());
            assertEquals(pack.spotHash(), evidence.provenance().targetSpotHash());
            assertEquals(MultiwayPackJson.fullRoundContentHash(pack), evidence.targetPackHash());
            assertEquals(
                    MultiwayPackJson.fullRoundContentHash(original),
                    evidence.provenance().sourcePackHash());
            assertEquals(pack.spot().rules(), evidence.provenance().targetRules());
            assertEquals(684, evidence.provenance().reusedPayoffEntries());
            for (int entry = 0; entry < original.payoffs().size(); entry++) {
                var old = original.payoffs().get(entry);
                var changed = pack.payoffs().get(entry);
                assertEquals(old.dealtCombos(), changed.dealtCombos());
                assertEquals(old.activeMask(), changed.activeMask());
                assertArrayEquals(old.estimate().shares(), changed.estimate().shares());
                assertArrayEquals(
                        old.estimate().standardErrors(), changed.estimate().standardErrors());
                assertEquals(old.estimate().trials(), changed.estimate().trials());
            }
            for (int seat = 0; seat < 6; seat++) {
                var oldRange = original.spot().ranges().get(seat);
                var changedRange = pack.spot().ranges().get(seat);
                assertEquals(oldRange.size(), changedRange.size());
                for (int combo = 0; combo < oldRange.size(); combo++) {
                    var old = oldRange.get(combo);
                    var changed = changedRange.get(combo);
                    assertEquals(old.key(), changed.key());
                    assertEquals(
                            seat == 3 && old.key().equals("Js Ts") ? weights[index] : old.weight(),
                            changed.weight());
                }
            }
            var game = pack.rebuildGame();
            assertEquals(evidence.targetTree(), game.treeSummary());
            assertEquals(11_566, game.treeSummary().totalStates());
            assertEquals(0, game.treeSummary().allInTerminals());
            assertEquals(12, game.chanceOutcomes(game.initialState()).size());
            double buttonJts =
                    game.chanceOutcomes(game.initialState()).stream()
                            .filter(
                                    deal ->
                                            game.dealtHands(deal.state())
                                                    .get(3)
                                                    .key()
                                                    .equals("Js Ts"))
                            .mapToDouble(ChanceOutcome::probability)
                            .sum();
            assertEquals(weights[index] / (1 + weights[index]), buttonJts, 1e-12);
            assertEquals(
                    pack.nashConvBb(),
                    MultiPlayerInformationSetBestResponse.assess(game, pack.solution())
                            .nashConvBb(),
                    1e-10);
        }
    }

    @Test
    void sourceMenuFailuresArePreservedAndSeed711CoverageReplaysUnderUnchangedCutoffs()
            throws Exception {
        for (var model : MODELS) {
            var pack = SixMaxPreflopPayoffReuseArtifactTest.pack(model);
            var saved =
                    SixMaxPreflopPayoffReuseArtifactTest.report(model, "payoff-reuse")
                            .targetMenuSearch();
            assertEquals("NO_FIT_IN_SEARCH_WINDOW", saved.status());
            assertTrue(saved.proposedSelections().isEmpty());
            assertEquals(16, saved.attempts().size());
            assertEquals(
                    SixMaxRetainedContinuationCoverage.Settings.researchDefault(),
                    saved.coverageSettings());
            assertEquals(SixMaxContinuationStudyBudget.widerFlops(), saved.budget());
            assertEquals(711, saved.settings().firstFlopSeed());
            assertEquals(1, saved.settings().histories());
            assertTrue(
                    saved.attempts().stream()
                            .noneMatch(attempt -> "MENU_FOUND".equals(attempt.status())));
            var plan =
                    SixMaxReachedContinuationStudy.select(
                            pack.rebuildGame(),
                            pack.solution(),
                            1,
                            1,
                            711,
                            saved.budget(),
                            SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05));
            assertEquals(saved.attempts().getFirst().cost(), saved.budget().validate(plan.game()));
            var replay =
                    SixMaxRetainedContinuationCoverage.assess(
                            plan.game(), pack.solution(), saved.coverageSettings());
            var before = saved.attempts().getFirst().coverage();
            assertFalse(replay.criteriaMet());
            assertEquals(before.failures(), replay.failures());
            assertEquals(
                    before.reach().selectedHistoryProbability(),
                    replay.reach().selectedHistoryProbability(),
                    1e-12);
            assertEquals(
                    before.reach().fractionOfHeadsUpProbabilitySelected(),
                    replay.reach().fractionOfHeadsUpProbabilitySelected(),
                    1e-12);
            assertTrue(replay.failures().contains("HEADS_UP_HISTORY_COVERAGE_BELOW_MINIMUM"));
            assertTrue(replay.reach().fractionOfHeadsUpProbabilitySelected() < .25);
        }
    }

    @Test
    void savedSensitivitySeparatesPhysicalChanceFromReachedActionChanges() throws Exception {
        var evidence =
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-staged-range-weight-sensitivity.json")
                                        .toFile(),
                                SixMaxPreflopRangeWeightSensitivityMain.Artifact.class);
        assertEquals("six-max-preflop-range-weight-sensitivity/v1", evidence.schemaVersion());
        assertEquals("VALIDATION_ONLY", evidence.publicationStatus());
        assertEquals(2, evidence.comparisons().size());
        var baseline = SixMaxPreflopPayoffReuseArtifactTest.pack(MODELS.getFirst());
        for (int index = 0; index < 2; index++) {
            var variant = SixMaxPreflopPayoffReuseArtifactTest.pack(MODELS.get(index + 1));
            var replay = SixMaxPreflopRangeWeightSensitivity.assess(baseline, variant);
            var saved = evidence.comparisons().get(index);
            assertEquals(saved.baselinePackHash(), replay.baselinePackHash());
            assertEquals(saved.variantPackHash(), replay.variantPackHash());
            assertEquals(saved.weightChanges(), replay.weightChanges());
            assertEquals(1, replay.weightChanges().size());
            assertEquals(1.0 / 6, replay.jointDealTotalVariation(), 1e-12);
            assertEquals(12, replay.jointDeals());
            assertEquals(500, replay.iterations());
            assertEquals(9_161, replay.policyDifference().informationSets());
            assertEquals(138_793, replay.policyDifference().visitedStates());
            assertEquals(
                    saved.policyDifference().maximumDifferenceInformationSet(),
                    replay.policyDifference().maximumDifferenceInformationSet());
            assertEquals(
                    saved.policyDifference().reachWeightedTotalVariation(),
                    replay.policyDifference().reachWeightedTotalVariation(),
                    1e-12);
            assertTrue(replay.policyDifference().maximumTotalVariation() > .99);
        }
        assertTrue(
                evidence.comparisons().get(1).policyDifference().reachWeightedTotalVariation()
                        > .20);
        assertTrue(
                evidence.comparisons().getFirst().policyDifference().reachWeightedTotalVariation()
                        < .04);
    }
}
