package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SixMaxContinuationMenuSearchTest {
    private static SixMaxRetainedContinuationCoverage.Settings permissive() {
        return new SixMaxRetainedContinuationCoverage.Settings(1e-12, 1e-12, .05, 2);
    }

    @Test
    void deterministicProposalKeepsEveryWorldAndNeverTransfersOldPostflopRows() throws Exception {
        var pack = SixMaxReachedContinuationStudyTest.eightDealSource();
        var source = pack.rebuildGame();
        var settings = new SixMaxContinuationMenuSearch.Settings(711, 3, 2, 1);
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var report =
                SixMaxContinuationMenuSearch.search(
                        source, pack.solution(), settings, budget, permissive());
        assertEquals("MENU_FOUND", report.status());
        assertEquals(1, report.attempts().size());
        assertEquals("MENU_FOUND", report.attempts().getFirst().status());
        assertEquals(
                new SixMaxContinuationStudyBudget.Cost(12, 1_409_473),
                report.attempts().getFirst().cost());
        assertTrue(report.attempts().getFirst().coverage().criteriaMet());
        var game = new SixMaxConnectedPreflopGame(source, report.proposedSelections());
        assertEquals(8, game.chanceOutcomes(game.initialState()).size());
        assertEquals(2, game.selections().size());
        assertEquals(
                report,
                SixMaxContinuationMenuSearch.search(
                        source, pack.solution(), settings, budget, permissive()));
        assertThrows(
                UnsupportedOperationException.class, () -> report.proposedSelections().clear());
        assertEquals(
                SixMaxConnectedPostflopAudit.solutionHash(pack.solution()),
                report.preflopPolicyHash());
        assertTrue(report.interpretation().contains("requires a new solve"));
    }

    @Test
    void publicReachBoundsRejectImpossibleRequestsBeforeTryingFlopSeeds() throws Exception {
        var pack = SixMaxReachedContinuationStudyTest.eightDealSource();
        var source = pack.rebuildGame();
        var settings = new SixMaxContinuationMenuSearch.Settings(711, 16, 2, 1);
        var reach =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        settings,
                        SixMaxContinuationStudyBudget.widerFlops(),
                        new SixMaxRetainedContinuationCoverage.Settings(1, 1e-12, .05, 2));
        assertEquals("FEASIBILITY_FAILED", reach.status());
        assertEquals(
                java.util.List.of("INSUFFICIENT_MATERIAL_HISTORY_REACH"),
                reach.feasibilityFailures());
        assertTrue(reach.highestHistoryProbability() < 1);
        assertTrue(reach.requestedLastHistoryProbability() <= reach.highestHistoryProbability());
        assertTrue(reach.attempts().isEmpty());
        assertTrue(reach.proposedSelections().isEmpty());
        var coverage =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        new SixMaxContinuationMenuSearch.Settings(711, 16, 1, 1),
                        SixMaxContinuationStudyBudget.widerFlops(),
                        new SixMaxRetainedContinuationCoverage.Settings(1e-12, 1, .05, 2));
        assertEquals("FEASIBILITY_FAILED", coverage.status());
        assertEquals(
                java.util.List.of("INSUFFICIENT_POSSIBLE_HEADS_UP_COVERAGE"),
                coverage.feasibilityFailures());
        assertTrue(coverage.maximumHeadsUpFractionWithRequestedHistories() < 1);
        assertTrue(coverage.attempts().isEmpty());
    }

    @Test
    void boundedSearchRecordsCostAndDiversityFailuresWithoutTrimmingSupport() throws Exception {
        var pack = SixMaxReachedContinuationStudyTest.eightDealSource();
        var source = pack.rebuildGame();
        var settings = new SixMaxContinuationMenuSearch.Settings(711, 2, 2, 1);
        var expensive =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        settings,
                        new SixMaxContinuationStudyBudget(1, 2_000_000),
                        permissive());
        assertEquals("NO_FIT_IN_SEARCH_WINDOW", expensive.status());
        assertEquals(2, expensive.attempts().size());
        for (var attempt : expensive.attempts()) {
            assertEquals("BUDGET_REJECTED", attempt.status());
            assertEquals("COMPATIBLE_DEAL_FLOPS", attempt.budgetResource());
            assertTrue(attempt.required() > attempt.limit());
            assertEquals(1L, attempt.limit());
        }
        var narrow =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        settings,
                        SixMaxContinuationStudyBudget.widerFlops(),
                        new SixMaxRetainedContinuationCoverage.Settings(1e-12, 1e-12, .49, 2));
        assertEquals("NO_FIT_IN_SEARCH_WINDOW", narrow.status());
        assertEquals(2, narrow.attempts().size());
        for (var attempt : narrow.attempts()) {
            assertEquals("NO_DIVERSE_MENU", attempt.status());
            assertFalse(attempt.selectionAudit().considered().isEmpty());
            assertNull(attempt.cost());
        }
        assertEquals(8, source.chanceOutcomes(source.initialState()).size());

        var tooManyStates =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        new SixMaxContinuationMenuSearch.Settings(711, 1, 2, 1),
                        new SixMaxContinuationStudyBudget(16, 1_000),
                        permissive());
        var rejected = tooManyStates.attempts().getFirst();
        assertEquals("BUDGET_REJECTED", rejected.status());
        assertEquals("COMPLETE_TREE_STATES", rejected.budgetResource());
        assertEquals(1_409_473L, rejected.required());
        assertEquals(1_000L, rejected.limit());
    }

    @Test
    void validatesSearchLimitsAndDoesNotSwallowInvalidPolicies() {
        for (int value : new int[] {0, 17})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxContinuationMenuSearch.Settings(711, value, 2, 1));
        for (int value : new int[] {0, 5}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxContinuationMenuSearch.Settings(711, 2, value, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxContinuationMenuSearch.Settings(711, 2, 2, value));
        }
        assertThrows(
                ArithmeticException.class,
                () -> new SixMaxContinuationMenuSearch.Settings(Long.MAX_VALUE, 2, 2, 1));
        var source = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxContinuationMenuSearch.search(
                                source,
                                new CfrSolution(1, java.util.Map.of()),
                                new SixMaxContinuationMenuSearch.Settings(711, 2, 2, 1),
                                SixMaxContinuationStudyBudget.widerFlops(),
                                permissive()));
    }

    @Test
    void noHeadsUpReachCannotBecomeAProposalBySamplingDifferentBoards() {
        var source = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var rows =
                new java.util.LinkedHashMap<>(
                        SixMaxRetainedContinuationCoverageTest.uniform(source).strategy());
        rows.replaceAll(
                (key, row) -> {
                    String chosen =
                            row.containsKey("fold") ? "fold" : row.keySet().iterator().next();
                    var pure = new java.util.LinkedHashMap<>(row);
                    pure.replaceAll((action, mass) -> action.equals(chosen) ? 1.0 : 0.0);
                    return pure;
                });
        var report =
                SixMaxContinuationMenuSearch.search(
                        source,
                        new CfrSolution(1, rows),
                        new SixMaxContinuationMenuSearch.Settings(711, 16, 2, 1),
                        SixMaxContinuationStudyBudget.widerFlops(),
                        permissive());
        assertEquals("FEASIBILITY_FAILED", report.status());
        assertEquals(0, report.headsUpProbability());
        assertEquals(0, report.highestHistoryProbability());
        assertEquals(0, report.maximumHeadsUpFractionWithRequestedHistories());
        assertEquals(2, report.feasibilityFailures().size());
        assertTrue(report.attempts().isEmpty());
    }

    @Test
    void optimisticReachBoundDoesNotGuaranteeTheGreedyDiverseMenuWillPass() throws Exception {
        var pack = SixMaxReachedContinuationStudyTest.eightDealSource();
        var source = pack.rebuildGame();
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var selected =
                SixMaxReachedContinuationStudy.select(
                        source,
                        pack.solution(),
                        1,
                        2,
                        711,
                        budget,
                        SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05));
        assertEquals(5, selected.selectedHistories().getFirst().sourceReachRank());
        var ranked = SixMaxPreflopContinuationAudit.assess(source, pack.solution(), 711, 20);
        double cutoff =
                (ranked.examples().getFirst().reachProbability()
                                + selected.selectedHistories().getFirst().sourceReachProbability())
                        / 2;
        var report =
                SixMaxContinuationMenuSearch.search(
                        source,
                        pack.solution(),
                        new SixMaxContinuationMenuSearch.Settings(711, 1, 1, 2),
                        budget,
                        new SixMaxRetainedContinuationCoverage.Settings(cutoff, 1e-12, .05, 2));
        assertTrue(report.feasibilityFailures().isEmpty());
        assertEquals("NO_FIT_IN_SEARCH_WINDOW", report.status());
        assertEquals("CONTENT_REJECTED", report.attempts().getFirst().status());
        assertFalse(report.attempts().getFirst().coverage().criteriaMet());
        assertTrue(report.proposedSelections().isEmpty());
    }
}
