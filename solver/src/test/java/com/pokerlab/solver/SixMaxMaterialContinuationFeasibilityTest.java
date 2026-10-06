package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxMaterialContinuationFeasibilityTest {
    static CfrSolution forced(SixMaxPreflopCheckdownGame game, double likelihood) {
        var rows =
                new LinkedHashMap<>(
                        SixMaxRetainedContinuationCoverageTest.uniform(game).strategy());
        for (var root : game.chanceOutcomes(game.initialState())) {
            var state = root.state();
            for (var action : SixMaxConnectedPreflopGameTest.HISTORY) {
                rows.put(
                        game.currentPlayer(state) + ":" + game.informationSet(state),
                        SixMaxRetainedContinuationCoverageTest.chosenRow(
                                game, state, action.action(), likelihood));
                state = game.afterAction(state, action.action());
            }
        }
        return new CfrSolution(1, rows);
    }

    private static SixMaxMaterialContinuationFeasibility.Report assess(
            SixMaxPreflopCheckdownGame game, CfrSolution policy) {
        return SixMaxMaterialContinuationFeasibility.assess(
                game, policy, SixMaxMaterialContinuationFeasibility.Settings.researchDefault());
    }

    @Test
    void exhaustiveBoardsKeepAllPhysicalBlockersAndInclusiveMaterialBoundary() {
        var game = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var policy = forced(game, 1);
        var before = policy.strategy();
        var settings =
                new SixMaxMaterialContinuationFeasibility.Settings(
                        20, 1, new SixMaxRetainedContinuationCoverage.Settings(1, 1, .2, 2));
        var report = SixMaxMaterialContinuationFeasibility.assess(game, policy, settings);
        assertEquals("NOT_RULED_OUT", report.status());
        assertEquals(1, report.headsUpProbability(), 1e-12);
        assertEquals(1, report.optimisticHeadsUpFraction(), 1e-12);
        assertEquals(0, report.unexaminedHistoryProbability());
        assertEquals(4, report.rootPrivateDeals());
        assertEquals(22_100, report.boardsEnumeratedPerHistory());
        var history = report.histories().getFirst();
        // Eight fixed-seat cards and eight alternative active-seat cards must all be absent.
        assertEquals(36 * 35 * 34 / 6, history.optimisticMaterialFlops());
        assertEquals(4, history.minimumCompatibleCounterfactualDeals());
        assertEquals(SixMaxConnectedPreflopGameTest.HISTORY, history.history());
        assertEquals(before, policy.strategy());
        assertEquals(report, SixMaxMaterialContinuationFeasibility.assess(game, policy, settings));
        assertThrows(UnsupportedOperationException.class, () -> report.histories().clear());
        assertThrows(UnsupportedOperationException.class, () -> history.exampleFlop().clear());
    }

    @Test
    void boardConditioningCanRemoveADominantHandWithoutPruningRootWorlds() {
        var original = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var hands =
                original.dealtHands(
                        original.chanceOutcomes(original.initialState()).getFirst().state());
        var ranges = new ArrayList<>(hands.stream().map(List::of).toList());
        ranges.set(
                3,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("Js Jh", 100),
                        SixMaxConnectedPreflopGameTest.combo("Jc Jd", 1),
                        SixMaxConnectedPreflopGameTest.combo("Qc Qd", 1)));
        ranges.set(
                5,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("9s 9h", 1),
                        SixMaxConnectedPreflopGameTest.combo("9c 9d", 1)));
        var game =
                new SixMaxPreflopCheckdownGame(
                        original.rules(),
                        ranges,
                        CashRakeRule.none(),
                        (dealt, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var report = assess(game, forced(game, 1));
        assertEquals("NOT_RULED_OUT", report.status());
        assertEquals(6, report.rootPrivateDeals());
        assertTrue(report.histories().getFirst().optimisticMaterialFlops() > 0);
        assertEquals(4, report.histories().getFirst().minimumCompatibleCounterfactualDeals());
    }

    @Test
    void narrowActiveRangesCannotBecomeMaterialOnAnyFlop() {
        var game = SixMaxConnectedPreflopGameTest.base();
        var report = assess(game, forced(game, 1));
        assertEquals("INFEASIBLE_UNDER_FIXED_POLICY", report.status());
        assertEquals(0, report.optimisticHeadsUpFraction());
        assertEquals(0, report.histories().getFirst().optimisticMaterialFlops());
        assertNull(report.histories().getFirst().minimumCompatibleCounterfactualDeals());
        assertTrue(report.histories().getFirst().exampleFlop().isEmpty());
    }

    @Test
    void tailIsFullyCreditedAndMoreEvidenceOnlyTightensTheBound() {
        var game = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var policy = SixMaxRetainedContinuationCoverageTest.uniform(game);
        var coverage = SixMaxRetainedContinuationCoverage.Settings.researchDefault();
        var small =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(1, 1, coverage));
        var larger =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(20, 1, coverage));
        var four =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(20, 4, coverage));
        assertTrue(small.unexaminedHistoryProbability() > 0);
        assertEquals(
                small.headsUpProbability() - small.histories().getFirst().probability(),
                small.unexaminedHistoryProbability(),
                1e-12);
        assertTrue(
                larger.optimisticSelectedHistoryProbability()
                        <= small.optimisticSelectedHistoryProbability() + 1e-12);
        assertTrue(
                four.optimisticSelectedHistoryProbability()
                        >= larger.optimisticSelectedHistoryProbability());
        assertTrue(four.optimisticSelectedHistoryProbability() <= four.headsUpProbability());
    }

    @Test
    void zeroReachAndUnderflowCannotBeConfusedWithAnInfeasibilityCertificate() {
        var game = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var rows = new LinkedHashMap<>(forced(game, 1).strategy());
        for (var entry : rows.entrySet()) {
            var row = new LinkedHashMap<String, Double>();
            String chosen =
                    entry.getValue().containsKey("fold")
                            ? "fold"
                            : entry.getValue().keySet().iterator().next();
            for (var action : entry.getValue().keySet())
                row.put(action, action.equals(chosen) ? 1.0 : 0.0);
            entry.setValue(row);
        }
        assertEquals("NO_HEADS_UP_REACH", assess(game, new CfrSolution(1, rows)).status());
        var underflow = assess(game, forced(game, 1e-100));
        assertTrue(underflow.numericReachUnresolved());
        assertEquals("NUMERIC_REACH_UNRESOLVED", underflow.status());
    }

    @Test
    void historySlotsAndAbsoluteReachFloorApplyBeforeCoverageIsCredited() {
        var game = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var rows = new LinkedHashMap<>(forced(game, 1).strategy());
        for (var root : game.chanceOutcomes(game.initialState())) {
            var state = root.state();
            for (int i = 0; i < 3; i++) state = game.afterAction(state, "fold");
            var row = new LinkedHashMap<String, Double>();
            for (var action : game.legalActions(state))
                row.put(action, action.equals("call") || action.equals("raise:3.0") ? .5 : 0);
            rows.put(game.currentPlayer(state) + ":" + game.informationSet(state), row);
            state = game.afterAction(state, "call");
            rows.put(
                    game.currentPlayer(state) + ":" + game.informationSet(state),
                    SixMaxRetainedContinuationCoverageTest.chosenRow(game, state, "fold", 1));
            state = game.afterAction(state, "fold");
            rows.put(
                    game.currentPlayer(state) + ":" + game.informationSet(state),
                    SixMaxRetainedContinuationCoverageTest.chosenRow(game, state, "check", 1));
        }
        var policy = new CfrSolution(1, rows);
        var coverage = SixMaxRetainedContinuationCoverage.Settings.researchDefault();
        var one =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(20, 1, coverage));
        var two =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(20, 2, coverage));
        assertEquals(2, one.reachedHeadsUpHistories());
        assertEquals(0, one.unexaminedHistoryProbability());
        assertEquals(.5, one.optimisticHeadsUpFraction(), 1e-12);
        assertEquals(1, two.optimisticHeadsUpFraction(), 1e-12);
        var floor =
                SixMaxMaterialContinuationFeasibility.assess(
                        game,
                        policy,
                        new SixMaxMaterialContinuationFeasibility.Settings(
                                20,
                                2,
                                new SixMaxRetainedContinuationCoverage.Settings(.75, .25, .05, 2)));
        assertEquals("INFEASIBLE_UNDER_FIXED_POLICY", floor.status());
        assertTrue(floor.histories().stream().allMatch(h -> h.optimisticMaterialFlops() > 0));
        assertTrue(floor.histories().stream().noneMatch(h -> h.eligibleForUpperBound()));
    }

    @Test
    void empiricalChanceRakeAndOverwidePrivateSupportAreNotCertified() {
        var original = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        var hands =
                original.dealtHands(
                        original.chanceOutcomes(original.initialState()).getFirst().state());
        MultiwayShowdownOracle oracle =
                (dealt, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                };
        var sample =
                new SixMaxJointDealSampler.Sample(
                        1, 1, 0, List.of(new SixMaxJointDealSampler.JointDeal(hands, 1)));
        var empirical =
                SixMaxPreflopCheckdownGame.fromSampledDeals(
                        original.rules(), sample, CashRakeRule.none(), oracle);
        assertThrows(
                IllegalArgumentException.class,
                () -> assess(empirical, new CfrSolution(1, Map.of())));
        var ranges = new ArrayList<>(hands.stream().map(List::of).toList());
        var raked =
                new SixMaxPreflopCheckdownGame(
                        original.rules(), ranges, new CashRakeRule(.05, 1, true), oracle);
        assertThrows(
                IllegalArgumentException.class, () -> assess(raked, new CfrSolution(1, Map.of())));
        ranges.set(
                0,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("As Ah", 1),
                        SixMaxConnectedPreflopGameTest.combo("Ac Ad", 1)));
        ranges.set(
                3,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("Js Jh", 1),
                        SixMaxConnectedPreflopGameTest.combo("Jc Jd", 1)));
        ranges.set(
                4,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("6s 6h", 1),
                        SixMaxConnectedPreflopGameTest.combo("6c 6d", 1)));
        ranges.set(
                5,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("9s 9h", 1),
                        SixMaxConnectedPreflopGameTest.combo("9c 9d", 1)));
        var wide =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0)),
                        ranges,
                        CashRakeRule.none(),
                        oracle);
        assertEquals(16, wide.chanceOutcomes(wide.initialState()).size());
        assertThrows(
                IllegalArgumentException.class, () -> assess(wide, new CfrSolution(1, Map.of())));
    }

    @Test
    void missingForeignAndIllegalRowsFailBeforeAuditing() {
        var game = SixMaxRetainedContinuationCoverageTest.fourDealBase();
        assertThrows(
                IllegalArgumentException.class, () -> assess(game, new CfrSolution(1, Map.of())));
        var rows = new LinkedHashMap<>(forced(game, 1).strategy());
        rows.put("foreign", Map.of("fold", 1.0));
        assertThrows(IllegalArgumentException.class, () -> assess(game, new CfrSolution(1, rows)));
        rows.remove("foreign");
        rows.put(rows.keySet().iterator().next(), Map.of("fold", .5));
        assertThrows(IllegalArgumentException.class, () -> assess(game, new CfrSolution(1, rows)));
        for (int limit : new int[] {0, 21})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxMaterialContinuationFeasibility.Settings(
                                    limit,
                                    1,
                                    SixMaxRetainedContinuationCoverage.Settings.researchDefault()));
        for (int count : new int[] {0, 5})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxMaterialContinuationFeasibility.Settings(
                                    20,
                                    count,
                                    SixMaxRetainedContinuationCoverage.Settings.researchDefault()));
    }
}
