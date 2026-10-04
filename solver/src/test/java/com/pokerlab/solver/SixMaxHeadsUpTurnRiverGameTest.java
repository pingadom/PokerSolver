package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SixMaxHeadsUpTurnRiverGameTest {
    static SixMaxPolicyTurnTransition transition(SixMaxHeadsUpFlopGame flop) {
        var policy =
                SixMaxHeadsUpFlopGameTest.policy(
                        flop, (s, a) -> a.equals("check") || a.equals("call") ? 1.0 : 0.0);
        return new SixMaxPolicyTurnTransition(flop, policy, List.of("check", "check"));
    }

    static SixMaxHeadsUpTurnRiverGame uncertainGame(double strongOpponentWeight) {
        return new SixMaxHeadsUpTurnRiverGame(
                transition(SixMaxHeadsUpFlopGameTest.uncertainGame(strongOpponentWeight))
                        .conditionOnTurn(Card.parse("5c")),
                3.25,
                6.5);
    }

    static CfrSolution policy(
            SixMaxHeadsUpTurnRiverGame game,
            BiFunction<SixMaxHeadsUpTurnRiverGame.State, String, Double> probability) {
        Map<String, Map<String, Double>> strategies = new LinkedHashMap<>();
        collect(game, game.initialState(), probability, strategies);
        return new CfrSolution(1, strategies);
    }

    private static void collect(
            SixMaxHeadsUpTurnRiverGame game,
            SixMaxHeadsUpTurnRiverGame.State state,
            BiFunction<SixMaxHeadsUpTurnRiverGame.State, String, Double> probability,
            Map<String, Map<String, Double>> strategies) {
        if (game.isTerminal(state)) return;
        if (game.currentPlayer(state) == -1) {
            for (var outcome : game.chanceOutcomes(state))
                collect(game, outcome.state(), probability, strategies);
            return;
        }
        var weights = new LinkedHashMap<String, Double>();
        for (String action : game.legalActions(state))
            weights.put(action, probability.apply(state, action));
        strategies.put(game.currentPlayer(state) + ":" + game.informationSet(state), weights);
        for (String action : game.legalActions(state))
            collect(game, game.afterAction(state, action), probability, strategies);
    }

    static SixMaxHeadsUpFlopGame royalFlop() {
        String[] cards = {"2c 2d", "3c 3d", "4c 4d", "9s 9h", "5c 5d", "As Ks"};
        var ranges =
                java.util.Arrays.stream(cards)
                        .map(
                                text -> {
                                    var split = text.split(" ");
                                    return List.of(
                                            new WeightedCombo(
                                                    Card.parse(split[0]), Card.parse(split[1]), 1));
                                })
                        .toList();
        return new SixMaxHeadsUpFlopGame(
                SixMaxHeadsUpFlopGameTest.handoff(ranges)
                        .conditionOnFlop(
                                List.of(Card.parse("Qs"), Card.parse("Js"), Card.parse("Ts"))),
                3.25);
    }

    @Test
    void physicalChanceHas36RiversAndDoesNotRevealHiddenCards() {
        var game = uncertainGame(1);
        var roots = game.chanceOutcomes(game.initialState());
        assertEquals(4, roots.size());
        assertEquals(1, roots.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(1, roots.stream().map(o -> game.informationSet(o.state())).distinct().count());
        for (var root : roots) {
            var chance =
                    game.chanceOutcomes(
                            game.replay(root.state(), List.of("check", "check"), null, List.of()));
            assertEquals(36, chance.size());
            assertEquals(1, chance.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
            for (var river : chance) {
                assertEquals(1.0 / 36, river.probability());
                for (var hand : game.turn().deals().get(root.state().dealIndex()).hands()) {
                    assertNotEquals(hand.first(), river.state().river());
                    assertNotEquals(hand.second(), river.state().river());
                }
                assertFalse(game.turn().board().contains(river.state().river()));
            }
        }
        String key = game.informationSet(roots.getFirst().state());
        assertTrue(key.contains("9h 9s"));
        assertFalse(key.contains("Ah As"));
        assertFalse(key.contains("Jh Js"));
        var root = roots.getFirst().state();
        var checked = game.replay(root, List.of("check", "check"), Card.parse("6c"), List.of());
        var called = game.replay(root, List.of("bet", "call"), Card.parse("6c"), List.of());
        assertNotEquals(game.informationSet(checked), game.informationSet(called));
        assertNotEquals(
                game.informationSet(checked),
                game.informationSet(
                        game.replay(root, List.of("check", "check"), Card.parse("7c"), List.of())));
    }

    @Test
    void bothStreetsConserveChipsAndUncalledBetsAreReturned() {
        var game =
                new SixMaxHeadsUpTurnRiverGame(
                        transition(royalFlop()).conditionOnTurn(Card.parse("6h")), 3, 10);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        var turnPaths = List.of(List.of("bet", "fold"), List.of("check", "bet", "fold"));
        for (int i = 0; i < turnPaths.size(); i++) {
            var state = game.replay(root, turnPaths.get(i), null, List.of());
            assertEquals(i == 0 ? 3.5 : -3, game.terminalUtilitiesBb(state).get(Seat.BB), 1e-12);
        }
        var turnRounds =
                List.of(
                        List.of("check", "check"),
                        List.of("bet", "call"),
                        List.of("check", "bet", "call"));
        var riverRounds =
                List.of(
                        List.of("check", "check"),
                        List.of("bet", "call"),
                        List.of("check", "bet", "call"),
                        List.of("bet", "fold"),
                        List.of("check", "bet", "fold"));
        for (var turnRound : turnRounds)
            for (var riverRound : riverRounds) {
                double matchedTurn = turnRound.contains("call") ? 3 : 0;
                double matchedRiver = riverRound.contains("call") ? 10 : 0;
                var state = game.replay(root, turnRound, Card.parse("7h"), riverRound);
                double expected =
                        riverRound.equals(List.of("check", "bet", "fold"))
                                ? -3 - matchedTurn
                                : 3.5 + matchedTurn + matchedRiver;
                var actual = game.terminalUtilitiesBb(state);
                assertEquals(expected, actual.get(Seat.BB), 1e-12);
                assertEquals(-0.5, actual.get(Seat.SB));
                assertEquals(
                        0, actual.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
                assertEquals(actual.get(Seat.BB) - 0.25, game.terminalUtility(state), 1e-12);
                assertEquals(actual.get(Seat.BTN) - 0.25, -game.terminalUtility(state), 1e-12);
            }
    }

    @Test
    void stackCapsSkipRiverBettingAfterATurnAllInAndCapRiverBetsAfterCalls() {
        var turn = transition(royalFlop()).conditionOnTurn(Card.parse("6h"));
        var allIn = new SixMaxHeadsUpTurnRiverGame(turn, 1000, 1000);
        assertEquals(97, allIn.turnBetBb());
        var root = allIn.chanceOutcomes(allIn.initialState()).getFirst().state();
        var chance = allIn.replay(root, List.of("bet", "call"), null, List.of());
        assertFalse(allIn.isTerminal(chance));
        assertEquals(-1, allIn.currentPlayer(chance));
        for (var river : allIn.chanceOutcomes(chance)) {
            assertTrue(allIn.isTerminal(river.state()));
            assertEquals(0, allIn.riverBetBb(river.state()));
            assertEquals(100.5, allIn.terminalUtilitiesBb(river.state()).get(Seat.BB));
            assertThrows(IllegalArgumentException.class, () -> allIn.legalActions(river.state()));
        }
        var capped = new SixMaxHeadsUpTurnRiverGame(turn, 95, 1000);
        root = capped.chanceOutcomes(capped.initialState()).getFirst().state();
        var river = capped.replay(root, List.of("bet", "call"), Card.parse("7h"), List.of());
        assertEquals(2, capped.riverBetBb(river));
        assertEquals(
                100.5,
                capped.terminalUtilitiesBb(
                                capped.replay(
                                        root,
                                        List.of("bet", "call"),
                                        Card.parse("7h"),
                                        List.of("bet", "call")))
                        .get(Seat.BB));
    }

    @Test
    void exactCheckdownAndSolvedPolicyHaveIndependentQualityBounds() {
        var game = uncertainGame(1);
        var check = policy(game, (s, a) -> a.equals("check") || a.equals("call") ? 1.0 : 0.0);
        var baseline = game.exactCheckdownUtilitiesBb();
        for (Seat seat : Seat.values())
            assertEquals(baseline.get(seat), game.profileUtilitiesBb(check).get(seat), 1e-12);
        var solved = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1000);
        var quality = HeadsUpBestResponse.assess(game, solved);
        assertTrue(quality.gap() < 0.02, "gap=" + quality.gap());
        assertTrue(solved.strategy().size() > 400);
        assertEquals(
                0,
                game.profileUtilitiesBb(solved).values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-10);
        assertEquals(
                quality.profileValue(), game.profileUtilitiesBb(solved).get(Seat.BB) - 0.25, 1e-10);
    }

    @Test
    void invalidStatesCardsPoliciesAndBetsAreRejected() {
        var game = uncertainGame(1);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertThrows(
                IllegalArgumentException.class,
                () -> game.replay(root, List.of("check", "check"), Card.parse("Th"), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.replay(root, List.of(), Card.parse("6c"), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.replay(root, List.of(), null, List.of("check")));
        assertThrows(IllegalArgumentException.class, () -> game.terminalUtility(root));
        assertThrows(IllegalArgumentException.class, () -> game.chanceOutcomes(root));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxHeadsUpTurnRiverGame.State(-1, "b", null, "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxHeadsUpTurnRiverGame.State(0, null, null, "")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.isTerminal(
                                new SixMaxHeadsUpTurnRiverGame.State(
                                        0, "kk", Card.parse("Th"), "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.profileUtilitiesBb(new CfrSolution(1, Map.of())));
        for (double bet : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxHeadsUpTurnRiverGame(game.turn(), bet, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxHeadsUpTurnRiverGame(game.turn(), 1, bet));
        }
    }

    @Test
    void boardTiesSplitDeadMoneyAndPriorCalledFlopChipsSurviveTheHandoff() {
        var tied = uncertainGame(1);
        for (var outcome : tied.chanceOutcomes(tied.initialState())) {
            var terminal =
                    tied.replay(
                            outcome.state(),
                            List.of("bet", "call"),
                            Card.parse("6c"),
                            List.of("bet", "call"));
            assertEquals(0.25, tied.terminalUtilitiesBb(terminal).get(Seat.BB), 1e-12);
            assertEquals(0.25, tied.terminalUtilitiesBb(terminal).get(Seat.BTN), 1e-12);
            assertEquals(0, tied.terminalUtility(terminal), 1e-12);
        }
        var flop = royalFlop();
        var call =
                SixMaxHeadsUpFlopGameTest.policy(
                        flop, (s, a) -> a.equals("bet") || a.equals("call") ? 1.0 : 0.0);
        var transition = new SixMaxPolicyTurnTransition(flop, call, List.of("bet", "call"));
        var game =
                new SixMaxHeadsUpTurnRiverGame(transition.conditionOnTurn(Card.parse("6h")), 1, 1);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        var terminal =
                game.replay(
                        root,
                        List.of("check", "check"),
                        Card.parse("7h"),
                        List.of("check", "check"));
        assertEquals(6.75, game.terminalUtilitiesBb(terminal).get(Seat.BB), 1e-12);
        assertEquals(-6.25, game.terminalUtilitiesBb(terminal).get(Seat.BTN), 1e-12);
    }
}
