package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPostflopTraversalCacheTest {
    private static SixMaxHeadsUpPostflopGame game(
            boolean cached, double flopBet, List<Double> quantiles) {
        var transition =
                SixMaxPolicyFlopTransition.counterfactualSupport(
                        SixMaxConnectedPreflopGameTest.base(),
                        SixMaxConnectedPreflopGameTest.HISTORY);
        var flop =
                transition.conditionOnFlop(
                        List.of(Card.parse("2d"), Card.parse("3d"), Card.parse("4d")));
        return new SixMaxHeadsUpPostflopGame(flop, flopBet, 6.5, 13, quantiles, cached);
    }

    @Test
    void exhaustivePublicChanceActionsAndChipSettlementsMatchTheUncachedGame() {
        for (double flopBet : List.of(3.25, 100.0)) {
            var cached = game(true, flopBet, List.of());
            var reference = game(false, flopBet, List.of());
            assertTrue(compare(cached, reference, cached.initialState()) > 1_000);
        }
        var cached = game(true, 3.25, List.of(0.0, .5));
        var reference = game(false, 3.25, List.of(0.0, .5));
        assertTrue(compare(cached, reference, cached.initialState()) > 1_000);
    }

    private static long compare(
            SixMaxHeadsUpPostflopGame cached,
            SixMaxHeadsUpPostflopGame reference,
            SixMaxHeadsUpPostflopGame.State state) {
        assertEquals(reference.isTerminal(state), cached.isTerminal(state));
        if (cached.isTerminal(state)) {
            assertEquals(reference.terminalUtility(state), cached.terminalUtility(state));
            return 1;
        }
        assertEquals(reference.currentPlayer(state), cached.currentPlayer(state));
        long states = 1;
        if (cached.currentPlayer(state) == -1) {
            assertEquals(reference.chanceOutcomes(state), cached.chanceOutcomes(state));
            for (var outcome : cached.chanceOutcomes(state))
                states += compare(cached, reference, outcome.state());
        } else {
            assertEquals(reference.informationSet(state), cached.informationSet(state));
            assertEquals(reference.legalActions(state), cached.legalActions(state));
            for (String action : cached.legalActions(state)) {
                var next = cached.afterAction(state, action);
                assertEquals(reference.afterAction(state, action), next);
                assertSame(next, cached.afterAction(state, action));
                states += compare(cached, reference, next);
            }
        }
        return states;
    }

    @Test
    void preservesTheEntireLearnedPolicyAndExactBestResponsesAndRejectsBadStatesRepeatedly() {
        var cached = game(true, 3.25, List.of(0.0, .5));
        var reference = game(false, 3.25, List.of(0.0, .5));
        var candidate = new CfrSolver<>(cached, CfrSolver.Variant.CFR_PLUS).solve(5);
        var original = new CfrSolver<>(reference, CfrSolver.Variant.CFR_PLUS).solve(5);
        assertEquals(
                SixMaxConnectedPostflopAudit.solutionHash(original),
                SixMaxConnectedPostflopAudit.solutionHash(candidate));
        assertEquals(
                HeadsUpBestResponse.assess(reference, original),
                HeadsUpBestResponse.assess(cached, candidate));
        var invalid =
                List.of(
                        new SixMaxHeadsUpPostflopGame.State(-2, "", null, "", null, ""),
                        new SixMaxHeadsUpPostflopGame.State(
                                0, "kk", Card.parse("Ks"), "", null, ""),
                        new SixMaxHeadsUpPostflopGame.State(
                                0, "bf", Card.parse("5d"), "", null, ""));
        for (var state : invalid)
            for (int repeat = 0; repeat < 2; repeat++)
                assertThrows(IllegalArgumentException.class, () -> cached.isTerminal(state));
    }
}
