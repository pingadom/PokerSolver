package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPrivateSupportAuditTest {
    static SixMaxPreflopCheckdownGame eightDealBase() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var hands = base.dealtHands(base.chanceOutcomes(base.initialState()).getFirst().state());
        var ranges = new ArrayList<>(hands.stream().map(List::of).toList());
        ranges.set(0, List.of(combo("As Ah", 1), combo("Ac Ad", 3)));
        ranges.set(2, List.of(combo("Qs Qh", 1), combo("Qc Qd", 2)));
        ranges.set(3, List.of(combo("Js Jh", 1), combo("Jc Jd", 4)));
        return new SixMaxPreflopCheckdownGame(
                base.rules(),
                ranges,
                CashRakeRule.none(),
                (dealt, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    private static WeightedCombo combo(String cards, double weight) {
        return SixMaxConnectedPreflopGameTest.combo(cards, weight);
    }

    private static CfrSolution uniform(SixMaxPreflopCheckdownGame game) {
        return MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, new CfrSolution(1, Map.of()), 200_000)
                .solution();
    }

    @Test
    void eightWorldsRetainWeightedFoldedCardRemovalAndPrivateInformation() {
        var base = eightDealBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d", "As 2d 3d")));
        var policy = uniform(base);
        var audit = SixMaxPrivateSupportAudit.assess(game, policy);
        assertEquals(8, audit.sourceJointDeals());
        assertEquals(List.of(Seat.UTG, Seat.CO, Seat.BTN), audit.uncertainSeats());
        assertEquals(.25, audit.sourceMarginals().get(Seat.UTG).get("Ah As"), 1e-12);
        assertEquals(.75, audit.sourceMarginals().get(Seat.UTG).get("Ac Ad"), 1e-12);
        var open = audit.boards().getFirst();
        assertEquals(8, open.counterfactualJointDeals());
        assertEquals(8, open.reachedJointDeals());
        assertEquals(1, open.compatiblePriorMass(), 1e-12);
        assertEquals(1.0 / 9880, open.priorPhysicalFlopProbability(), 1e-15);
        assertEquals(List.of(Seat.UTG, Seat.CO), open.uncertainFoldedSeats());
        assertEquals(1, open.firstPlayerRootInformationSets());
        assertEquals(2, open.secondPlayerRootInformationSets());
        var blocked = audit.boards().get(1);
        assertEquals(4, blocked.counterfactualJointDeals());
        assertEquals(.75, blocked.compatiblePriorMass(), 1e-12);
        assertEquals(.75 / 9880, blocked.priorPhysicalFlopProbability(), 1e-15);
        assertEquals(List.of(Seat.CO), blocked.uncertainFoldedSeats());
        assertEquals(
                java.util.Set.of("Ac Ad"),
                blocked.counterfactualMarginals().get(Seat.UTG).keySet());
        assertEquals(1, blocked.counterfactualMarginals().get(Seat.UTG).get("Ac Ad"), 1e-12);
        assertEquals(
                open.policyHistoryReach() * .75 / 9880,
                blocked.reachedPhysicalFlopProbability(),
                1e-15);
        assertThrows(
                UnsupportedOperationException.class,
                () -> audit.sourceMarginals().get(Seat.UTG).put("xx", 1.0));

        // Changing only concealed UTG/CO hands cannot reveal the world at any later street.
        var groups = new LinkedHashMap<String, List<SixMaxConnectedPreflopGame.State>>();
        for (var root : base.chanceOutcomes(base.initialState())) {
            var pre =
                    game.replayPreflop(
                            SixMaxConnectedPreflopGameTest.HISTORY, root.state().dealIndex());
            var flop = game.chanceOutcomes(pre).getFirst().state();
            groups.computeIfAbsent(
                            base.dealtHands(root.state()).get(3).key(), k -> new ArrayList<>())
                    .add(flop);
        }
        for (var worlds : groups.values()) {
            assertEquals(4, worlds.size());
            var keys = new ArrayList<List<String>>();
            for (var flop : worlds) {
                var row = new ArrayList<String>();
                row.add(game.informationSet(flop));
                var second = game.afterAction(flop, "check");
                row.add(game.informationSet(second));
                var turnChance = game.afterAction(second, "check");
                var turn =
                        game.chanceOutcomes(turnChance).stream()
                                .filter(o -> o.state().postflop().turn().equals(Card.parse("5d")))
                                .findFirst()
                                .orElseThrow()
                                .state();
                row.add(game.informationSet(turn));
                var riverChance = game.afterAction(game.afterAction(turn, "check"), "check");
                var river =
                        game.chanceOutcomes(riverChance).stream()
                                .filter(o -> o.state().postflop().river().equals(Card.parse("6d")))
                                .findFirst()
                                .orElseThrow()
                                .state();
                row.add(game.informationSet(river));
                keys.add(row);
            }
            for (var key : keys) assertEquals(keys.getFirst(), key);
        }
    }

    @Test
    void changedAndZeroPolicyReachAreDistinctFromCounterfactualSupport() {
        var base = eightDealBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d", "As 2d 3d")));
        var uniform = uniform(base);
        var rows = new LinkedHashMap<>(uniform.strategy());
        for (var root : base.chanceOutcomes(base.initialState()))
            if (base.dealtHands(root.state()).getFirst().key().equals("Ah As")) {
                var key = "0:" + base.informationSet(root.state());
                var row = new LinkedHashMap<>(rows.get(key));
                row.replaceAll((action, probability) -> action.equals("call") ? 1.0 : 0.0);
                rows.put(key, row);
            }
        var changed = SixMaxPrivateSupportAudit.assess(game, new CfrSolution(1, rows));
        assertEquals(8, changed.boards().getFirst().counterfactualJointDeals());
        assertEquals(4, changed.boards().getFirst().reachedJointDeals());
        assertEquals(
                Map.of("Ac Ad", 1.0), changed.boards().getFirst().reachedMarginals().get(Seat.UTG));
        assertEquals(
                changed.boards().getFirst().reachedPhysicalFlopProbability(),
                changed.boards().get(1).reachedPhysicalFlopProbability(),
                1e-15);
        rows.replaceAll(
                (key, row) -> {
                    var pure = new LinkedHashMap<>(row);
                    var choice = row.containsKey("fold") ? "fold" : row.keySet().iterator().next();
                    pure.replaceAll((action, probability) -> action.equals(choice) ? 1.0 : 0.0);
                    return pure;
                });
        var zero = SixMaxPrivateSupportAudit.assess(game, new CfrSolution(1, rows));
        for (var board : zero.boards()) {
            assertEquals(0, board.policyHistoryReach());
            assertEquals(0, board.reachedPhysicalFlopProbability());
            assertEquals(0, board.reachedJointDeals());
            assertTrue(board.reachedMarginals().isEmpty());
            assertTrue(board.counterfactualJointDeals() > 0);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPrivateSupportAudit.assess(game, new CfrSolution(1, Map.of())));
    }

    @Test
    void eightDealPreflightRejectsExcessFlopSupportRatherThanTrimmingWorlds() {
        var base = eightDealBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(
                                SixMaxConnectedPreflopGameTest.selection(
                                        "2d 3d 4d", "2c 3c 4c", "2s 3s 4s")));
        assertEquals(8, game.chanceOutcomes(game.initialState()).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxContinuationStudyBudget.widerFlops().validate(game));
        assertEquals(
                24,
                game.coverage().getFirst().legalSelectedFlopsByDeal().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
    }

    @Test
    void positiveLikelihoodUnderflowDoesNotEraseReachedBeliefs() {
        var base = eightDealBase();
        var selection = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d");
        var game = new SixMaxConnectedPreflopGame(base, List.of(selection));
        var rows = new LinkedHashMap<>(uniform(base).strategy());
        for (var root : base.chanceOutcomes(base.initialState())) {
            var state = root.state();
            for (var action : selection.history()) {
                String key = base.currentPlayer(state) + ":" + base.informationSet(state);
                var legal = base.legalActions(state);
                String other = legal.stream().filter(a -> !a.equals(action.action())).findFirst().orElseThrow();
                var row = new LinkedHashMap<String, Double>();
                for (var name : legal) row.put(name, name.equals(action.action()) ? 1e-100 : name.equals(other) ? 1.0 : 0.0);
                rows.put(key, row);
                state = base.afterAction(state, action.action());
            }
        }
        var report = SixMaxPrivateSupportAudit.assess(game, new CfrSolution(1, rows));
        var board = report.boards().getFirst();
        assertEquals(0, board.policyHistoryReach());
        assertEquals(0, board.reachedPhysicalFlopProbability());
        assertEquals(8, board.reachedJointDeals());
        assertEquals(6, board.reachedMarginals().size());
        for (var marginal : board.reachedMarginals().values()) assertEquals(1, marginal.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }
}
