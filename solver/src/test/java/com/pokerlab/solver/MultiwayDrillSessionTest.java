package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MultiwayDrillSessionTest {
    @Test
    void deterministicSessionsSupportMixedSeatsAndRecomputeEveryScore() {
        var game =
                SixSeatValidationSpot.create()
                        .game(
                                (dealt, mask) ->
                                        MultiwayShowdownEstimate.certain(
                                                new double[] {1, 0, 0, 0, 0, 0}));
        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(20);
        var session = new MultiwayDrillSession(game, solution);
        var players = new HashSet<Integer>();
        for (int index = 0; index < 10; index++) {
            var mixed = session.question(Long.MAX_VALUE, index, 0);
            assertEquals(mixed, session.question(Long.MAX_VALUE, index, 0));
            players.add(mixed.actingPlayer());
            assertEquals(5, session.question(Long.MAX_VALUE, index, 5).actingPlayer());
        }
        assertTrue(players.size() > 1);
        var actions = Collections.nCopies(10, MultiwayCallTrainer.Action.CALL);
        var review = session.review(Long.MAX_VALUE, 0, actions);
        assertEquals(10, review.attempts().size());
        double total = 0;
        for (int index = 0; index < 10; index++) {
            var expected = session.grade(Long.MAX_VALUE, index, 0, actions.get(index));
            assertEquals(expected, review.attempts().get(index));
            total += expected.feedback().evLossBb();
        }
        assertEquals(total, review.totalEvLossBb());
        assertEquals(total / 10, review.averageEvLossBb());
        assertTrue(total > 0);
        assertEquals(
                0,
                session.review(42, 0, Collections.nCopies(10, MultiwayCallTrainer.Action.FOLD))
                        .totalEvLossBb());
        assertThrows(IllegalArgumentException.class, () -> session.question(42, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> session.question(42, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> session.question(42, 0, 6));
        assertThrows(
                IllegalArgumentException.class, () -> session.review(42, 0, actions.subList(0, 9)));
    }
}
