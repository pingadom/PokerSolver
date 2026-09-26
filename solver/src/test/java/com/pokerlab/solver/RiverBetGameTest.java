package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiverBetGameTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    @Test
    void riverTreeHasLegalActionsAndConservesTheCenteredPot() {
        RiverBetSpot spot = RiverValidationSpot.create();
        RiverBetGame game = spot.game();
        assertEquals(9, game.chanceOutcomes(game.initialState()).size());
        RiverBetGame.State deal =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.first().key().equals("Ah As"))
                        .filter(state -> state.second().key().equals("Kc Kd"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(-1, game.currentPlayer(game.initialState()));
        assertEquals(0, game.currentPlayer(deal));
        assertEquals(List.of("k", "b"), game.legalActions(deal));
        assertEquals("Ah As:", game.informationSet(deal));
        RiverBetGame.State checked = game.afterAction(deal, "k");
        RiverBetGame.State bet = game.afterAction(deal, "b");
        assertEquals(1, game.currentPlayer(checked));
        assertEquals(1, game.currentPlayer(bet));
        assertEquals(List.of("c", "f"), game.legalActions(bet));
        assertEquals(10, game.terminalUtility(game.afterAction(checked, "k")));
        assertEquals(10, game.terminalUtility(game.afterAction(bet, "f")));
        assertEquals(20, game.terminalUtility(game.afterAction(bet, "c")));
        RiverBetGame.State checkedBet = game.afterAction(checked, "b");
        assertEquals(0, game.currentPlayer(checkedBet));
        assertEquals(-10, game.terminalUtility(game.afterAction(checkedBet, "f")));
        assertEquals(20, game.terminalUtility(game.afterAction(checkedBet, "c")));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(deal, "c"));
        assertFalse(game.legalActions(checkedBet).contains("b"));
    }

    @Test
    void blockedDealsAreRemovedBeforeWeightNormalization() {
        RiverBetSpot spot =
                new RiverBetSpot(
                        "blocked-river-check",
                        PreflopAllInSpot.Seat.BTN,
                        PreflopAllInSpot.Seat.BB,
                        RiverValidationSpot.create().board(),
                        20,
                        80,
                        10,
                        List.of(combo("As", "Ah", 2), combo("Kc", "Kd", 1)),
                        List.of(combo("As", "Qh", 1), combo("5c", "5d", 3)));
        RiverBetGame game = spot.game();
        List<Double> probabilities =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(ChanceOutcome::probability)
                        .toList();
        assertEquals(3, probabilities.size());
        assertEquals(1, probabilities.stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(List.of(0.6, 0.3, 0.1), probabilities);
    }

    @Test
    void rejectsBadBoardsRangesAndBetSizes() {
        RiverBetSpot spot = RiverValidationSpot.create();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverBetSpot(
                                spot.id(),
                                spot.firstSeat(),
                                spot.secondSeat(),
                                List.of(
                                        Card.parse("2c"),
                                        Card.parse("2c"),
                                        Card.parse("4h"),
                                        Card.parse("8s"),
                                        Card.parse("9c")),
                                spot.potBb(),
                                spot.remainingStackBb(),
                                spot.betBb(),
                                spot.firstRange(),
                                spot.secondRange()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverBetSpot(
                                spot.id(),
                                spot.firstSeat(),
                                spot.secondSeat(),
                                spot.board(),
                                spot.potBb(),
                                spot.remainingStackBb(),
                                81,
                                spot.firstRange(),
                                spot.secondRange()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiverBetSpot(
                                spot.id(),
                                spot.firstSeat(),
                                spot.secondSeat(),
                                spot.board(),
                                spot.potBb(),
                                spot.remainingStackBb(),
                                spot.betBb(),
                                List.of(combo("2c", "Ah", 1)),
                                spot.secondRange()));
        assertEquals(
                spot.contentHash(),
                new RiverBetSpot(
                                spot.id(),
                                spot.firstSeat(),
                                spot.secondSeat(),
                                List.of(
                                        spot.board().get(4),
                                        spot.board().get(3),
                                        spot.board().get(2),
                                        spot.board().get(1),
                                        spot.board().get(0)),
                                spot.potBb(),
                                spot.remainingStackBb(),
                                spot.betBb(),
                                spot.firstRange().reversed(),
                                spot.secondRange().reversed())
                        .contentHash());
    }

    @Test
    void bestResponseGapShrinksAndBoundsProfileValue() {
        RiverBetGame game = RiverValidationSpot.create().game();
        CfrSolution early = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        CfrSolution solved = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(5_000);
        RiverBetBestResponse.Report earlyReport = RiverBetBestResponse.assess(game, early);
        RiverBetBestResponse.Report report = RiverBetBestResponse.assess(game, solved);
        assertTrue(report.gap() < earlyReport.gap());
        assertTrue(report.gap() < 0.05);
        assertTrue(report.secondBestResponse() <= report.profileValue());
        assertTrue(report.profileValue() <= report.firstBestResponse());
        for (Map<String, Double> strategy : solved.strategy().values())
            assertEquals(
                    1, strategy.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void bestResponseMatchesIndependentPureStrategyEnumeration() {
        RiverBetGame game = RiverValidationSpot.create().game();
        CfrSolution profile = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(40);
        RiverBetBestResponse.Report report = RiverBetBestResponse.assess(game, profile);
        assertEquals(pureBestResponse(game, profile, 0), report.firstBestResponse(), 1e-9);
        assertEquals(pureBestResponse(game, profile, 1), report.secondBestResponse(), 1e-9);
    }

    private static double pureBestResponse(RiverBetGame game, CfrSolution profile, int player) {
        List<String> informationSets =
                profile.strategy().keySet().stream()
                        .filter(key -> key.startsWith(player + ":"))
                        .sorted()
                        .toList();
        double best = player == 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int mask = 0; mask < 1 << informationSets.size(); mask++) {
            Map<String, Map<String, Double>> pure = new HashMap<>(profile.strategy());
            for (int index = 0; index < informationSets.size(); index++) {
                String key = informationSets.get(index);
                List<String> actions = new ArrayList<>(profile.strategy().get(key).keySet());
                actions.sort(String::compareTo);
                boolean chooseFirst = (mask & (1 << index)) != 0;
                pure.put(
                        key,
                        Map.of(
                                actions.get(0), chooseFirst ? 1.0 : 0.0,
                                actions.get(1), chooseFirst ? 0.0 : 1.0));
            }
            double value =
                    StrategyEvaluator.playerZeroUtility(
                            game, new CfrSolution(profile.iterations(), pure));
            best = player == 0 ? Math.max(best, value) : Math.min(best, value);
        }
        return best;
    }
}
