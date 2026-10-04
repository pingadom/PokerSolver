package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxHeadsUpTurnRiverDecisionEvaluatorTest {
    private static CfrSolution selective(SixMaxHeadsUpTurnRiverGame game, double betProbability) {
        return SixMaxHeadsUpTurnRiverGameTest.policy(
                game,
                (s, a) -> {
                    String history = s.river() == null ? s.turnHistory() : s.riverHistory();
                    if (history.equals("")) return a.equals("bet") ? 1.0 : 0.0;
                    if (history.equals("k")) {
                        double bet = game.ownHand(s).key().equals("Jh Js") ? betProbability : 0;
                        return a.equals("bet") ? bet : 1 - bet;
                    }
                    return a.equals("call") ? 1.0 : 0.0;
                });
    }

    @Test
    void opponentRiverBetConditionsHandsAndOwnZeroFrequencyCheckRemainsInspectable() {
        var game = SixMaxHeadsUpTurnRiverGameTest.uncertainGame(1);
        var evaluator = new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, selective(game, 1));
        var decision =
                evaluator.evaluate(
                        List.of("bet", "call"), Card.parse("7c"), List.of("check", "bet"), "9h 9s");
        assertEquals(Seat.BB, decision.actingSeat());
        assertEquals(2, decision.compatibleJointDeals());
        assertEquals(-6.25, decision.actionEvBb().get("fold"), 1e-12);
        assertEquals(-12.75, decision.actionEvBb().get("call"), 1e-12);
        assertEquals(6.5, decision.evLossBb("call"), 1e-12);
        assertEquals(0, decision.evLossBb("fold"), 1e-12);
        assertEquals(Map.of("call", 1.0, "fold", 0.0), decision.actionFrequency());
        assertThrows(UnsupportedOperationException.class, () -> decision.actionEvBb().clear());
        assertThrows(IllegalArgumentException.class, () -> decision.evLossBb("raise"));
    }

    @Test
    void secondPlayerEvsRestoreActualContributionsAndPublicRiverRemovesFoldedDeals() {
        var game = SixMaxHeadsUpTurnRiverGameTest.uncertainGame(1);
        var evaluator = new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, selective(game, 1));
        var decision =
                evaluator.evaluate(
                        List.of("bet", "call"), Card.parse("7c"), List.of("bet"), "Jh Js");
        assertEquals(Seat.BTN, decision.actingSeat());
        assertEquals(-6.25, decision.actionEvBb().get("fold"), 1e-12);
        assertEquals(13.25, decision.actionEvBb().get("call"), 1e-12);
        var blocked =
                evaluator.evaluate(List.of("check", "check"), Card.parse("Ac"), List.of(), "9h 9s");
        // Opponent checks on the turn only with 88. Ac additionally removes the folded AcAd deal.
        assertEquals(1, blocked.compatibleJointDeals());
    }

    @Test
    void turnActionEvsIntegrateAllPhysicalRiversAndPreserveRareOpponentReach() {
        var game = SixMaxHeadsUpTurnRiverGameTest.uncertainGame(1);
        var check =
                SixMaxHeadsUpTurnRiverGameTest.policy(
                        game, (s, a) -> a.equals("check") || a.equals("call") ? 1.0 : 0.0);
        var root =
                new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, check)
                        .evaluate(List.of(), null, List.of(), "9h 9s");
        assertEquals(
                game.exactCheckdownUtilitiesBb().get(Seat.BB),
                root.actionEvBb().get("check"),
                1e-12);
        var rare = SixMaxHeadsUpTurnRiverGameTest.uncertainGame(1e-100);
        var decision =
                new SixMaxHeadsUpTurnRiverDecisionEvaluator(rare, selective(rare, 1e-250))
                        .evaluate(
                                List.of("bet", "call"),
                                Card.parse("7c"),
                                List.of("check", "bet"),
                                "9h 9s");
        var ordinary =
                new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, selective(game, 1))
                        .evaluate(
                                List.of("bet", "call"),
                                Card.parse("7c"),
                                List.of("check", "bet"),
                                "9h 9s");
        assertEquals(2, decision.compatibleJointDeals());
        for (String action : ordinary.actionEvBb().keySet())
            assertEquals(
                    ordinary.actionEvBb().get(action), decision.actionEvBb().get(action), 1e-12);
    }

    @Test
    void invalidPoliciesAndImpossibleOrZeroReachHistoriesAreRejected() {
        var game = SixMaxHeadsUpTurnRiverGameTest.uncertainGame(1);
        var solution = selective(game, 1);
        var evaluator = new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, solution);
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of("call"), null, List.of(), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of("bet", "fold"), null, List.of(), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of("check", "check"), null, List.of(), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        evaluator.evaluate(
                                List.of("check", "check"), Card.parse("Th"), List.of(), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        evaluator.evaluate(
                                List.of("check", "check"),
                                Card.parse("7c"),
                                List.of("check", "bet"),
                                "8h 8s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of(), null, List.of(), "2h 2s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHeadsUpTurnRiverDecisionEvaluator(
                                        game, new CfrSolution(1, Map.of()))
                                .evaluate(List.of(), null, List.of(), "9h 9s"));
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        String key = "0:" + game.informationSet(root);
        for (double bad : new double[] {-0.1, Double.NaN, Double.POSITIVE_INFINITY, 0.3}) {
            var broken = new LinkedHashMap<>(solution.strategy());
            broken.put(key, Map.of("check", bad, "bet", 0.5));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxHeadsUpTurnRiverDecisionEvaluator(
                                            game, new CfrSolution(1, broken))
                                    .evaluate(List.of(), null, List.of(), "9h 9s"));
        }
    }
}
