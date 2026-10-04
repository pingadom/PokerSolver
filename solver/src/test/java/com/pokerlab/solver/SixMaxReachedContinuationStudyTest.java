package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxReachedContinuationStudyTest {
    @Test
    void ranksMultipleHistoriesAndAccountsForPhysicalAndUnselectedMass() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var policy = SixMaxConditionalPostflopRefinementTest.uniform(base);
        var expected = SixMaxPreflopContinuationAudit.assess(base, policy, 711, 2);
        var plan = SixMaxReachedContinuationStudy.select(base, policy, 2, 711);
        assertEquals(2, plan.selectedHistories().size());
        assertEquals(2, plan.game().selections().size());
        assertTrue(plan.compatibleDealFlops() >= 2 && plan.compatibleDealFlops() <= 4);
        double selected = 0;
        for (int index = 0; index < 2; index++) {
            var history = plan.selectedHistories().get(index);
            var example = expected.examples().get(index);
            assertEquals(index + 1, history.sourceReachRank());
            assertEquals(example.history(), history.coverage().actions());
            assertEquals(example.reachProbability(), history.sourceReachProbability(), 1e-15);
            assertEquals(example.posteriorJointDeals(), history.sourcePosteriorJointDeals());
            selected += example.reachProbability();
        }
        var reach = plan.sourceReach();
        assertEquals(selected, reach.selectedHistoryProbability(), 1e-12);
        assertEquals(
                reach.headsUpProbability(),
                reach.selectedHistoryProbability() + reach.unselectedHeadsUpProbability(),
                1e-12);
        assertEquals(
                selected,
                reach.selectedPhysicalFlopProbability()
                        + reach.selectedHistoryOtherFlopsProbability(),
                1e-12);
        assertTrue(reach.unselectedHeadsUpProbability() > 0);
        assertTrue(reach.selectedPhysicalFlopProbability() <= selected / 9880 + 1e-15);
        assertEquals(
                selected / reach.headsUpProbability(),
                reach.fractionOfHeadsUpProbabilitySelected(),
                1e-12);
        assertEquals(
                1,
                reach.terminalProbability().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-12);
        assertEquals(reach, SixMaxReachedContinuationStudy.reach(plan.game(), policy));
        var repeat = SixMaxReachedContinuationStudy.select(base, policy, 2, 711);
        assertEquals(plan.selectedHistories(), repeat.selectedHistories());
        assertEquals(plan.sourceReach(), repeat.sourceReach());
    }

    @Test
    void refusesTooManyCompatibleBranchesRatherThanTrimmingHiddenDeals() throws Exception {
        var pack =
                MultiwayPackJson.readFullRound(
                        java.nio.file.Files.readString(
                                java.nio.file.Path.of(
                                        "../docs/data/sixmax-diverse-source-pack.json")));
        var base = pack.rebuildGame();
        var uniform = SixMaxConditionalPostflopRefinementTest.uniform(base);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudy.select(base, uniform, 4, 711));
    }

    @Test
    void preservesCounterfactualSupportAndHandlesAChangedPolicyWithZeroHeadsUpReach() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var history =
                List.of(
                        new PublicAction(Seat.UTG, "call"), new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"), new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"), new PublicAction(Seat.BB, "check"));
        var folded = new LinkedHashMap<String, Map<String, Double>>();
        for (var row : SixMaxConditionalPostflopRefinementTest.uniform(base).strategy().entrySet())
            folded.put(row.getKey(), pure(row.getValue(), "fold"));
        var reached = new LinkedHashMap<>(folded);
        for (var outcome : base.chanceOutcomes(base.initialState())) {
            var state = outcome.state();
            for (var action : history) {
                String key = base.currentPlayer(state) + ":" + base.informationSet(state);
                reached.put(
                        key,
                        pure(
                                reached.get(key),
                                action.seat() == Seat.UTG && state.dealIndex() == 0
                                        ? "fold"
                                        : action.action()));
                state = base.afterAction(state, action.action());
            }
        }
        var plan = SixMaxReachedContinuationStudy.select(base, new CfrSolution(1, reached), 2, 711);
        assertEquals(1, plan.selectedHistories().size());
        assertEquals(history, plan.selectedHistories().getFirst().coverage().actions());
        assertEquals(1, plan.selectedHistories().getFirst().sourcePosteriorJointDeals());
        assertEquals(2, plan.game().chanceOutcomes(plan.game().initialState()).size());
        assertEquals(
                2, SixMaxPolicyFlopTransition.counterfactualSupport(base, history).deals().size());
        assertEquals(.75, plan.sourceReach().selectedHistoryProbability(), 1e-12);
        assertEquals(1, plan.sourceReach().fractionOfHeadsUpProbabilitySelected(), 1e-12);
        var zero = new CfrSolution(1, folded);
        var zeroReach = SixMaxReachedContinuationStudy.reach(plan.game(), zero);
        assertEquals(0, zeroReach.headsUpProbability());
        assertEquals(0, zeroReach.selectedHistoryProbability());
        assertEquals(0, zeroReach.fractionOfHeadsUpProbabilitySelected());
        assertEquals(0, zeroReach.selectedPhysicalFlopProbability());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudy.select(base, zero, 2, 711));
    }

    @Test
    void rejectsIncompletePoliciesAndOversizedPrivateSupportBeforeBuildingPostflop() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var uniform = SixMaxConditionalPostflopRefinementTest.uniform(base);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudy.select(base, uniform, 0, 711));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudy.select(base, uniform, 5, 711));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReachedContinuationStudy.select(
                                base, new CfrSolution(1, Map.of()), 2, 711));
        var hands = base.dealtHands(base.chanceOutcomes(base.initialState()).getFirst().state());
        var ranges = hands.stream().map(List::of).toList();
        var wider = new java.util.ArrayList<>(ranges);
        wider.set(
                0,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("As Ah", 1),
                        SixMaxConnectedPreflopGameTest.combo("Ac Ad", 1),
                        SixMaxConnectedPreflopGameTest.combo("2c 2d", 1)));
        wider.set(3, List.of(hands.get(3), SixMaxConnectedPreflopGameTest.combo("3c 3d", 1)));
        var oversized =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0, 100.0)),
                        wider,
                        CashRakeRule.none(),
                        (h, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        assertEquals(6, oversized.chanceOutcomes(oversized.initialState()).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudy.select(oversized, uniform, 1, 711));
    }

    private static Map<String, Double> pure(Map<String, Double> row, String action) {
        String chosen = row.containsKey(action) ? action : row.keySet().iterator().next();
        var result = new LinkedHashMap<String, Double>();
        row.keySet().forEach(a -> result.put(a, a.equals(chosen) ? 1.0 : 0.0));
        return result;
    }
}
