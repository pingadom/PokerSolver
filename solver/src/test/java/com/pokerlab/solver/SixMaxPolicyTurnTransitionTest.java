package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPolicyTurnTransitionTest {
    private static CfrSolution selective(SixMaxHeadsUpFlopGame game, double betProbability) {
        return SixMaxHeadsUpFlopGameTest.policy(
                game,
                (s, a) -> {
                    if (s.history().equals("")) return a.equals("check") ? 1.0 : 0.0;
                    if (s.history().equals("k")) {
                        double bet = game.ownHand(s).key().equals("Jh Js") ? betProbability : 0;
                        return a.equals("bet") ? bet : 1 - bet;
                    }
                    return a.equals("call") ? 1.0 : 0.0;
                });
    }

    @Test
    void publicTurnConditionsFoldedCardsAndKeepsThePhysicalDistribution() {
        var flop = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var handoff = SixMaxHeadsUpTurnRiverGameTest.transition(flop);
        assertEquals(1, handoff.historyProbability(), 1e-12);
        assertEquals(4, handoff.deals().size());
        assertEquals(0.1 / 37, handoff.turnProbability(Card.parse("Ac")), 1e-12);
        assertEquals(0, handoff.turnProbability(Card.parse("Th")));
        var turn = handoff.conditionOnTurn(Card.parse("Ac"));
        assertEquals(2, turn.deals().size());
        assertEquals(0.1 / 37, turn.probability(), 1e-12);
        for (var deal : turn.deals()) assertEquals("Ah As", deal.hands().getFirst().key());
        assertEquals(
                1,
                turn.deals().stream()
                        .mapToDouble(SixMaxPolicyFlopTransition.JointDeal::probability)
                        .sum(),
                1e-12);
        assertEquals(36, turn.undealtCards(0).size());
        assertThrows(UnsupportedOperationException.class, () -> turn.board().clear());
        assertEquals(handoff.sampleTurn(711).board(), handoff.sampleTurn(711).board());
        double probability = 0, utility = 0;
        for (Card card : new Deck().cards()) {
            if (flop.flop().board().contains(card)) continue;
            double p = handoff.turnProbability(card);
            probability += p;
            if (p > 0)
                utility +=
                        p
                                * new SixMaxHeadsUpTurnRiverGame(
                                                handoff.conditionOnTurn(card), 1, 1)
                                        .exactCheckdownUtilitiesBb()
                                        .get(Seat.BB);
        }
        assertEquals(1, probability, 1e-12);
        assertEquals(flop.flop().exactCheckdown().utilitiesBb().get(Seat.BB), utility, 1e-12);
    }

    @Test
    void bothFlopActionsUpdateBeliefAndCalledChipsCarryIntoTurn() {
        var flop = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var handoff =
                new SixMaxPolicyTurnTransition(
                        flop, selective(flop, 0.25), List.of("check", "bet", "call"));
        assertEquals(0.125, handoff.historyProbability(), 1e-12);
        assertEquals(2, handoff.deals().size());
        for (var deal : handoff.deals())
            assertEquals("Jh Js", deal.hands().get(Seat.BTN.ordinal()).key());
        assertEquals(13, handoff.potBb());
        assertEquals(93.75, handoff.remainingStackBb());
        assertEquals(6.25, handoff.committedBb(Seat.BB));
        assertEquals(6.25, handoff.committedBb(Seat.BTN));
        assertEquals(0.5, handoff.committedBb(Seat.SB));
    }

    @Test
    void rareFlopHistoryIsConditionedInLogSpaceEvenWhenReachUnderflows() {
        var rare = SixMaxHeadsUpFlopGameTest.uncertainGame(1e-100);
        var transition =
                new SixMaxPolicyTurnTransition(
                        rare, selective(rare, 1e-250), List.of("check", "bet", "call"));
        var ordinary = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var control =
                new SixMaxPolicyTurnTransition(
                        ordinary, selective(ordinary, 1), List.of("check", "bet", "call"));
        assertEquals(0, transition.historyProbability());
        assertTrue(Double.isFinite(transition.logHistoryProbability()));
        assertEquals(2, transition.deals().size());
        for (int i = 0; i < 2; i++)
            assertEquals(
                    control.deals().get(i).probability(),
                    transition.deals().get(i).probability(),
                    1e-12);
        assertEquals(
                control.turnProbability(Card.parse("Ac")),
                transition.turnProbability(Card.parse("Ac")),
                1e-12);
    }

    @Test
    void incompleteFoldedAllInZeroReachAndInvalidPoliciesAreRejected() {
        var flop = SixMaxHeadsUpFlopGameTest.uncertainGame(1);
        var solution = selective(flop, 1);
        for (var history :
                List.of(
                        List.<String>of(),
                        List.of("check"),
                        List.of("bet", "fold"),
                        List.of("bet", "call"),
                        List.of("call"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxPolicyTurnTransition(flop, solution, history));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPolicyTurnTransition(
                                flop, new CfrSolution(1, Map.of()), List.of("check", "check")));
        var allIn = new SixMaxHeadsUpFlopGame(flop.flop(), 97);
        var allInPolicy =
                SixMaxHeadsUpFlopGameTest.policy(
                        allIn, (s, a) -> a.equals("bet") || a.equals("call") ? 1.0 : 0.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxPolicyTurnTransition(allIn, allInPolicy, List.of("bet", "call")));
        var valid = SixMaxHeadsUpTurnRiverGameTest.transition(flop);
        assertThrows(IllegalArgumentException.class, () -> valid.conditionOnTurn(Card.parse("Th")));
        assertThrows(IllegalArgumentException.class, () -> valid.conditionOnTurn(Card.parse("2d")));
        assertThrows(IllegalArgumentException.class, () -> valid.conditionOnTurn(null));
    }
}
