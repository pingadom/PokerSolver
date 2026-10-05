package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxReachedContinuationStudyTest {
    static SixMaxPreflopSolutionPack eightDealSource() throws Exception {
        return MultiwayPackJson.readFullRound(
                java.nio.file.Files.readString(
                        java.nio.file.Path.of("../docs/data/sixmax-eight-deal-source-pack.json")));
    }

    @Test
    void diverseSelectionChoosesMaterialHandsOnDifferentSeatPairsWithoutPruningRoots()
            throws Exception {
        var pack = eightDealSource();
        var base = pack.rebuildGame();
        var settings = SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05);
        var plan =
                SixMaxReachedContinuationStudy.select(
                        base,
                        pack.solution(),
                        2,
                        1,
                        711,
                        SixMaxContinuationStudyBudget.widerFlops(),
                        settings);
        assertEquals(
                List.of(1, 5),
                plan.selectedHistories().stream()
                        .map(SixMaxReachedContinuationStudy.SelectedHistory::sourceReachRank)
                        .toList());
        assertEquals(8, plan.game().chanceOutcomes(plan.game().initialState()).size());
        assertEquals(12, plan.compatibleDealFlops());
        assertEquals(
                new SixMaxContinuationStudyBudget.Cost(12, 1_409_473),
                SixMaxContinuationStudyBudget.widerFlops().validate(plan.game()));
        assertEquals(settings, plan.selectionAudit().settings());
        var considered = plan.selectionAudit().considered();
        assertEquals(5, considered.size());
        assertEquals(
                List.of(
                        "SELECTED",
                        "DUPLICATE_ACTIVE_PAIR",
                        "DUPLICATE_ACTIVE_PAIR",
                        "INSUFFICIENT_ACTIVE_HAND_MASS",
                        "SELECTED"),
                considered.stream()
                        .map(SixMaxReachedContinuationStudy.CandidateAssessment::disposition)
                        .toList());
        var cutoff = considered.get(4);
        assertEquals(Seat.CO, cutoff.firstToAct());
        assertEquals(Seat.BTN, cutoff.secondToAct());
        assertEquals(List.of("3h", "Ac", "Ad"), cutoff.flops().getFirst().flop());
        assertEquals(.602633, cutoff.flops().getFirst().first().get("Ah Kh"), 1e-6);
        assertEquals(.750795, cutoff.flops().getFirst().second().get("8h 8s"), 1e-6);
        for (var candidate : considered)
            if (candidate.disposition().equals("SELECTED"))
                for (var flop : candidate.flops())
                    for (var marginal : List.of(flop.first(), flop.second())) {
                        assertEquals(
                                1,
                                marginal.values().stream().mapToDouble(Double::doubleValue).sum(),
                                1e-12);
                        assertEquals(2, marginal.values().stream().filter(v -> v >= .05).count());
                        assertThrows(UnsupportedOperationException.class, () -> marginal.clear());
                    }
        var support = SixMaxPrivateSupportAudit.assess(plan.game(), pack.solution());
        assertEquals(4, support.boards().get(1).counterfactualJointDeals());
        assertEquals(1, support.boards().get(1).counterfactualMarginals().get(Seat.BB).size());
        assertEquals(2, support.boards().get(1).reachedMarginals().get(Seat.CO).size());
        assertEquals(
                plan.selectedHistories(),
                SixMaxReachedContinuationStudy.select(
                                base,
                                pack.solution(),
                                2,
                                1,
                                711,
                                SixMaxContinuationStudyBudget.widerFlops(),
                                settings)
                        .selectedHistories());
    }

    @Test
    void materialHandRequirementAppliesToEveryFlopRatherThanOnlyTheFirst() throws Exception {
        var pack = eightDealSource();
        var base = pack.rebuildGame();
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var settings = SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05);
        var narrow =
                SixMaxReachedContinuationStudy.select(
                        base, pack.solution(), 1, 1, 711, budget, settings);
        assertEquals(1, narrow.selectedHistories().getFirst().sourceReachRank());
        var wide =
                SixMaxReachedContinuationStudy.select(
                        base, pack.solution(), 1, 2, 711, budget, settings);
        assertEquals(5, wide.selectedHistories().getFirst().sourceReachRank());
        var firstCandidate = wide.selectionAudit().considered().getFirst();
        assertEquals("INSUFFICIENT_ACTIVE_HAND_MASS", firstCandidate.disposition());
        assertEquals(2, firstCandidate.flops().getFirst().second().size());
        assertEquals(Map.of("Js Ts", 1.0), firstCandidate.flops().get(1).second());
        assertEquals(2, wide.selectionAudit().considered().get(4).flops().size());
        assertEquals(8, wide.game().chanceOutcomes(wide.game().initialState()).size());
    }

    @Test
    void diverseSelectionFailsWhenItsCandidateWindowOrHandMassCannotMeetTheRequest()
            throws Exception {
        var pack = eightDealSource();
        var base = pack.rebuildGame();
        for (var settings :
                List.of(
                        new SixMaxReachedContinuationStudy.SelectionSettings(
                                SixMaxReachedContinuationStudy.SelectionMode.DIVERSE_ACTIVE_PAIRS,
                                4,
                                .05),
                        SixMaxReachedContinuationStudy.SelectionSettings.diverse(.49)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxReachedContinuationStudy.select(
                                    base,
                                    pack.solution(),
                                    2,
                                    1,
                                    711,
                                    SixMaxContinuationStudyBudget.widerFlops(),
                                    settings));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReachedContinuationStudy.select(
                                base,
                                pack.solution(),
                                2,
                                1,
                                711,
                                new SixMaxContinuationStudyBudget(11, 2_000_000),
                                SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05)));
        for (double invalid : new double[] {0, -.1, .50001, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxReachedContinuationStudy.SelectionSettings.diverse(invalid));
    }

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
    void widerMenusAreNestedUniqueAndUsePhysicalBlockerProbabilities() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var policy = SixMaxConditionalPostflopRefinementTest.uniform(base);
        var narrow = SixMaxReachedContinuationStudy.select(base, policy, 2, 711);
        var wide =
                SixMaxReachedContinuationStudy.select(
                        base, policy, 2, 2, 711, SixMaxContinuationStudyBudget.widerFlops());
        var repeated =
                SixMaxReachedContinuationStudy.select(
                        base, policy, 2, 2, 711, SixMaxContinuationStudyBudget.widerFlops());
        assertEquals(wide.selectedHistories(), repeated.selectedHistories());
        assertEquals(
                narrow.sourceReach().selectedHistoryProbability(),
                wide.sourceReach().selectedHistoryProbability());
        assertEquals(
                narrow.sourceReach().headsUpProbability(), wide.sourceReach().headsUpProbability());
        double physicalReach = 0;
        int pairs = 0;
        for (int rank = 0; rank < narrow.selectedHistories().size(); rank++) {
            var before = narrow.selectedHistories().get(rank);
            var after = wide.selectedHistories().get(rank);
            assertEquals(before.coverage().actions(), after.coverage().actions());
            assertEquals(before.coverage().flops(), after.coverage().flops().subList(0, 1));
            assertEquals(2, after.coverage().flops().stream().distinct().count());
            var posterior =
                    new SixMaxPolicyFlopTransition(base, policy, after.coverage().actions());
            var support =
                    SixMaxPolicyFlopTransition.counterfactualSupport(
                            base, after.coverage().actions());
            for (var flop : after.coverage().flops()) {
                var cards = flop.stream().map(com.pokerlab.core.card.Card::parse).toList();
                physicalReach += after.sourceReachProbability() * posterior.flopProbability(cards);
                pairs += support.conditionOnFlop(cards).deals().size();
            }
        }
        assertEquals(pairs, wide.compatibleDealFlops());
        assertEquals(physicalReach, wide.sourceReach().selectedPhysicalFlopProbability(), 1e-15);
        assertTrue(physicalReach > narrow.sourceReach().selectedPhysicalFlopProbability());
        assertTrue(
                physicalReach
                        <= 2 * narrow.sourceReach().selectedHistoryProbability() / 9880 + 1e-15);
        for (int width : List.of(0, 5))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxReachedContinuationStudy.select(
                                    base,
                                    policy,
                                    2,
                                    width,
                                    711,
                                    SixMaxContinuationStudyBudget.widerFlops()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReachedContinuationStudy.select(
                                base,
                                policy,
                                2,
                                2,
                                711,
                                new SixMaxContinuationStudyBudget(1, 2_000_000)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReachedContinuationStudy.select(
                                base, policy, 2, 2, 711, new SixMaxContinuationStudyBudget(16, 1)));
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
        wider.set(5, List.of(hands.get(5), SixMaxConnectedPreflopGameTest.combo("5c 5d", 1)));
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
        assertEquals(12, oversized.chanceOutcomes(oversized.initialState()).size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxConnectedPreflopGame(
                                oversized,
                                List.of(SixMaxConnectedPreflopGameTest.selection("6c 7c 8c"))));
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
