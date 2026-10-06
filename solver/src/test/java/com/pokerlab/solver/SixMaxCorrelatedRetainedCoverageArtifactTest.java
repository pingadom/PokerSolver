package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Saved screening evidence cross-checked against the independently saved completed solve. */
class SixMaxCorrelatedRetainedCoverageArtifactTest {
    @Test
    void relaxedReachSensitivityStillFindsNoQualifyingMenuInItsDeclaredWindow() throws Exception {
        var screen =
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-correlated-relaxed-coverage-711.json")
                                        .toFile(),
                                SixMaxRetainedContinuationCoverageMain.Artifact.class);
        var trial = SixMaxCorrelatedLinearArtifactTest.read(711);
        assertEquals(trial.sourcePackHash(), screen.sourcePackHash());
        assertEquals(
                trial.rounds().retainedAudit().solutionHash(), screen.checkpointSolutionHash());
        assertEquals(trial.rounds().retainedAudit().reach(), screen.retainedCoverage().reach());
        assertEquals(
                new SixMaxRetainedContinuationCoverage.Settings(.00001, .25, .05, 2),
                screen.retainedCoverage().settings());
        assertFalse(screen.retainedCoverage().criteriaMet());
        for (var history : screen.retainedCoverage().histories()) {
            assertTrue(history.probability() >= .00001);
            assertFalse(history.failures().contains("HISTORY_REACH_BELOW_MINIMUM"));
            assertEquals(1, history.boards().getFirst().second().materialReachedCombos());
        }
        var search = screen.retainedMenuSearch();
        assertEquals("NO_FIT_IN_SEARCH_WINDOW", search.status());
        assertTrue(search.feasibilityFailures().isEmpty());
        assertEquals(
                java.util.stream.LongStream.rangeClosed(711, 726).boxed().toList(),
                search.attempts().stream()
                        .map(SixMaxContinuationMenuSearch.Attempt::flopSeed)
                        .toList());
        assertTrue(search.proposedSelections().isEmpty());
        assertEquals(
                11,
                search.attempts().stream()
                        .filter(a -> a.status().equals("BUDGET_REJECTED"))
                        .count());
        assertEquals(
                4,
                search.attempts().stream()
                        .filter(a -> a.status().equals("NO_DIVERSE_MENU"))
                        .count());
        var candidate =
                search.attempts().stream()
                        .filter(a -> a.status().equals("CONTENT_REJECTED"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(713, candidate.flopSeed());
        assertFalse(candidate.coverage().criteriaMet());
        assertTrue(candidate.coverage().reach().fractionOfHeadsUpProbabilitySelected() < .003);
        for (var history : candidate.coverage().histories()) {
            assertTrue(history.probability() < .00001);
            assertTrue(history.failures().contains("HISTORY_REACH_BELOW_MINIMUM"));
            for (var board : history.boards()) {
                assertEquals(2, board.first().materialReachedCombos());
                assertEquals(2, board.second().materialReachedCombos());
            }
        }
    }

    @Test
    void qualityAcceptedTrialsStillFailRetainedContentCriteria() throws Exception {
        var source = SixMaxCorrelatedSourcePackTest.source();
        for (int seed : new int[] {711, 712}) {
            var trial = SixMaxCorrelatedLinearArtifactTest.read(seed);
            // Historical v4 evidence remains unchanged; v5 adds screens to future reports.
            assertNull(trial.sourceContentCoverage());
            assertNull(trial.retainedContentCoverage());
            var screen =
                    new ObjectMapper()
                            .readValue(
                                    Path.of(
                                                    "../docs/data/sixmax-correlated-retained-coverage-"
                                                            + seed
                                                            + ".json")
                                            .toFile(),
                                    SixMaxRetainedContinuationCoverageMain.Artifact.class);
            assertEquals("six-max-retained-continuation-coverage/v1", screen.schemaVersion());
            assertEquals("VALIDATION_ONLY", screen.publicationStatus());
            assertEquals(trial.sourcePackHash(), screen.sourcePackHash());
            assertEquals(trial.sourceSpotHash(), screen.sourceSpotHash());
            assertEquals(trial.budget(), screen.budget());
            assertEquals(
                    trial.selectedHistories().stream()
                            .map(
                                    h ->
                                            new SixMaxConnectedPreflopGame.Selection(
                                                    h.coverage().actions(),
                                                    h.coverage().flops().stream()
                                                            .map(
                                                                    f ->
                                                                            f.stream()
                                                                                    .map(
                                                                                            com
                                                                                                            .pokerlab
                                                                                                            .core
                                                                                                            .card
                                                                                                            .Card
                                                                                                    ::parse)
                                                                                    .toList())
                                                            .toList(),
                                                    h.coverage().flopBetBb(),
                                                    h.coverage().requestedTurnBetBb(),
                                                    h.coverage().requestedRiverBetBb()))
                            .toList(),
                    screen.checkpointSelections());
            var game =
                    new SixMaxConnectedPreflopGame(
                            source.rebuildGame(), screen.checkpointSelections());
            assertEquals(
                    SixMaxRetainedContinuationCoverage.assess(
                            game,
                            source.solution(),
                            SixMaxRetainedContinuationCoverage.Settings.researchDefault()),
                    screen.sourceCoverage());
            assertTrue(screen.sourceCoverage().criteriaMet());
            assertEquals(1, trial.rounds().acceptedRounds());
            assertEquals(
                    trial.rounds().retainedAudit().solutionHash(), screen.checkpointSolutionHash());
            assertEquals(screen.checkpointSolutionHash(), screen.retainedCoverage().solutionHash());
            assertEquals(trial.rounds().retainedAudit().reach(), screen.retainedCoverage().reach());
            assertFalse(screen.retainedCoverage().criteriaMet());
            assertEquals("COVERAGE_CRITERIA_FAILED", screen.retainedCoverage().status());
            var thresholds = screen.retainedCoverage().settings();
            for (int index = 0; index < trial.retainedPrivateSupport().boards().size(); index++) {
                var original = trial.retainedPrivateSupport().boards().get(index);
                var history = screen.retainedCoverage().histories().get(index);
                var board = history.boards().getFirst();
                assertEquals(original.publicHistory(), history.publicHistory());
                assertEquals(original.policyHistoryReach(), history.probability());
                assertEquals(original.flop(), board.flop());
                assertEquals(original.reachedJointDeals(), board.reachedJointDeals());
                assertEquals(original.counterfactualJointDeals(), board.counterfactualJointDeals());
                assertEquals(
                        original.reachedPhysicalFlopProbability(),
                        board.absolutePhysicalProbability());
                assertEquals(
                        history.probability() * board.probabilityGivenHistory(),
                        board.absolutePhysicalProbability(),
                        1e-20);
                assertTrue(history.probability() < thresholds.minimumHistoryProbability());
                assertEquals("POSITIVE_REACH", history.reachStatus());
                assertEquals(Math.log(history.probability()), history.logProbability(), 1e-12);
                for (var mix : java.util.List.of(board.first(), board.second())) {
                    assertEquals(
                            original.counterfactualMarginals().get(mix.seat()),
                            mix.counterfactualMarginal());
                    assertEquals(
                            original.reachedMarginals().get(mix.seat()), mix.reachedMarginal());
                    assertEquals(
                            mix.reachedMarginal().values().stream()
                                    .filter(p -> p >= thresholds.minimumComboMass())
                                    .count(),
                            mix.materialReachedCombos());
                }
                assertEquals(2, board.first().materialReachedCombos());
                assertEquals(1, board.second().materialReachedCombos());
                assertTrue(board.failures().contains("BTN:RETAINED_HAND_MIX_TOO_NARROW"));
            }
            var search = screen.sourceMenuSearch();
            assertEquals("MENU_FOUND", search.status());
            assertEquals(
                    java.util.List.of(711L, 712L, 713L, 714L, 715L),
                    search.attempts().stream()
                            .map(SixMaxContinuationMenuSearch.Attempt::flopSeed)
                            .toList());
            assertEquals(
                    java.util.List.of(18L, 24L, 24L, 18L),
                    search.attempts().subList(0, 4).stream()
                            .map(SixMaxContinuationMenuSearch.Attempt::required)
                            .toList());
            assertEquals(screen.checkpointSelections(), search.proposedSelections());
            assertEquals(screen.sourceCoverage(), search.attempts().getLast().coverage());
            assertEquals(trial.cost(), search.attempts().getLast().cost());
            var retainedSearch = screen.retainedMenuSearch();
            assertEquals("FEASIBILITY_FAILED", retainedSearch.status());
            assertTrue(
                    retainedSearch.highestHistoryProbability()
                            < thresholds.minimumHistoryProbability());
            assertEquals(
                    screen.retainedCoverage().reach().headsUpProbability(),
                    retainedSearch.headsUpProbability());
            assertEquals(
                    java.util.List.of("INSUFFICIENT_MATERIAL_HISTORY_REACH"),
                    retainedSearch.feasibilityFailures());
            assertTrue(retainedSearch.attempts().isEmpty());
            assertTrue(retainedSearch.proposedSelections().isEmpty());
        }
    }
}
