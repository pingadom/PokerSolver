package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPostflopDecisionEvaluator.History;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPostflopDecisionEvaluatorTest {
    private static SixMaxHeadsUpPostflopGame game(double weight) {
        return new SixMaxHeadsUpPostflopGame(
                SixMaxHeadsUpFlopGameTest.uncertainGame(weight).flop(), 1, 2, 4, List.of(0.1));
    }

    private static CfrSolution selective(SixMaxHeadsUpPostflopGame game, double rareBet) {
        return SixMaxHeadsUpPostflopGameTest.policy(
                game,
                (s, a) -> {
                    String h =
                            s.turn() == null
                                    ? s.flopHistory()
                                    : s.river() == null ? s.turnHistory() : s.riverHistory();
                    if (h.isEmpty()) return a.equals("bet") ? 1.0 : 0.0;
                    if (h.equals("k")) {
                        double bet = game.ownHand(s).key().equals("Jh Js") ? rareBet : 0;
                        return a.equals("bet") ? bet : 1 - bet;
                    }
                    return a.equals("call") ? 1.0 : 0.0;
                });
    }

    @Test
    void connectedCalledFlopValuesAgreeWithTheIndependentTurnRiverHandoff() {
        var connected = game(1);
        var allCalls =
                SixMaxHeadsUpPostflopGameTest.policy(
                        connected, (s, a) -> a.equals("bet") || a.equals("call") ? 1.0 : 0.0);
        var flop = new SixMaxHeadsUpFlopGame(connected.flop(), 1);
        var flopCalls =
                SixMaxHeadsUpFlopGameTest.policy(
                        flop, (s, a) -> a.equals("bet") || a.equals("call") ? 1.0 : 0.0);
        var handoff = new SixMaxPolicyTurnTransition(flop, flopCalls, List.of("bet", "call"));
        var old = new SixMaxHeadsUpTurnRiverGame(handoff.conditionOnTurn(Card.parse("5c")), 2, 4);
        var oldCalls =
                SixMaxHeadsUpTurnRiverGameTest.policy(
                        old, (s, a) -> a.equals("bet") || a.equals("call") ? 1.0 : 0.0);
        for (Seat seat : Seat.values())
            assertEquals(
                    old.profileUtilitiesBb(oldCalls).get(seat),
                    connected.profileUtilitiesBb(allCalls).get(seat),
                    1e-12);
        var earlier =
                new SixMaxHeadsUpTurnRiverDecisionEvaluator(old, oldCalls)
                        .evaluate(List.of(), null, List.of(), "9h 9s");
        var current =
                new SixMaxPostflopDecisionEvaluator(connected, allCalls)
                        .evaluate(
                                new History(
                                        List.of("bet", "call"),
                                        Card.parse("5c"),
                                        List.of(),
                                        null,
                                        List.of()),
                                "9h 9s");
        for (String action : current.actionEvBb().keySet())
            assertEquals(earlier.actionEvBb().get(action), current.actionEvBb().get(action), 1e-12);
    }

    @Test
    void riverOpponentMovesConditionBeliefAndBothPlayersGetActualChipEvs() {
        var game = game(1);
        var evaluator = new SixMaxPostflopDecisionEvaluator(game, selective(game, 1));
        var first =
                evaluator.evaluate(
                        new History(
                                List.of("bet", "call"),
                                Card.parse("5c"),
                                List.of("bet", "call"),
                                Card.parse("7c"),
                                List.of("check", "bet")),
                        "9h 9s");
        assertEquals(Seat.BB, first.actingSeat());
        assertEquals("RIVER", first.street());
        assertEquals(2, first.compatibleJointDeals());
        assertEquals(-6, first.actionEvBb().get("fold"), 1e-12);
        assertEquals(-10, first.actionEvBb().get("call"), 1e-12);
        assertEquals(4, first.evLossBb("call"), 1e-12);
        assertEquals(Map.of("call", 1.0, "fold", 0.0), first.actionFrequency());
        var second =
                evaluator.evaluate(
                        new History(
                                List.of("bet", "call"),
                                Card.parse("5c"),
                                List.of("bet", "call"),
                                Card.parse("7c"),
                                List.of("bet")),
                        "Jh Js");
        assertEquals(Seat.BTN, second.actingSeat());
        assertEquals(2, second.compatibleJointDeals());
        assertEquals(-6, second.actionEvBb().get("fold"), 1e-12);
        assertEquals(10.5, second.actionEvBb().get("call"), 1e-12);
        assertThrows(UnsupportedOperationException.class, () -> first.actionFrequency().clear());
        assertThrows(IllegalArgumentException.class, () -> first.evLossBb("raise"));
    }

    @Test
    void flopAndTurnEvsIntegrateDeclaredChanceAndRiversFilterFoldedCardDeals() {
        var game = game(1);
        var checks =
                SixMaxHeadsUpPostflopGameTest.policy(
                        game, (s, a) -> a.equals("check") || a.equals("call") ? 1.0 : 0.0);
        var evaluator = new SixMaxPostflopDecisionEvaluator(game, checks);
        var flop = evaluator.evaluate(History.flop(List.of()), "9h 9s");
        var turn =
                evaluator.evaluate(
                        new History(
                                List.of("check", "check"),
                                Card.parse("5c"),
                                List.of(),
                                null,
                                List.of()),
                        "9h 9s");
        assertEquals("FLOP", flop.street());
        assertEquals("TURN", turn.street());
        assertEquals(
                game.checkdownUtilitiesBb().get(Seat.BB), flop.actionEvBb().get("check"), 1e-12);
        assertEquals(flop.actionEvBb().get("check"), turn.actionEvBb().get("check"), 1e-12);
        var river =
                evaluator.evaluate(
                        new History(
                                List.of("check", "check"),
                                Card.parse("5c"),
                                List.of("check", "check"),
                                Card.parse("Ac"),
                                List.of()),
                        "9h 9s");
        assertEquals(2, river.compatibleJointDeals());
        var tied =
                evaluator.evaluate(
                        new History(
                                List.of("check", "check"),
                                Card.parse("5c"),
                                List.of("check", "check"),
                                Card.parse("6c"),
                                List.of("check")),
                        "Jh Js");
        assertEquals(0.25, tied.actionEvBb().get("bet"), 1e-12);
    }

    @Test
    void ownChecksAreForcedAndRepeatedRareOpponentBetsStayConditionable() {
        var history =
                new History(
                        List.of("check", "bet", "call"),
                        Card.parse("5c"),
                        List.of("check", "bet", "call"),
                        Card.parse("7c"),
                        List.of("check", "bet"));
        var ordinary = game(1);
        var rare = game(1e-100);
        var control =
                new SixMaxPostflopDecisionEvaluator(ordinary, selective(ordinary, 1))
                        .evaluate(history, "9h 9s");
        var measured =
                new SixMaxPostflopDecisionEvaluator(rare, selective(rare, 1e-250))
                        .evaluate(history, "9h 9s");
        assertEquals(2, measured.compatibleJointDeals());
        for (String action : control.actionEvBb().keySet())
            assertEquals(
                    control.actionEvBb().get(action), measured.actionEvBb().get(action), 1e-12);
    }

    @Test
    void missingPoliciesUnsupportedChanceAndIllegalOrZeroReachHistoriesAreRejected() {
        var game = game(1);
        var policy = selective(game, 1);
        var evaluator = new SixMaxPostflopDecisionEvaluator(game, policy);
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(History.flop(List.of("call")), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(History.flop(List.of("bet", "fold")), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(History.flop(List.of("check", "check")), "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        evaluator.evaluate(
                                new History(
                                        List.of("check", "check"),
                                        Card.parse("Ac"),
                                        List.of(),
                                        null,
                                        List.of()),
                                "9h 9s"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        evaluator.evaluate(
                                new History(
                                        List.of("check", "check"),
                                        Card.parse("5c"),
                                        List.of("check", "bet"),
                                        null,
                                        List.of()),
                                "8h 8s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(History.flop(List.of()), "2h 2s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new History(List.of(), null, List.of("check"), null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPostflopDecisionEvaluator(game, new CfrSolution(1, Map.of()))
                                .evaluate(History.flop(List.of()), "9h 9s"));
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        for (double bad : new double[] {-0.1, Double.NaN, Double.POSITIVE_INFINITY, 0.3}) {
            var broken = new LinkedHashMap<>(policy.strategy());
            broken.put("0:" + game.informationSet(root), Map.of("check", bad, "bet", 0.5));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxPostflopDecisionEvaluator(game, new CfrSolution(1, broken))
                                    .evaluate(History.flop(List.of()), "9h 9s"));
        }
    }
}
