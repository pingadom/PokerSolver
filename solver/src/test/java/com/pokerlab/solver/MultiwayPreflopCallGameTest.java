package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiwayPreflopCallGameTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<PreflopAllInSpot.Seat> sixSeats() {
        return List.of(PreflopAllInSpot.Seat.values());
    }

    private static MultiwayPreflopCallGame sixPlayerGame(double deadMoney) {
        return new MultiwayPreflopCallGame(
                sixSeats(),
                List.of(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KS", "KH", 1)),
                        List.of(combo("QS", "QH", 1)),
                        List.of(combo("JS", "JH", 1)),
                        List.of(combo("TS", "TH", 1)),
                        List.of(combo("9S", "9H", 1))),
                List.of(10.0, 1.0, 2.0, 0.0, 0.5, 1.0),
                10,
                deadMoney,
                (dealt, activeMask) -> {
                    double[] shares = new double[dealt.size()];
                    shares[0] = 1;
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    @Test
    void sixSeatsActInOrderAndEveryTerminalConservesChips() {
        MultiwayPreflopCallGame game = sixPlayerGame(0.75);
        MultiwayPreflopCallGame.State root =
                game.chanceOutcomes(game.initialState()).get(0).state();
        assertEquals(6, game.playerCount());
        assertEquals(1, game.currentPlayer(root));
        assertEquals(List.of("c", "f"), game.legalActions(root));
        MultiwayPreflopCallGame.State allFold = root;
        for (int player = 1; player < 6; player++) {
            assertEquals(player, game.currentPlayer(allFold));
            allFold = game.afterAction(allFold, "f");
        }
        assertArrayEquals(
                new double[] {5.25, -1, -2, 0, -0.5, -1}, game.terminalUtilities(allFold));
        assertEquals(0.75, sum(game.terminalUtilities(allFold)), 1e-12);

        for (int mask = 0; mask < 32; mask++) {
            MultiwayPreflopCallGame.State state = root;
            for (int player = 1; player < 6; player++)
                state = game.afterAction(state, (mask & (1 << (player - 1))) == 0 ? "f" : "c");
            assertTrue(game.isTerminal(state));
            assertEquals(0.75, sum(game.terminalUtilities(state)), 1e-9);
        }
    }

    @Test
    void shorterCallersCreateMainAndSidePotsWithAnExactDeviationCheck() {
        List<PreflopAllInSpot.Seat> seats =
                List.of(
                        PreflopAllInSpot.Seat.UTG,
                        PreflopAllInSpot.Seat.BTN,
                        PreflopAllInSpot.Seat.BB);
        List<List<WeightedCombo>> ranges =
                List.of(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KS", "KH", 1)),
                        List.of(combo("QS", "QH", 1)));
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        seats,
                        ranges,
                        List.of(30.0, 1.0, 2.0),
                        List.of(30.0, 10.0, 20.0),
                        0.5,
                        (dealt, mask) -> {
                            double[] shares = new double[3];
                            shares[(mask & 0b010) != 0 ? 1 : (mask & 0b100) != 0 ? 2 : 0] = 1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        assertEquals(List.of(30.0, 10.0, 20.0), game.stacksBb());
        assertEquals(9, game.callCostBb(1));
        assertEquals(18, game.callCostBb(2));
        assertEquals(33.5, game.potBeforeDecision(""));
        assertEquals(42.5, game.potBeforeDecision("c"));
        var root = game.chanceOutcomes(game.initialState()).get(0).state();
        assertArrayEquals(
                new double[] {3.5, -1, -2},
                game.terminalUtilities(game.afterAction(game.afterAction(root, "f"), "f")),
                1e-12);
        assertArrayEquals(
                new double[] {-20, 20.5, 0},
                game.terminalUtilities(game.afterAction(game.afterAction(root, "c"), "c")),
                1e-12);
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(500);
        assertTrue(MultiwayCallBestResponse.assess(game, solution).nashConvBb() < 0.05);
        assertEquals(0.5, sum(MultiPlayerStrategyEvaluator.utilities(game, solution)), 1e-9);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayPreflopCallGame(
                                seats,
                                ranges,
                                List.of(30.0, 10.0, 2.0),
                                List.of(30.0, 10.0, 20.0),
                                0,
                                (dealt, mask) ->
                                        MultiwayShowdownEstimate.certain(new double[] {1, 0, 0})));
    }

    @Test
    void everySixSeatUnequalStackTerminalConservesChips() {
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        sixSeats(),
                        List.of(
                                List.of(combo("AS", "AH", 1)),
                                List.of(combo("KS", "KH", 1)),
                                List.of(combo("QS", "QH", 1)),
                                List.of(combo("JS", "JH", 1)),
                                List.of(combo("TS", "TH", 1)),
                                List.of(combo("9S", "9H", 1))),
                        List.of(30.0, 1.0, 2.0, 0.0, 0.5, 1.0),
                        List.of(30.0, 10.0, 20.0, 15.0, 25.0, 5.0),
                        0.75,
                        (dealt, activeMask) -> {
                            double[] shares = new double[6];
                            shares[31 - Integer.numberOfLeadingZeros(activeMask)] = 1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var root = game.chanceOutcomes(game.initialState()).get(0).state();
        for (int calls = 0; calls < 32; calls++) {
            var state = root;
            for (int responder = 0; responder < 5; responder++)
                state = game.afterAction(state, (calls & (1 << responder)) == 0 ? "f" : "c");
            assertEquals(0.75, sum(game.terminalUtilities(state)), 1e-9);
        }
        var allCall = root;
        for (int responder = 0; responder < 5; responder++)
            allCall = game.afterAction(allCall, "c");
        assertArrayEquals(
                new double[] {-25, -10, -20, -15, 45, 25.75},
                game.terminalUtilities(allCall),
                1e-12);
    }

    @Test
    void jointDealsRemoveBlockedCardsAndKeepWeights() {
        List<List<WeightedCombo>> ranges = new ArrayList<>();
        ranges.add(List.of(combo("AS", "AH", 2), combo("KS", "KH", 1)));
        ranges.add(List.of(combo("AS", "AD", 1), combo("QC", "QD", 3)));
        ranges.add(List.of(combo("7S", "7H", 1)));
        ranges.add(List.of(combo("8S", "8H", 1)));
        ranges.add(List.of(combo("9S", "9H", 1)));
        ranges.add(List.of(combo("TS", "TH", 1)));
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        sixSeats(),
                        ranges,
                        List.of(10.0, 1.0, 1.0, 1.0, 0.5, 1.0),
                        10,
                        0,
                        (dealt, mask) -> {
                            double[] shares = new double[6];
                            shares[0] = 1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var outcomes = game.chanceOutcomes(game.initialState());
        assertEquals(3, outcomes.size());
        assertEquals(
                List.of(0.6, 0.1, 0.3), outcomes.stream().map(ChanceOutcome::probability).toList());
        assertTrue(
                outcomes.stream()
                        .allMatch(
                                outcome ->
                                        !game.dealtCombos(outcome.state())
                                                .get(0)
                                                .conflictsWith(
                                                        game.dealtCombos(outcome.state()).get(1))));
    }

    @Test
    void sixPlayerRegretMatchingLearnsToFoldWhenAggressorAlwaysWins() {
        MultiwayPreflopCallGame game = sixPlayerGame(0);
        CfrSolution early = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(250);
        var root = game.chanceOutcomes(game.initialState()).get(0).state();
        var state = root;
        for (int player = 1; player < 6; player++) {
            assertTrue(solution.at(player, game.informationSet(state)).get("f") > 0.99);
            state = game.afterAction(state, "f");
        }
        assertEquals(
                solution, new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(250));
        var initialReport = MultiwayCallBestResponse.assess(game, early);
        var finalReport = MultiwayCallBestResponse.assess(game, solution);
        assertTrue(initialReport.nashConvBb() > 1);
        assertTrue(finalReport.nashConvBb() < 0.01, () -> "NashConv: " + finalReport.nashConvBb());
        assertEquals(
                0,
                sum(
                        finalReport.profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray()),
                1e-9);
        assertEquals(0, finalReport.deviationGainsBb().get(0));
    }

    @Test
    void threePlayerResponseKeepsStrongCallerAndFoldsDeadCaller() {
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(
                                PreflopAllInSpot.Seat.UTG,
                                PreflopAllInSpot.Seat.BTN,
                                PreflopAllInSpot.Seat.BB),
                        List.of(
                                List.of(combo("AS", "AH", 1)),
                                List.of(combo("KS", "KH", 1)),
                                List.of(combo("QS", "QH", 1))),
                        List.of(10.0, 1.0, 1.0),
                        10,
                        0,
                        (dealt, activeMask) -> {
                            double[] shares = new double[3];
                            shares[(activeMask & 0b010) != 0 ? 1 : 0] = 1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(250);
        var state = game.chanceOutcomes(game.initialState()).get(0).state();
        assertTrue(solution.at(1, game.informationSet(state)).get("c") > 0.99);
        state = game.afterAction(state, "c");
        assertTrue(solution.at(2, game.informationSet(state)).get("f") > 0.99);
        assertTrue(MultiwayCallBestResponse.assess(game, solution).nashConvBb() < 0.01);
    }

    @Test
    void deviationReportMatchesAnalyticThreeSeatGame() {
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(
                                PreflopAllInSpot.Seat.UTG,
                                PreflopAllInSpot.Seat.BTN,
                                PreflopAllInSpot.Seat.BB),
                        List.of(
                                List.of(combo("AS", "AH", 1)),
                                List.of(combo("KS", "KH", 1)),
                                List.of(combo("QS", "QH", 1))),
                        List.of(10.0, 1.0, 1.0),
                        10,
                        0,
                        (dealt, mask) -> MultiwayShowdownEstimate.certain(new double[] {1, 0, 0}));
        var state = game.chanceOutcomes(game.initialState()).get(0).state();
        CfrSolution profile =
                new CfrSolution(
                        1,
                        Map.of(
                                "1:" + game.informationSet(state), Map.of("c", 0.25, "f", 0.75),
                                "2:" + game.informationSet(game.afterAction(state, "c")),
                                        Map.of("c", 0.5, "f", 0.5),
                                "2:" + game.informationSet(game.afterAction(state, "f")),
                                        Map.of("c", 0.5, "f", 0.5)));
        var report = MultiwayCallBestResponse.assess(game, profile);
        assertEquals(-3.25, report.profileUtilitiesBb().get(1), 1e-12);
        assertEquals(-5.5, report.profileUtilitiesBb().get(2), 1e-12);
        assertEquals(2.25, report.deviationGainsBb().get(1), 1e-12);
        assertEquals(4.5, report.deviationGainsBb().get(2), 1e-12);
        assertEquals(6.75, report.nashConvBb(), 1e-12);
    }

    @Test
    void rejectsInvalidOracleSharesAndImpossibleDeals() {
        var aces = combo("AS", "AH", 1);
        var kings = combo("KS", "KH", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayPreflopCallGame(
                                List.of(PreflopAllInSpot.Seat.UTG, PreflopAllInSpot.Seat.BB),
                                List.of(List.of(aces), List.of(kings)),
                                List.of(10.0, 1.0),
                                10,
                                0,
                                (dealt, mask) ->
                                        MultiwayShowdownEstimate.certain(new double[] {0.5, 0.4})));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayPreflopCallGame(
                                List.of(PreflopAllInSpot.Seat.UTG, PreflopAllInSpot.Seat.BB),
                                List.of(List.of(aces), List.of(aces)),
                                List.of(10.0, 1.0),
                                10,
                                0,
                                (dealt, mask) ->
                                        MultiwayShowdownEstimate.certain(new double[] {0.5, 0.5})));
    }

    @Test
    void sampledSixWayShowdownIsReproducibleAndSplitsThePot() {
        List<WeightedCombo> dealt =
                List.of(
                        combo("AS", "AH", 1),
                        combo("KS", "KH", 1),
                        combo("QS", "QH", 1),
                        combo("JS", "JH", 1),
                        combo("TS", "TH", 1),
                        combo("9S", "9H", 1));
        SeededMultiwayShowdownOracle oracle = new SeededMultiwayShowdownOracle(500, 42);
        double[] shares = oracle.estimate(dealt, 0b111111).shares();
        assertArrayEquals(shares, oracle.estimate(dealt, 0b111111).shares());
        assertEquals(1, sum(shares), 1e-12);
        assertEquals(500, oracle.estimate(dealt, 0b111111).trials());
        assertTrue(oracle.estimate(dealt, 0b111111).standardErrors()[0] > 0);
        double[] headsUp = oracle.estimate(dealt, 0b000011).shares();
        assertEquals(0, headsUp[2]);
        assertEquals(0, headsUp[5]);
        assertEquals(1, sum(headsUp), 1e-12);
    }

    @Test
    void sampledGameReportsPayoffUncertaintySeparatelyFromDeviation() {
        var game =
                new MultiwayPreflopCallGame(
                        List.of(PreflopAllInSpot.Seat.UTG, PreflopAllInSpot.Seat.BB),
                        List.of(List.of(combo("AS", "AH", 1)), List.of(combo("KS", "KH", 1))),
                        List.of(10.0, 1.0),
                        10,
                        0,
                        new SeededMultiwayShowdownOracle(500, 42));
        assertTrue(game.maximumTerminalPayoffStandardErrorBb() > 0);
    }

    @Test
    void twoWaySampleAgreesWithIndependentExactEnumerator() {
        WeightedCombo aces = combo("AS", "AH", 1);
        WeightedCombo kings = combo("KS", "KH", 1);
        var sampled =
                new SeededMultiwayShowdownOracle(10_000, 42).estimate(List.of(aces, kings), 0b11);
        var exact = new ExactPreflopEquityOracle().estimate(aces, kings);
        assertEquals(exact.equity(), sampled.shares()[0], 4 * sampled.standardErrors()[0]);
    }

    @Test
    void rejectsPayoffTablesBeyondTheResearchCap() {
        List<List<WeightedCombo>> ranges =
                List.of(
                        List.of(combo("AS", "AH", 1), combo("KS", "KH", 1), combo("QS", "QH", 1)),
                        List.of(combo("AD", "AC", 1), combo("KD", "KC", 1), combo("QD", "QC", 1)),
                        List.of(combo("JS", "JH", 1), combo("TS", "TH", 1), combo("9S", "9H", 1)),
                        List.of(combo("JD", "JC", 1), combo("TD", "TC", 1), combo("9D", "9C", 1)),
                        List.of(combo("8S", "8H", 1), combo("7S", "7H", 1), combo("6S", "6H", 1)),
                        List.of(combo("8D", "8C", 1), combo("7D", "7C", 1), combo("6D", "6C", 1)));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MultiwayPreflopCallGame(
                                        sixSeats(),
                                        ranges,
                                        List.of(10.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                                        10,
                                        0,
                                        (dealt, mask) -> {
                                            throw new AssertionError(
                                                    "Payoff computation should not begin");
                                        }));
        assertTrue(error.getMessage().contains("payoff table cap"));
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum;
    }
}
