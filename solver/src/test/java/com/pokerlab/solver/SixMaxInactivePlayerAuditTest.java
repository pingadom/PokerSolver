package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxConnectedPreflopGame.State;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SixMaxInactivePlayerAuditTest {
    @Test
    void shortcutPreservesEveryBestResponseAndAvoidsFoldedSeatTerminalQueries() {
        var game =
                new SixMaxConnectedPreflopGame(
                        SixMaxConnectedPreflopGameTest.base(),
                        List.of(
                                SixMaxConnectedPreflopGameTest.selection("As 2d 3d", "2d 3d 4d"),
                                new SixMaxConnectedPreflopGame.Selection(
                                        lateFoldHistory(),
                                        SixMaxConnectedPreflopGameTest.selection("2d 3d 4d")
                                                .flops(),
                                        3.75,
                                        7.5,
                                        15)));
        // Include complete support and mixed preflop choices, so earlier deviations still see
        // both hidden deals and both the selected and residual physical-board continuations.
        var policy =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        var unpruned = new CountingGame(game, false);
        var reference = MultiPlayerInformationSetBestResponse.assess(unpruned, policy);
        var pruned = new CountingGame(game, true);
        var actual = MultiPlayerInformationSetBestResponse.assess(pruned, policy);
        assertArrayEquals(
                reference.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).toArray(),
                actual.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).toArray(),
                1e-12);
        assertArrayEquals(
                reference.bestResponseUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray(),
                actual.bestResponseUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray(),
                1e-12);
        assertArrayEquals(
                reference.deviationGainsBb().stream().mapToDouble(Double::doubleValue).toArray(),
                actual.deviationGainsBb().stream().mapToDouble(Double::doubleValue).toArray(),
                1e-12);
        assertEquals(reference.nashConvBb(), actual.nashConvBb(), 1e-12);
        assertEquals(reference.responseActions(), actual.responseActions());
        assertTrue(pruned.shortcutHits > 0);
        assertTrue(
                pruned.terminalQueries < unpruned.terminalQueries * .75,
                () ->
                        "Expected fewer terminal queries: "
                                + pruned.terminalQueries
                                + " vs "
                                + unpruned.terminalQueries);
    }

    @Test
    void fixedPayoffsApplyOnlyAfterTheSelectedPreflopHistory() {
        var game = SixMaxConnectedPreflopGameTest.game();
        for (int target = 0; target < 6; target++) {
            assertTrue(game.inactivePlayerUtility(game.initialState(), target).isEmpty());
            for (var root : game.chanceOutcomes(game.initialState()))
                assertTrue(game.inactivePlayerUtility(root.state(), target).isEmpty());
            var state = game.replayPreflop(SixMaxConnectedPreflopGameTest.HISTORY, 0);
            var utility = game.inactivePlayerUtility(state, target);
            if (target == 3 || target == 5) assertTrue(utility.isEmpty());
            else {
                assertEquals(target == 4 ? -.5 : 0, utility.orElseThrow(), 0);
                for (var outcome : game.chanceOutcomes(state))
                    assertEquals(utility, game.inactivePlayerUtility(outcome.state(), target));
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> game.inactivePlayerUtility(game.initialState(), -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.inactivePlayerUtility(game.initialState(), 6));
        assertThrows(IllegalArgumentException.class, () -> game.inactivePlayerUtility(null, 0));
        var lateFold =
                new SixMaxConnectedPreflopGame(
                        game.source(),
                        List.of(
                                new SixMaxConnectedPreflopGame.Selection(
                                        lateFoldHistory(),
                                        game.selections().getFirst().flops(),
                                        3.75,
                                        7.5,
                                        15)));
        assertEquals(
                -1,
                lateFold.inactivePlayerUtility(lateFold.replayPreflop(lateFoldHistory(), 0), 0)
                        .orElseThrow());
    }

    @Test
    void aSeatCanBeActiveInOneHistoryAndInactiveInAnother() {
        var other =
                List.of(
                        new PublicAction(Seat.UTG, "call"), new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"), new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"), new PublicAction(Seat.BB, "check"));
        var selection = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d");
        var game =
                new SixMaxConnectedPreflopGame(
                        SixMaxConnectedPreflopGameTest.base(),
                        List.of(
                                selection,
                                new SixMaxConnectedPreflopGame.Selection(
                                        other, selection.flops(), 1.25, 2.5, 5)));
        for (var root : game.chanceOutcomes(game.initialState())) {
            int deal = root.state().preflop().dealIndex();
            var inactive = game.replayPreflop(selection.history(), deal);
            var active = game.replayPreflop(other, deal);
            assertTrue(game.inactivePlayerUtility(inactive, Seat.UTG.ordinal()).isPresent());
            assertTrue(game.inactivePlayerUtility(active, Seat.UTG.ordinal()).isEmpty());
            assertTrue(game.inactivePlayerUtility(inactive, Seat.BTN.ordinal()).isEmpty());
            assertTrue(game.inactivePlayerUtility(active, Seat.BTN.ordinal()).isPresent());
            var first = game.chanceOutcomes(active).getFirst().state();
            assertEquals(Seat.BB.ordinal(), game.currentPlayer(first));
            assertTrue(game.inactivePlayerUtility(first, Seat.UTG.ordinal()).isEmpty());
            var acting = game.afterAction(first, "check");
            assertEquals(Seat.UTG.ordinal(), game.currentPlayer(acting));
            assertTrue(game.inactivePlayerUtility(acting, Seat.UTG.ordinal()).isEmpty());
        }
    }

    @Test
    void rejectsNonFiniteShortcutValues() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var policy =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        for (double invalid :
                List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            var bad =
                    new CountingGame(game, true) {
                        @Override
                        public OptionalDouble inactivePlayerUtility(State state, int player) {
                            return OptionalDouble.of(invalid);
                        }
                    };
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MultiPlayerInformationSetBestResponse.assess(bad, policy));
        }
    }

    private static class CountingGame implements MultiPlayerCfrGame<State> {
        private final SixMaxConnectedPreflopGame delegate;
        private final boolean shortcuts;
        private long terminalQueries;
        private long shortcutHits;

        private CountingGame(SixMaxConnectedPreflopGame delegate, boolean shortcuts) {
            this.delegate = delegate;
            this.shortcuts = shortcuts;
        }

        @Override
        public int playerCount() {
            return delegate.playerCount();
        }

        @Override
        public State initialState() {
            return delegate.initialState();
        }

        @Override
        public boolean isTerminal(State state) {
            return delegate.isTerminal(state);
        }

        @Override
        public double[] terminalUtilities(State state) {
            terminalQueries++;
            return delegate.terminalUtilities(state);
        }

        @Override
        public int currentPlayer(State state) {
            return delegate.currentPlayer(state);
        }

        @Override
        public List<String> legalActions(State state) {
            return delegate.legalActions(state);
        }

        @Override
        public String informationSet(State state) {
            return delegate.informationSet(state);
        }

        @Override
        public State afterAction(State state, String action) {
            return delegate.afterAction(state, action);
        }

        @Override
        public List<ChanceOutcome<State>> chanceOutcomes(State state) {
            return delegate.chanceOutcomes(state);
        }

        @Override
        public OptionalDouble inactivePlayerUtility(State state, int player) {
            var value =
                    shortcuts
                            ? delegate.inactivePlayerUtility(state, player)
                            : OptionalDouble.empty();
            if (value.isPresent()) shortcutHits++;
            return value;
        }
    }

    private static List<PublicAction> lateFoldHistory() {
        return List.of(
                new PublicAction(Seat.UTG, "call"),
                new PublicAction(Seat.HJ, "fold"),
                new PublicAction(Seat.CO, "fold"),
                new PublicAction(Seat.BTN, "raise:3.0"),
                new PublicAction(Seat.SB, "fold"),
                new PublicAction(Seat.BB, "call"),
                new PublicAction(Seat.UTG, "fold"));
    }
}
