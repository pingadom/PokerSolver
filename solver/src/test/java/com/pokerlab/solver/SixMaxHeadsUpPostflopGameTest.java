package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SixMaxHeadsUpPostflopGameTest {
    static CfrSolution policy(
            SixMaxHeadsUpPostflopGame game,
            BiFunction<SixMaxHeadsUpPostflopGame.State, String, Double> probability) {
        Map<String, Map<String, Double>> weights = new LinkedHashMap<>();
        collect(game, game.initialState(), probability, weights);
        return new CfrSolution(1, weights);
    }

    private static void collect(
            SixMaxHeadsUpPostflopGame game,
            SixMaxHeadsUpPostflopGame.State s,
            BiFunction<SixMaxHeadsUpPostflopGame.State, String, Double> probability,
            Map<String, Map<String, Double>> policies) {
        if (game.isTerminal(s)) return;
        if (game.currentPlayer(s) == -1) {
            for (var outcome : game.chanceOutcomes(s))
                collect(game, outcome.state(), probability, policies);
            return;
        }
        var weights = new LinkedHashMap<String, Double>();
        for (String action : game.legalActions(s))
            weights.put(action, probability.apply(s, action));
        policies.put(game.currentPlayer(s) + ":" + game.informationSet(s), weights);
        for (String action : game.legalActions(s))
            collect(game, game.afterAction(s, action), probability, policies);
    }

    static SixMaxHeadsUpPostflopGame.State actions(
            SixMaxHeadsUpPostflopGame game,
            SixMaxHeadsUpPostflopGame.State s,
            List<String> actions) {
        for (String action : actions) s = game.afterAction(s, action);
        return s;
    }

    static SixMaxHeadsUpPostflopGame.State reveal(
            SixMaxHeadsUpPostflopGame game, SixMaxHeadsUpPostflopGame.State s, String card) {
        boolean turn = s.turn() == null;
        return game.chanceOutcomes(s).stream()
                .map(ChanceOutcome::state)
                .filter(next -> (turn ? next.turn() : next.river()).equals(Card.parse(card)))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void exactPhysicalChanceRetainsAllSixHandsAndMatchesIndependentCheckdown() {
        var flop = SixMaxHeadsUpFlopGameTest.uncertainGame(1).flop();
        var game = new SixMaxHeadsUpPostflopGame(flop, 1, 2, 4);
        assertEquals(
                SixMaxHeadsUpPostflopGame.ChanceModel.EXACT_PHYSICAL_TURN_RIVER,
                game.chanceModel());
        var roots = game.chanceOutcomes(game.initialState());
        assertEquals(4, roots.size());
        assertEquals(1, roots.stream().map(o -> game.informationSet(o.state())).distinct().count());
        for (var root : roots) {
            var turns = game.chanceOutcomes(actions(game, root.state(), List.of("check", "check")));
            assertEquals(37, turns.size());
            assertEquals(1, turns.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
            for (var turn : turns) {
                assertEquals(1.0 / 37, turn.probability());
                var rivers =
                        game.chanceOutcomes(actions(game, turn.state(), List.of("check", "check")));
                assertEquals(36, rivers.size());
                for (var river : rivers) {
                    assertEquals(1.0 / 36, river.probability());
                    assertNotEquals(turn.state().turn(), river.state().river());
                    for (var hand : flop.deals().get(root.state().dealIndex()).hands()) {
                        assertNotEquals(hand.first(), turn.state().turn());
                        assertNotEquals(hand.second(), turn.state().turn());
                        assertNotEquals(hand.first(), river.state().river());
                        assertNotEquals(hand.second(), river.state().river());
                    }
                }
            }
        }
        var independent = flop.exactCheckdown().utilitiesBb();
        for (Seat seat : Seat.values())
            assertEquals(independent.get(seat), game.checkdownUtilitiesBb().get(seat), 1e-10);
        var checked = policy(game, (s, a) -> a.equals("check") || a.equals("call") ? 1.0 : 0.0);
        for (Seat seat : Seat.values())
            assertEquals(independent.get(seat), game.profileUtilitiesBb(checked).get(seat), 1e-10);
    }

    @Test
    void allStreetTerminalPathsRefundUncalledBetsAndConserveSixSeatChips() {
        var game =
                new SixMaxHeadsUpPostflopGame(
                        SixMaxHeadsUpTurnRiverGameTest.royalFlop().flop(), 3, 4, 10);
        var rounds =
                List.of(
                        List.of("check", "check"),
                        List.of("bet", "call"),
                        List.of("check", "bet", "call"),
                        List.of("bet", "fold"),
                        List.of("check", "bet", "fold"));
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        for (var f : rounds) {
            var flop = actions(game, root, f);
            if (game.isTerminal(flop)) {
                verify(game, flop, f.equals(List.of("bet", "fold")) ? 3.5 : -3);
                continue;
            }
            double fc = f.contains("call") ? 3 : 0;
            for (var t : rounds) {
                var turn = actions(game, reveal(game, flop, "6h"), t);
                if (game.isTerminal(turn)) {
                    verify(game, turn, t.equals(List.of("bet", "fold")) ? 3.5 + fc : -3 - fc);
                    continue;
                }
                double tc = t.contains("call") ? 4 : 0;
                for (var r : rounds) {
                    var river = actions(game, reveal(game, turn, "7h"), r);
                    double expected =
                            r.equals(List.of("check", "bet", "fold"))
                                    ? -3 - fc - tc
                                    : 3.5 + fc + tc + (r.contains("call") ? 10 : 0);
                    verify(game, river, expected);
                }
            }
        }
    }

    private static void verify(
            SixMaxHeadsUpPostflopGame game,
            SixMaxHeadsUpPostflopGame.State terminal,
            double expected) {
        var utilities = game.terminalUtilitiesBb(terminal);
        assertEquals(expected, utilities.get(Seat.BB), 1e-12);
        assertEquals(-0.5, utilities.get(Seat.SB));
        assertEquals(0, utilities.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(utilities.get(Seat.BB) - 0.25, game.terminalUtility(terminal), 1e-12);
        assertEquals(utilities.get(Seat.BTN) - 0.25, -game.terminalUtility(terminal), 1e-12);
    }

    @Test
    void stackCapsAndFlopTurnRiverAllInsHaveNoExtraBetting() {
        var flop = SixMaxHeadsUpTurnRiverGameTest.royalFlop().flop();
        var flopAllIn = new SixMaxHeadsUpPostflopGame(flop, 1000, 1000, 1000);
        var root = flopAllIn.chanceOutcomes(flopAllIn.initialState()).getFirst().state();
        var flopChance = actions(flopAllIn, root, List.of("bet", "call"));
        assertEquals(97, flopAllIn.flopBetBb());
        var turnChance = reveal(flopAllIn, flopChance, "6h");
        assertEquals(-1, flopAllIn.currentPlayer(turnChance));
        var terminal = reveal(flopAllIn, turnChance, "7h");
        verify(flopAllIn, terminal, 100.5);
        assertThrows(
                IllegalArgumentException.class, () -> flopAllIn.afterAction(turnChance, "check"));
        var flopTerminal = terminal;
        assertThrows(IllegalArgumentException.class, () -> flopAllIn.legalActions(flopTerminal));
        var turnAllIn = new SixMaxHeadsUpPostflopGame(flop, 95, 1000, 1000);
        root = turnAllIn.chanceOutcomes(turnAllIn.initialState()).getFirst().state();
        var turn = reveal(turnAllIn, actions(turnAllIn, root, List.of("bet", "call")), "6h");
        assertEquals(2, turnAllIn.betBb(turn));
        terminal = reveal(turnAllIn, actions(turnAllIn, turn, List.of("bet", "call")), "7h");
        verify(turnAllIn, terminal, 100.5);
        var riverAllIn = new SixMaxHeadsUpPostflopGame(flop, 95, 1, 1000);
        root = riverAllIn.chanceOutcomes(riverAllIn.initialState()).getFirst().state();
        turn = reveal(riverAllIn, actions(riverAllIn, root, List.of("bet", "call")), "6h");
        var river = reveal(riverAllIn, actions(riverAllIn, turn, List.of("bet", "call")), "7h");
        assertEquals(1, riverAllIn.betBb(river));
        verify(riverAllIn, actions(riverAllIn, river, List.of("bet", "call")), 100.5);
    }

    @Test
    void publicHistoryPreservesRecallAndChanceMenusBindPolicyKeys() {
        var flop = SixMaxHeadsUpFlopGameTest.uncertainGame(1).flop();
        var exact = new SixMaxHeadsUpPostflopGame(flop, 1, 2, 4);
        var menu = new SixMaxHeadsUpPostflopGame(flop, 1, 2, 4, List.of(0.1, 0.1, 0.9));
        var root = exact.chanceOutcomes(exact.initialState()).getFirst().state();
        assertNotEquals(exact.informationSet(root), menu.informationSet(root));
        String key = exact.informationSet(root);
        assertTrue(key.contains("9h 9s"));
        assertFalse(key.contains("Jh Js"));
        assertFalse(key.contains("Ah As"));
        var checked = reveal(exact, actions(exact, root, List.of("check", "check")), "5c");
        var called = reveal(exact, actions(exact, root, List.of("bet", "call")), "5c");
        assertNotEquals(exact.informationSet(checked), exact.informationSet(called));
        var river1 = reveal(exact, actions(exact, checked, List.of("check", "check")), "6c");
        var river2 = reveal(exact, actions(exact, checked, List.of("bet", "call")), "6c");
        assertNotEquals(exact.informationSet(river1), exact.informationSet(river2));
        var draws = menu.chanceOutcomes(actions(menu, root, List.of("check", "check")));
        assertEquals(2, draws.size());
        assertEquals(2.0 / 3, draws.getFirst().probability());
        assertEquals(1.0 / 3, draws.getLast().probability());
        for (var turn : draws)
            assertEquals(
                    36,
                    menu.chanceOutcomes(actions(menu, turn.state(), List.of("check", "check")))
                            .size());
    }

    @Test
    void cfrReSolvesAllThreeStreetsAndHasExactWithinModelBounds() {
        var game =
                new SixMaxHeadsUpPostflopGame(
                        SixMaxHeadsUpFlopGameTest.uncertainGame(1).flop(),
                        1,
                        2,
                        4,
                        List.of(0.1, 0.9));
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(300);
        var quality = HeadsUpBestResponse.assess(game, solution);
        assertTrue(quality.gap() < 0.05, "gap=" + quality.gap());
        assertTrue(solution.strategy().size() > 2000);
        assertEquals(
                quality.profileValue(),
                game.profileUtilitiesBb(solution).get(Seat.BB) - 0.25,
                1e-10);
        assertEquals(
                0,
                game.profileUtilitiesBb(solution).values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-10);
        assertEquals(solution, new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(300));
    }

    @Test
    void malformedStatesAndInputsCannotReachDecisionsOrPayoffs() {
        var game =
                new SixMaxHeadsUpPostflopGame(
                        SixMaxHeadsUpFlopGameTest.uncertainGame(1).flop(), 1, 2, 4);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(root, "call"));
        assertThrows(IllegalArgumentException.class, () -> game.chanceOutcomes(root));
        assertThrows(IllegalArgumentException.class, () -> game.terminalUtility(root));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.profileUtilitiesBb(new CfrSolution(1, Map.of())));
        for (var state :
                List.of(
                        new SixMaxHeadsUpPostflopGame.State(-1, "b", null, "", null, ""),
                        new SixMaxHeadsUpPostflopGame.State(0, "", Card.parse("5c"), "", null, ""),
                        new SixMaxHeadsUpPostflopGame.State(
                                0, "kk", Card.parse("Th"), "", null, ""),
                        new SixMaxHeadsUpPostflopGame.State(
                                0, "kk", Card.parse("5c"), "kk", Card.parse("5c"), ""),
                        new SixMaxHeadsUpPostflopGame.State(
                                0, "kk", Card.parse("5c"), "bf", Card.parse("6c"), "")))
            assertThrows(IllegalArgumentException.class, () -> game.isTerminal(state));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxHeadsUpPostflopGame(game.flop(), bad, 1, 1));
        for (var menu :
                List.of(
                        List.of(-0.1),
                        List.of(1.0),
                        List.of(Double.NaN),
                        java.util.Collections.nCopies(9, 0.5)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxHeadsUpPostflopGame(game.flop(), 1, 1, 1, menu));
    }
}
