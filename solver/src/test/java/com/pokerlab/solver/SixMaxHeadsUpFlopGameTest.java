package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SixMaxHeadsUpFlopGameTest {
    private static final List<PublicAction> HISTORY =
            List.of(
                    new PublicAction(Seat.UTG, "fold"), new PublicAction(Seat.HJ, "fold"),
                    new PublicAction(Seat.CO, "fold"), new PublicAction(Seat.BTN, "raise:3.0"),
                    new PublicAction(Seat.SB, "fold"), new PublicAction(Seat.BB, "call"));

    private static WeightedCombo combo(String cards, double weight) {
        var split = cards.split(" ");
        return new WeightedCombo(Card.parse(split[0]), Card.parse(split[1]), weight);
    }

    static SixMaxPolicyFlopTransition handoff(List<List<WeightedCombo>> ranges) {
        var source =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                        ranges,
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        Map<String, Map<String, Double>> strategies = new LinkedHashMap<>();
        for (var outcome : source.chanceOutcomes(source.initialState())) {
            var state = outcome.state();
            for (var action : HISTORY) {
                Map<String, Double> weights = new LinkedHashMap<>();
                double selected =
                        action.seat() == Seat.UTG && ranges.getFirst().size() > 1
                                ? source.dealtHands(state).getFirst().key().equals("Ah As")
                                        ? 0.25
                                        : 0.75
                                : 1;
                String other =
                        source.legalActions(state).stream()
                                .filter(a -> !a.equals(action.action()))
                                .findFirst()
                                .orElseThrow();
                for (String legal : source.legalActions(state))
                    weights.put(
                            legal,
                            legal.equals(action.action())
                                    ? selected
                                    : legal.equals(other) ? 1 - selected : 0);
                strategies.put(
                        source.currentPlayer(state) + ":" + source.informationSet(state), weights);
                state = source.afterAction(state, action.action());
            }
        }
        return new SixMaxPolicyFlopTransition(source, new CfrSolution(1, strategies), HISTORY);
    }

    static SixMaxHeadsUpFlopGame uncertainGame(double strongOpponentWeight) {
        var ranges =
                List.of(
                        List.of(combo("As Ah", 1), combo("Ac Ad", 3)),
                        List.of(combo("Ks Kh", 1)),
                        List.of(combo("Qs Qh", 1)),
                        List.of(combo("Js Jh", strongOpponentWeight), combo("8s 8h", 1)),
                        List.of(combo("Ts Th", 1)),
                        List.of(combo("9s 9h", 1)));
        var flop =
                handoff(ranges)
                        .conditionOnFlop(
                                List.of(Card.parse("2d"), Card.parse("3d"), Card.parse("4d")));
        return new SixMaxHeadsUpFlopGame(flop, 3.25);
    }

    static CfrSolution policy(
            SixMaxHeadsUpFlopGame game,
            BiFunction<SixMaxHeadsUpFlopGame.State, String, Double> probability) {
        Map<String, Map<String, Double>> strategies = new LinkedHashMap<>();
        for (var outcome : game.chanceOutcomes(game.initialState()))
            collect(game, outcome.state(), probability, strategies);
        return new CfrSolution(1, strategies);
    }

    private static void collect(
            SixMaxHeadsUpFlopGame game,
            SixMaxHeadsUpFlopGame.State state,
            BiFunction<SixMaxHeadsUpFlopGame.State, String, Double> probability,
            Map<String, Map<String, Double>> strategies) {
        if (game.isTerminal(state)) return;
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String action : game.legalActions(state))
            weights.put(action, probability.apply(state, action));
        strategies.put(game.currentPlayer(state) + ":" + game.informationSet(state), weights);
        for (String action : game.legalActions(state))
            collect(game, game.afterAction(state, action), probability, strategies);
    }

    @Test
    void exactCheckdownRetainsFoldedCardsAndMergesIndistinguishableDeals() {
        var game = uncertainGame(1);
        assertEquals(Seat.BB, game.seat(0));
        assertEquals(Seat.BTN, game.seat(1));
        var chance = game.chanceOutcomes(game.initialState());
        assertEquals(4, chance.size());
        assertEquals(1, chance.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(
                1, chance.stream().map(o -> game.informationSet(o.state())).distinct().count());
        String key = game.informationSet(chance.getFirst().state());
        assertTrue(key.contains("9h 9s"));
        assertTrue(key.contains("2d 3d 4d"));
        assertFalse(key.contains("Ah As"));
        assertFalse(key.contains("Jh Js"));
        assertEquals(0.25, game.liveUtilityOffsetBb());
        var check =
                policy(
                        game,
                        (state, action) ->
                                action.equals("check") || action.equals("call") ? 1.0 : 0.0);
        var baseline = game.flop().exactCheckdown();
        assertEquals(4 * 666L, baseline.runouts());
        assertEquals(
                baseline.utilitiesBb().get(Seat.BB),
                game.profileUtilitiesBb(check).get(Seat.BB),
                1e-12);
        assertEquals(
                baseline.utilitiesBb().get(Seat.BTN),
                game.profileUtilitiesBb(check).get(Seat.BTN),
                1e-12);
        for (int i = 0; i < 4; i++) {
            assertEquals(666, game.flop().exactCheckdown(i).runouts());
            assertEquals(37, game.flop().undealtCards(i).size());
            assertFalse(game.flop().undealtCards(i).contains(Card.parse("Th")));
        }
    }

    @Test
    void everyTerminalPathConservesAllSixSeatsAndTheCenteredGameIsZeroSum() {
        var ranges =
                List.of(
                        List.of(combo("2c 2d", 1)),
                        List.of(combo("3c 3d", 1)),
                        List.of(combo("4c 4d", 1)),
                        List.of(combo("9s 9h", 1)),
                        List.of(combo("5c 5d", 1)),
                        List.of(combo("As Ks", 1)));
        var flop =
                handoff(ranges)
                        .conditionOnFlop(
                                List.of(Card.parse("Qs"), Card.parse("Js"), Card.parse("Ts")));
        var game = new SixMaxHeadsUpFlopGame(flop, 3.25);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        var paths =
                List.of(
                        List.of("check", "check"),
                        List.of("bet", "call"),
                        List.of("bet", "fold"),
                        List.of("check", "bet", "call"),
                        List.of("check", "bet", "fold"));
        double[] first = {3.5, 6.75, 3.5, 6.75, -3};
        double[] second = {-3, -6.25, -3, -6.25, 3.5};
        for (int i = 0; i < paths.size(); i++) {
            var state = game.replay(root, paths.get(i));
            var utilities = game.terminalUtilitiesBb(state);
            assertEquals(first[i], utilities.get(Seat.BB), 1e-12);
            assertEquals(second[i], utilities.get(Seat.BTN), 1e-12);
            assertEquals(-0.5, utilities.get(Seat.SB));
            assertEquals(
                    0, utilities.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
            assertEquals(utilities.get(Seat.BB) - 0.25, game.terminalUtility(state), 1e-12);
            assertEquals(utilities.get(Seat.BTN) - 0.25, -game.terminalUtility(state), 1e-12);
        }
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2_000);
        var quality = HeadsUpBestResponse.assess(game, solution);
        assertTrue(quality.gap() < 1e-4, "gap=" + quality.gap());
        assertEquals(3.5, game.profileUtilitiesBb(solution).get(Seat.BB), 1e-4);
        assertEquals(solution, new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2_000));
    }

    @Test
    void cfrAndBestResponseWorkOnTheHiddenCorrelatedChanceRoot() {
        var game = uncertainGame(1);
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(5_000);
        assertEquals(6, solution.strategy().size());
        var quality = HeadsUpBestResponse.assess(game, solution);
        assertTrue(quality.gap() < 0.001, "gap=" + quality.gap());
        assertEquals(
                0,
                game.profileUtilitiesBb(solution).values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-10);
        assertEquals(-0.5, game.profileUtilitiesBb(solution).get(Seat.SB));
        for (var weights : solution.strategy().values())
            assertEquals(
                    1, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }

    @Test
    void survivingHandCorrelationNeverBecomesAProductOfMarginals() {
        var ranges =
                List.of(
                        List.of(combo("As Ah", 1), combo("Ac Ad", 3)),
                        List.of(combo("Ks Kh", 1)),
                        List.of(combo("Qs Qh", 1)),
                        List.of(combo("Js Jh", 1), combo("9s 9h", 1)),
                        List.of(combo("Ts Th", 1)),
                        List.of(combo("9s 9h", 1), combo("8s 8h", 1)));
        var flop =
                handoff(ranges)
                        .conditionOnFlop(
                                List.of(Card.parse("2d"), Card.parse("3d"), Card.parse("4d")));
        var game = new SixMaxHeadsUpFlopGame(flop, 3.25);
        assertEquals(6, game.chanceOutcomes(game.initialState()).size());
        assertEquals(
                2,
                game.chanceOutcomes(game.initialState()).stream()
                        .map(outcome -> game.informationSet(outcome.state()))
                        .distinct()
                        .count());
        assertTrue(flop.handoff().marginal(Seat.BB).get("9h 9s") > 0);
        assertTrue(flop.handoff().marginal(Seat.BTN).get("9h 9s") > 0);
        assertTrue(
                flop.deals().stream()
                        .noneMatch(
                                deal ->
                                        deal.hands().get(Seat.BB.ordinal()).key().equals("9h 9s")
                                                && deal.hands()
                                                        .get(Seat.BTN.ordinal())
                                                        .key()
                                                        .equals("9h 9s")));
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(5_000);
        assertTrue(HeadsUpBestResponse.assess(game, solution).gap() < 0.001);
        var evaluator = new SixMaxHeadsUpFlopDecisionEvaluator(game, solution);
        assertEquals(2, evaluator.evaluate(List.of(), "9h 9s").compatibleJointDeals());
        assertEquals(4, evaluator.evaluate(List.of(), "8h 8s").compatibleJointDeals());
    }

    @Test
    void rejectsImpossibleActionsAndInvalidOrOversizedBets() {
        var game = uncertainGame(1);
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHeadsUpFlopGame(game.flop(), Double.NaN));
        assertThrows(
                IllegalArgumentException.class, () -> new SixMaxHeadsUpFlopGame(game.flop(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHeadsUpFlopGame(game.flop(), 97.01));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(root, "call"));
        assertThrows(IllegalArgumentException.class, () -> game.chanceOutcomes(root));
        assertThrows(IllegalArgumentException.class, () -> game.terminalUtility(root));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxHeadsUpFlopGame.State(-1, "b")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxHeadsUpFlopGame.State(99, "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxHeadsUpFlopGame.State(0, "kbb")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.replay(root, List.of("bet", "fold", "check")));
        var allIn = new SixMaxHeadsUpFlopGame(game.flop(), 97);
        var dealt = allIn.chanceOutcomes(allIn.initialState()).getFirst().state();
        assertTrue(allIn.isTerminal(allIn.replay(dealt, List.of("bet", "call"))));
        assertEquals(
                0,
                allIn
                        .terminalUtilitiesBb(allIn.replay(dealt, List.of("bet", "call")))
                        .values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-10);
    }
}
