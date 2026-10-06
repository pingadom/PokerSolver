package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Replays small saved diagnostics and exact payoffs; does not repeat the 500-iteration solves. */
class SixMaxPreflopPayoffReuseArtifactTest {
    static SixMaxPreflopSolutionPack pack(String model) throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("../docs/data/sixmax-" + model + "-source-pack.json")));
    }

    static SixMaxPreflopPayoffReuseMain.Artifact report(String model, String suffix)
            throws Exception {
        return new ObjectMapper()
                .readValue(
                        Path.of("../docs/data/sixmax-" + model + "-" + suffix + ".json").toFile(),
                        SixMaxPreflopPayoffReuseMain.Artifact.class);
    }

    @Test
    void exactSharesBindFreshPoliciesAndDifferentBettingUtilitiesOnIdenticalPhysicalSupport()
            throws Exception {
        var source = SixMaxCorrelatedSourcePackTest.source();
        for (var model : List.of("three-nine", "three-open")) {
            var target = pack(model);
            var evidence = report(model, "payoff-reuse");
            assertEquals(
                    MultiwayPackJson.fullRoundContentHash(source),
                    evidence.provenance().sourcePackHash());
            assertEquals(source.spotHash(), evidence.provenance().sourceSpotHash());
            assertEquals(target.spotHash(), evidence.provenance().targetSpotHash());
            assertEquals(MultiwayPackJson.fullRoundContentHash(target), evidence.targetPackHash());
            assertEquals(source.spot().ranges(), target.spot().ranges());
            assertEquals(source.spot().rake(), target.spot().rake());
            assertEquals(684, evidence.provenance().reusedPayoffEntries());
            assertEquals(12, evidence.provenance().jointDeals());
            assertEquals(500, target.solution().iterations());
            assertEquals("VALIDATION_ONLY", target.publicationStatus());
            assertEquals("EXACT_ENUMERATION", target.payoffMethod());
            assertEquals(0, target.maxTerminalPayoffSEBb());
            assertNotEquals(source.spotHash(), target.spotHash());
            assertNotEquals(source.solution(), target.solution());
            assertEquals(target.nashConvBb(), evidence.provenance().targetNashConvBb());
            for (int i = 0; i < source.payoffs().size(); i++) {
                var original = source.payoffs().get(i);
                var reused = target.payoffs().get(i);
                assertEquals(original.dealtCombos(), reused.dealtCombos());
                assertEquals(original.activeMask(), reused.activeMask());
                assertArrayEquals(original.estimate().shares(), reused.estimate().shares());
                assertArrayEquals(
                        original.estimate().standardErrors(), reused.estimate().standardErrors());
                assertEquals(original.estimate().trials(), reused.estimate().trials());
            }
            var game = target.rebuildGame();
            assertEquals(evidence.targetTree(), game.treeSummary());
            assertEquals(12, game.chanceOutcomes(game.initialState()).size());
            assertEquals(0, game.treeSummary().allInTerminals());
            assertEquals(
                    0,
                    MultiPlayerStrategyCompletion.uniformAtUnseen(game, target.solution(), 200_000)
                            .addedInformationSets());
            var replay = SixMaxPreflopContinuationAudit.assess(game, target.solution(), 711, 20);
            assertEquals(
                    evidence.targetReach().headsUpContinuationProbability(),
                    replay.headsUpContinuationProbability(),
                    1e-12);
            assertTrue(target.nashConvBb() < .001);
        }
        assertEquals(12_832, pack("three-nine").rebuildGame().treeSummary().totalStates());
        assertEquals(1_360, pack("three-open").rebuildGame().treeSummary().totalStates());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopResearchSpot(
                                "over-cap",
                                new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0, 9.0, 100.0)),
                                source.spot().ranges(),
                                source.spot().rake(),
                                source.spot().continuationModel()));
    }

    @Test
    void reachedHandsAndBudgetsRemainSeparateFromHigherHeadsUpFrequency() throws Exception {
        var nine = report("three-nine", "payoff-reuse");
        var open = report("three-open", "payoff-reuse");
        assertEquals(.413630576758604, nine.targetReach().headsUpContinuationProbability(), 1e-12);
        assertEquals(.7496589378299601, open.targetReach().headsUpContinuationProbability(), 1e-12);
        for (var evidence : List.of(nine, open)) {
            assertEquals("NO_FIT_IN_SEARCH_WINDOW", evidence.targetMenuSearch().status());
            assertEquals(16, evidence.targetMenuSearch().attempts().size());
            assertTrue(evidence.targetMenuSearch().proposedSelections().isEmpty());
            assertEquals(
                    SixMaxContinuationStudyBudget.widerFlops(),
                    evidence.targetMenuSearch().budget());
            assertEquals(
                    SixMaxRetainedContinuationCoverage.Settings.researchDefault(),
                    evidence.targetMenuSearch().coverageSettings());
            assertTrue(evidence.targetMenuSearch().feasibilityFailures().isEmpty());
        }
        var nineSingle = report("three-nine", "single-history-search").targetMenuSearch();
        assertEquals("NO_FIT_IN_SEARCH_WINDOW", nineSingle.status());
        assertEquals(16, nineSingle.attempts().size());
        assertTrue(
                nineSingle.attempts().stream()
                        .allMatch(a -> a.status().equals("CONTENT_REJECTED")));
        var openSingle = report("three-open", "single-history-search").targetMenuSearch();
        assertEquals("MENU_FOUND", openSingle.status());
        assertEquals(1, openSingle.attempts().size());
        assertEquals(711, openSingle.attempts().getFirst().flopSeed());
        assertEquals(
                new SixMaxContinuationStudyBudget.Cost(12, 1_323_133),
                openSingle.attempts().getFirst().cost());
        var target = pack("three-open");
        var game =
                new SixMaxConnectedPreflopGame(
                        target.rebuildGame(), openSingle.proposedSelections());
        var coverage =
                SixMaxRetainedContinuationCoverage.assess(
                        game,
                        target.solution(),
                        SixMaxRetainedContinuationCoverage.Settings.researchDefault());
        assertEquals(openSingle.attempts().getFirst().coverage(), coverage);
        assertTrue(coverage.criteriaMet());
        assertEquals(12, game.chanceOutcomes(game.initialState()).size());
        assertEquals(.337907089298028, coverage.reach().selectedHistoryProbability(), 1e-12);
        assertEquals(
                .450747763077659, coverage.reach().fractionOfHeadsUpProbabilitySelected(), 1e-12);
        var board = coverage.histories().getFirst().boards().getFirst();
        assertEquals(12, board.counterfactualJointDeals());
        assertEquals(2, board.first().materialReachedCombos());
        assertEquals(2, board.second().materialReachedCombos());
        assertEquals(1, board.compatiblePosteriorMass(), 1e-12);
    }
}
