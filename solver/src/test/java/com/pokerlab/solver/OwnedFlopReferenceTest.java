package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class OwnedFlopReferenceTest {
    static FiniteTwoPlayerSequenceForm.Node leaf(double value) {
        return new FiniteTwoPlayerSequenceForm.Node(
                -2, "", List.of(), List.of(), List.of(), List.of(value, 0., 0., 0., 0., 0.));
    }

    @Test
    void pruningRequiresLiteralIdenticalPayoffsAndNoDecisionByThatSeat() {
        var zero = leaf(0);
        var other = leaf(1e-16);
        var mixed =
                new FiniteTwoPlayerSequenceForm.Node(
                        -1, "", List.of(), List.of(.5, .5), List.of(zero, other), List.of());
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxOwnedFlopReferences.validate(mixed, 0));
        var acting =
                new FiniteTwoPlayerSequenceForm.Node(
                        0, "0:row", List.of("a"), List.of(), List.of(zero), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxOwnedFlopReferences.validate(acting, 0));
    }

    @Test
    void ownedPruningMatchesTheOriginalSolverBitwiseWithTwoThirdsFewerNodes() throws Exception {
        var game = RefinedFlopOwnedFixture.game();
        var ordinary = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        var expected = ordinary.solve(1000);
        var runner = new SixMaxOwnedFlopReferences(game);
        var actual = runner.trainBounded(1000);
        assertEquals(expected, actual.policy());
        assertEquals(7926000, ordinary.statistics().visitedNodes());
        assertEquals(2646000, actual.audit().statistics().visitedNodes());
        assertEquals(Map.of(0, 0., 1, 0., 2, 0., 4, -.5), actual.audit().inactiveUtilities());
        assertThrows(IllegalArgumentException.class, () -> runner.train(400000));
        assertThrows(IllegalArgumentException.class, () -> runner.trainBounded(0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        runner.inactivePlayerUtility(
                                new SixMaxSuppliedFlopBettingGame.State(
                                        -99,
                                        "foreign",
                                        SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                                        ""),
                                0));
    }
}
