package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxHeadsUpFlopDecisionEvaluatorTest {
    private static CfrSolution selectivePolicy(SixMaxHeadsUpFlopGame game, double betProbability) {
        return SixMaxHeadsUpFlopGameTest.policy(
                game,
                (state, action) -> {
                    if (state.history().isEmpty()) return action.equals("bet") ? 1.0 : 0.0;
                    if (state.history().equals("k")) {
                        double bet = game.ownHand(state).key().equals("Jh Js") ? betProbability : 0;
                        return action.equals("bet") ? bet : 1 - bet;
                    }
                    return action.equals("call") ? 1.0 : 0.0;
                });
    }

    @Test
    void opponentActionsConditionJointBeliefWhileOwnPriorCheckIsForced() {
        var game = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var evaluator = new SixMaxHeadsUpFlopDecisionEvaluator(game, selectivePolicy(game, 1));
        var decision = evaluator.evaluate(List.of("check", "bet"), "9h 9s");
        assertEquals(Seat.BB, decision.actingSeat());
        assertEquals(2, decision.compatibleJointDeals());
        assertEquals(Map.of("call", 1.0, "fold", 0.0), decision.actionFrequency());
        assertEquals(-3, decision.actionEvBb().get("fold"), 1e-12);
        double mass = 0, share = 0;
        for (int index = 0; index < game.flop().deals().size(); index++) {
            var deal = game.flop().deals().get(index);
            if (!deal.hands().get(Seat.BTN.ordinal()).key().equals("Jh Js")) continue;
            mass += deal.probability();
            share += deal.probability() * game.flop().exactCheckdown(index).shares().get(Seat.BB);
        }
        double expectedCall = 13 * share / mass - 6.25;
        assertEquals(expectedCall, decision.actionEvBb().get("call"), 1e-12);
        double naiveUnconditioned = 13 * game.flop().exactCheckdown().shares().get(Seat.BB) - 6.25;
        assertTrue(Math.abs(expectedCall - naiveUnconditioned) > 1);
        assertEquals(Math.max(0, expectedCall + 3), decision.evLossBb("fold"), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> decision.evLossBb("bet"));
        assertThrows(UnsupportedOperationException.class, () -> decision.actionEvBb().clear());
    }

    @Test
    void secondPlayerActionEvsRestoreActualChipUtilitiesRatherThanCenteredPayoffs() {
        var game = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var solution = selectivePolicy(game, 1);
        var evaluator = new SixMaxHeadsUpFlopDecisionEvaluator(game, solution);
        var decision = evaluator.evaluate(List.of("bet"), "Jh Js");
        assertEquals(Seat.BTN, decision.actingSeat());
        assertEquals(2, decision.compatibleJointDeals());
        assertEquals(-3, decision.actionEvBb().get("fold"), 1e-12);
        double expected = 0, mass = 0;
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var node = game.afterAction(outcome.state(), "bet");
            if (!game.ownHand(node).key().equals("Jh Js")) continue;
            mass += outcome.probability();
            expected +=
                    outcome.probability()
                            * game.terminalUtilitiesBb(game.afterAction(node, "call"))
                                    .get(Seat.BTN);
        }
        assertEquals(expected / mass, decision.actionEvBb().get("call"), 1e-12);
    }

    @Test
    void rareOpponentBetsDoNotUnderflowTheConditionalHandDistribution() {
        var game = SixMaxHeadsUpFlopGameTest.uncertainGame(1e-100);
        var decision =
                new SixMaxHeadsUpFlopDecisionEvaluator(game, selectivePolicy(game, 1e-250))
                        .evaluate(List.of("check", "bet"), "9h 9s");
        assertEquals(2, decision.compatibleJointDeals());
        assertTrue(decision.actionEvBb().values().stream().allMatch(Double::isFinite));
        var ordinary = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var control =
                new SixMaxHeadsUpFlopDecisionEvaluator(ordinary, selectivePolicy(ordinary, 1))
                        .evaluate(List.of("check", "bet"), "9h 9s");
        assertEquals(control.actionEvBb().get("call"), decision.actionEvBb().get("call"), 1e-10);
    }

    @Test
    void missingInvalidAndZeroReachPoliciesCannotSilentlyProduceFeedback() {
        var game = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var policy = selectivePolicy(game, 1);
        var evaluator = new SixMaxHeadsUpFlopDecisionEvaluator(game, policy);
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(List.of(), "2h 2s"));
        assertThrows(
                IllegalArgumentException.class, () -> evaluator.evaluate(List.of("call"), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of("check", "check"), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpFlopDecisionEvaluator(game, selectivePolicy(game, 0))
                                .evaluate(List.of("check", "bet"), "9h 9s"));
        Map<String, Map<String, Double>> broken = new LinkedHashMap<>(policy.strategy());
        String rootKey =
                "0:"
                        + game.informationSet(
                                game.chanceOutcomes(game.initialState()).getFirst().state());
        broken.remove(rootKey);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpFlopDecisionEvaluator(game, new CfrSolution(1, broken))
                                .evaluate(List.of(), "9h 9s"));
        broken.put(rootKey, Map.of("check", 0.1, "bet", 0.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.profileUtilitiesBb(new CfrSolution(1, broken)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpFlopDecisionEvaluator(game, new CfrSolution(1, broken))
                                .evaluate(List.of(), "9h 9s"));
        for (double invalid : new double[] {-0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            broken.put(rootKey, Map.of("check", invalid, "bet", 1.0));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxHeadsUpFlopDecisionEvaluator(game, new CfrSolution(1, broken))
                                    .evaluate(List.of(), "9h 9s"));
        }
    }
}
