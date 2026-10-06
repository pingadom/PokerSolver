package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxRetainedContinuationCoverageTest {
    static SixMaxPreflopCheckdownGame fourDealBase() {
        var original = SixMaxConnectedPreflopGameTest.base();
        var hands =
                original.dealtHands(
                        original.chanceOutcomes(original.initialState()).getFirst().state());
        var ranges = new ArrayList<>(hands.stream().map(List::of).toList());
        ranges.set(
                3,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("Js Jh", 1),
                        SixMaxConnectedPreflopGameTest.combo("Jc Jd", 4)));
        ranges.set(
                5,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("9s 9h", 1),
                        SixMaxConnectedPreflopGameTest.combo("9c 9d", 1)));
        return new SixMaxPreflopCheckdownGame(
                original.rules(),
                ranges,
                CashRakeRule.none(),
                (dealt, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    static CfrSolution uniform(SixMaxPreflopCheckdownGame base) {
        return MultiPlayerStrategyCompletion.uniformAtUnseen(
                        base, new CfrSolution(1, Map.of()), 200_000)
                .solution();
    }

    private static SixMaxRetainedContinuationCoverage.Settings permissive() {
        return new SixMaxRetainedContinuationCoverage.Settings(1e-12, 1e-12, .05, 2);
    }

    @Test
    void physicalBoardsDoNotDoubleCountHistoriesAndBlockersExplainNarrowSupport() {
        var base = fourDealBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d", "9s 2d 3d")));
        var policy = uniform(base);
        var report = SixMaxRetainedContinuationCoverage.assess(game, policy, permissive());
        var history = report.histories().getFirst();
        assertEquals(1, report.histories().size());
        assertEquals(history.probability(), report.reach().selectedHistoryProbability(), 1e-15);
        assertEquals(2, history.boards().size());
        var first = history.boards().getFirst();
        assertTrue(first.failures().isEmpty());
        assertEquals(4, first.counterfactualJointDeals());
        assertEquals(2, first.first().materialReachedCombos());
        assertEquals(2, first.second().materialReachedCombos());
        assertEquals(1.0 / 9880, first.probabilityGivenHistory(), 1e-15);
        assertEquals(1, first.compatiblePosteriorMass(), 1e-12);
        assertEquals(2, first.first().effectiveReachedCombos(), 1e-12);
        var blocked = history.boards().get(1);
        assertEquals(2, blocked.counterfactualJointDeals());
        assertEquals(.5 / 9880, blocked.probabilityGivenHistory(), 1e-15);
        assertEquals(.5, blocked.compatiblePosteriorMass(), 1e-12);
        assertEquals(
                List.of(
                        "BB:COUNTERFACTUAL_HAND_SUPPORT_TOO_NARROW",
                        "BB:RETAINED_HAND_MIX_TOO_NARROW"),
                blocked.failures());
        assertEquals(1, blocked.first().effectiveReachedCombos(), 1e-12);
        assertEquals(
                history.probability() * 1.5 / 9880,
                report.reach().selectedPhysicalFlopProbability(),
                1e-15);
        assertFalse(report.criteriaMet());
        assertThrows(
                UnsupportedOperationException.class, () -> first.first().reachedMarginal().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.histories().clear());
    }

    @Test
    void inclusiveReachAndComboThresholdsAreIndependentOfRarePhysicalFlops() {
        var base = fourDealBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base, List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d")));
        var policy = uniform(base);
        var initial = SixMaxRetainedContinuationCoverage.assess(game, policy, permissive());
        double comboBoundary =
                initial
                        .histories()
                        .getFirst()
                        .boards()
                        .getFirst()
                        .second()
                        .reachedMarginal()
                        .values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .min()
                        .orElseThrow();
        var thresholds =
                new SixMaxRetainedContinuationCoverage.Settings(
                        initial.histories().getFirst().probability(),
                        initial.reach().fractionOfHeadsUpProbabilitySelected(),
                        comboBoundary,
                        2);
        var boundary = SixMaxRetainedContinuationCoverage.assess(game, policy, thresholds);
        assertTrue(boundary.criteriaMet());
        assertEquals("CONTENT_CRITERIA_MET", boundary.status());
        assertTrue(boundary.reach().selectedPhysicalFlopProbability() < 1e-6);
        assertFalse(
                SixMaxRetainedContinuationCoverage.assess(
                                game,
                                policy,
                                new SixMaxRetainedContinuationCoverage.Settings(
                                        Math.nextUp(thresholds.minimumHistoryProbability()),
                                        thresholds.minimumHeadsUpFraction(),
                                        comboBoundary,
                                        2))
                        .criteriaMet());
        assertFalse(
                SixMaxRetainedContinuationCoverage.assess(
                                game,
                                policy,
                                new SixMaxRetainedContinuationCoverage.Settings(
                                        thresholds.minimumHistoryProbability(),
                                        Math.nextUp(thresholds.minimumHeadsUpFraction()),
                                        comboBoundary,
                                        2))
                        .criteriaMet());
        assertFalse(
                SixMaxRetainedContinuationCoverage.assess(
                                game,
                                policy,
                                new SixMaxRetainedContinuationCoverage.Settings(
                                        thresholds.minimumHistoryProbability(),
                                        thresholds.minimumHeadsUpFraction(),
                                        Math.nextUp(comboBoundary),
                                        2))
                        .criteriaMet());
    }

    @Test
    void dominantRetainedHandFailsWithoutErasingCounterfactualWorlds() {
        var base = fourDealBase();
        var selection = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d");
        var game = new SixMaxConnectedPreflopGame(base, List.of(selection));
        var initial = uniform(base);
        var rows = new LinkedHashMap<>(initial.strategy());
        for (var root : base.chanceOutcomes(base.initialState())) {
            var state = root.state();
            for (var action : selection.history()) {
                if (action.seat() == PreflopAllInSpot.Seat.BTN) {
                    double weight = base.dealtHands(state).get(3).key().equals("Jc Jd") ? 1e-6 : 1;
                    rows.put(
                            base.currentPlayer(state) + ":" + base.informationSet(state),
                            chosenRow(base, state, action.action(), weight));
                }
                state = base.afterAction(state, action.action());
            }
        }
        var report =
                SixMaxRetainedContinuationCoverage.assess(
                        game, new CfrSolution(1, rows), permissive());
        var board = report.histories().getFirst().boards().getFirst();
        assertEquals(4, board.counterfactualJointDeals());
        assertEquals(4, board.reachedJointDeals());
        assertEquals(2, board.second().counterfactualMarginal().size());
        assertEquals(1, board.second().materialReachedCombos());
        assertTrue(board.second().largestReachedComboMass() > .99999);
        assertEquals(List.of("BTN:RETAINED_HAND_MIX_TOO_NARROW"), board.failures());
        assertFalse(report.criteriaMet());
        assertEquals(initial.strategy().keySet(), rows.keySet());
    }

    static Map<String, Double> chosenRow(
            SixMaxPreflopCheckdownGame base,
            SixMaxPreflopCheckdownGame.State state,
            String chosen,
            double weight) {
        var other =
                base.legalActions(state).stream()
                        .filter(a -> !a.equals(chosen))
                        .findFirst()
                        .orElseThrow();
        var row = new LinkedHashMap<String, Double>();
        for (var action : base.legalActions(state))
            row.put(action, action.equals(chosen) ? weight : action.equals(other) ? 1 - weight : 0);
        return row;
    }

    @Test
    void zeroReachAndNumericUnderflowHaveDifferentDiagnosesAndPosteriors() {
        var base = fourDealBase();
        var selection = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d");
        var game = new SixMaxConnectedPreflopGame(base, List.of(selection));
        for (double probability : new double[] {0, 1e-100}) {
            var rows = new LinkedHashMap<>(uniform(base).strategy());
            for (var root : base.chanceOutcomes(base.initialState())) {
                var state = root.state();
                for (var action : selection.history()) {
                    rows.put(
                            base.currentPlayer(state) + ":" + base.informationSet(state),
                            chosenRow(base, state, action.action(), probability));
                    state = base.afterAction(state, action.action());
                }
            }
            var report =
                    SixMaxRetainedContinuationCoverage.assess(
                            game, new CfrSolution(1, rows), permissive());
            var history = report.histories().getFirst();
            assertEquals(0, history.probability());
            assertFalse(report.criteriaMet());
            var board = history.boards().getFirst();
            assertEquals(4, board.counterfactualJointDeals());
            if (probability == 0) {
                assertEquals("ZERO_POLICY_REACH", history.reachStatus());
                assertNull(history.logProbability());
                assertEquals(0, board.reachedJointDeals());
                assertEquals(0, board.first().effectiveReachedCombos());
                assertTrue(board.failures().contains("BOARD_UNREACHED"));
            } else {
                assertEquals("NUMERIC_UNDERFLOW", history.reachStatus());
                assertTrue(Double.isFinite(history.logProbability()));
                assertEquals(4, board.reachedJointDeals());
                assertEquals(1.0 / 9880, board.probabilityGivenHistory(), 1e-15);
                assertEquals(2, board.first().materialReachedCombos());
                assertFalse(board.failures().contains("BOARD_UNREACHED"));
            }
        }
    }

    @Test
    void invalidThresholdsAndIncompletePreflopPoliciesFailExplicitly() {
        for (double invalid : new double[] {0, -1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxRetainedContinuationCoverage.Settings(invalid, .25, .05, 2));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxRetainedContinuationCoverage.Settings(.001, invalid, .05, 2));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxRetainedContinuationCoverage.Settings(.001, .25, invalid, 2));
        }
        for (int count : new int[] {0, 13})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxRetainedContinuationCoverage.Settings(.001, .25, .05, count));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxRetainedContinuationCoverage.Settings(.001, .25, .5, 3));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRetainedContinuationCoverage.assess(
                                SixMaxConnectedPreflopGameTest.game(),
                                new CfrSolution(1, Map.of()),
                                permissive()));
    }

    @Test
    void reachedHistoryCanHaveAnUnreachedBoardWithoutLosingCounterfactualSupport() {
        var base = fourDealBase();
        var selection = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d", "9s 2d 3d");
        var game = new SixMaxConnectedPreflopGame(base, List.of(selection));
        var rows = new LinkedHashMap<>(uniform(base).strategy());
        for (var root : base.chanceOutcomes(base.initialState())) {
            var state = root.state();
            for (var action : selection.history()) {
                if (action.seat() == PreflopAllInSpot.Seat.BB)
                    rows.put(
                            base.currentPlayer(state) + ":" + base.informationSet(state),
                            chosenRow(
                                    base,
                                    state,
                                    action.action(),
                                    base.dealtHands(state).get(5).key().equals("9c 9d") ? 0 : 1));
                state = base.afterAction(state, action.action());
            }
        }
        var report =
                SixMaxRetainedContinuationCoverage.assess(
                        game, new CfrSolution(1, rows), permissive());
        var history = report.histories().getFirst();
        assertEquals("POSITIVE_REACH", history.reachStatus());
        assertTrue(history.probability() > 0);
        assertEquals(2, history.boards().getFirst().reachedJointDeals());
        var blocked = history.boards().get(1);
        assertEquals(2, blocked.counterfactualJointDeals());
        assertEquals(0, blocked.reachedJointDeals());
        assertEquals(0, blocked.probabilityGivenHistory());
        assertTrue(blocked.first().reachedMarginal().isEmpty());
        assertTrue(blocked.second().reachedMarginal().isEmpty());
        assertTrue(blocked.failures().contains("BOARD_UNREACHED"));
        assertFalse(report.criteriaMet());
    }
}
