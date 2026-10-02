package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopCheckdownGameTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<List<WeightedCombo>> oneDeal() {
        return List.of(
                List.of(combo("AS", "AH", 1)),
                List.of(combo("KS", "KH", 1)),
                List.of(combo("QS", "QH", 1)),
                List.of(combo("JS", "JH", 1)),
                List.of(combo("TS", "TH", 1)),
                List.of(combo("9S", "9H", 1)));
    }

    private static MultiwayShowdownOracle equalShareOracle() {
        return (hands, mask) -> {
            double[] shares = new double[6];
            for (int seat = 0; seat < 6; seat++)
                if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / Integer.bitCount(mask);
            return MultiwayShowdownEstimate.certain(shares);
        };
    }

    private static SixMaxPreflopCheckdownGame game(CashRakeRule rake) {
        return new SixMaxPreflopCheckdownGame(
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                oneDeal(),
                rake,
                equalShareOracle());
    }

    private static SixMaxPreflopCheckdownGame.State action(
            SixMaxPreflopCheckdownGame game,
            SixMaxPreflopCheckdownGame.State state,
            String action) {
        assertTrue(game.legalActions(state).contains(action));
        return game.afterAction(state, action);
    }

    @Test
    void publicTreeReachesFoldWinCalledAllInAndExplicitCheckdown() {
        var game = game(CashRakeRule.none());
        var summary = game.treeSummary();
        assertTrue(summary.decisionStates() > 0);
        assertTrue(summary.uncontestedTerminals() > 0);
        assertTrue(summary.allInTerminals() > 0);
        assertTrue(summary.checkdownTerminals() > 0);
        assertTrue(summary.totalStates() <= SixMaxPreflopCheckdownGame.MAX_PUBLIC_STATES);

        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertEquals(UTG.ordinal(), game.currentPlayer(root));
        assertEquals(List.of("fold", "call", "raise:100.0"), game.legalActions(root));
        assertTrue(game.informationSet(root).contains(oneDeal().getFirst().getFirst().key()));

        var foldWin = root;
        for (int index = 0; index < 5; index++) foldWin = action(game, foldWin, "fold");
        assertEquals(UNCONTESTED, game.publicStatus(foldWin));
        assertEquals(0.5, game.terminalUtilities(foldWin)[BB.ordinal()], 1e-12);
        assertEquals(0, game.terminalRakeBb(foldWin));

        var allIn = action(game, root, "raise:100.0");
        allIn = action(game, allIn, "call");
        for (int index = 0; index < 4; index++) allIn = action(game, allIn, "fold");
        assertEquals(ALL_IN_SHOWDOWN, game.publicStatus(allIn));
        assertArrayEquals(
                new double[] {0.75, 0.75, 0, 0, -0.5, -1}, game.terminalUtilities(allIn), 1e-12);

        var checkdown = action(game, root, "call");
        for (int index = 0; index < 4; index++) checkdown = action(game, checkdown, "fold");
        checkdown = action(game, checkdown, "check");
        assertEquals(POSTFLOP_CONTINUATION_REQUIRED, game.publicStatus(checkdown));
        assertArrayEquals(
                new double[] {0.25, 0, 0, 0, -0.5, 0.25}, game.terminalUtilities(checkdown), 1e-12);
    }

    @Test
    void rakedCheckdownAndAllInPayoffsConserveChipsAtEveryPublicLeaf() {
        var game = game(new CashRakeRule(0.05, 1, true));
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        var checkdown = action(game, root, "call");
        for (int index = 0; index < 4; index++) checkdown = action(game, checkdown, "fold");
        checkdown = action(game, checkdown, "check");
        assertEquals(0.125, game.terminalRakeBb(checkdown), 1e-12);
        assertArrayEquals(
                new double[] {0.1875, 0, 0, 0, -0.5, 0.1875},
                game.terminalUtilities(checkdown),
                1e-12);
        var allIn = action(game, root, "raise:100.0");
        allIn = action(game, allIn, "call");
        for (int index = 0; index < 4; index++) allIn = action(game, allIn, "fold");
        assertEquals(1, game.terminalRakeBb(allIn), 1e-12);
        assertArrayEquals(
                new double[] {0.25, 0.25, 0, 0, -0.5, -1}, game.terminalUtilities(allIn), 1e-12);
        assertTrue(visitTerminals(game, root) > 100);
    }

    @Test
    void weightedChanceAndPerfectRecallInformationSetsCanBeSolved() {
        var ranges = new ArrayList<>(oneDeal());
        ranges.set(HJ.ordinal(), List.of(combo("KS", "KH", 1), combo("8S", "8H", 3)));
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        CashRakeRule.none(),
                        equalShareOracle());
        var outcomes = game.chanceOutcomes(game.initialState());
        assertEquals(2, outcomes.size());
        assertEquals(0.25, outcomes.get(0).probability(), 1e-12);
        assertEquals(0.75, outcomes.get(1).probability(), 1e-12);
        assertEquals(
                game.informationSet(outcomes.get(0).state()),
                game.informationSet(outcomes.get(1).state()));
        var firstHJ = action(game, outcomes.get(0).state(), "fold");
        var secondHJ = action(game, outcomes.get(1).state(), "fold");
        assertNotEquals(game.informationSet(firstHJ), game.informationSet(secondHJ));

        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(20);
        assertTrue(solution.strategy().size() > 6);
        var utilities = MultiPlayerStrategyEvaluator.utilities(game, solution);
        assertEquals(0, sum(utilities), 1e-7);
    }

    @Test
    void smallerOpenAndThreeWayCallReachCheckdownWithCorrectPot() {
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                        oneDeal(),
                        CashRakeRule.none(),
                        equalShareOracle());
        var state = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertEquals(List.of("fold", "call", "raise:3.0", "raise:100.0"), game.legalActions(state));
        state = action(game, state, "raise:3.0");
        state = action(game, state, "call");
        for (int index = 0; index < 3; index++) state = action(game, state, "fold");
        state = action(game, state, "call");
        assertEquals(POSTFLOP_CONTINUATION_REQUIRED, game.publicStatus(state));
        assertArrayEquals(
                new double[] {1.0 / 6, 1.0 / 6, 0, 0, -0.5, 1.0 / 6},
                game.terminalUtilities(state),
                1e-8);
    }

    @Test
    void exactBoardCheckdownKeepsFoldedHandsOutOfTheDeck() {
        var oracle = new ExactMultiwayShowdownOracle();
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        oneDeal(),
                        CashRakeRule.none(),
                        oracle);
        var state = game.chanceOutcomes(game.initialState()).getFirst().state();
        state = action(game, state, "call");
        for (int index = 0; index < 4; index++) state = action(game, state, "fold");
        state = action(game, state, "check");
        var shares =
                oracle.estimate(game.dealtHands(state), (1 << UTG.ordinal()) | (1 << BB.ordinal()))
                        .shares();
        assertEquals(
                2.5 * shares[UTG.ordinal()] - 1,
                game.terminalUtilities(state)[UTG.ordinal()],
                1e-8);
        assertEquals(
                2.5 * shares[BB.ordinal()] - 1, game.terminalUtilities(state)[BB.ordinal()], 1e-8);
        assertEquals(0, game.maximumTerminalPayoffStandardErrorBb());
    }

    @Test
    void sharedBoardSamplingBuildsTwoDealGameWithOneBoardStreamPerDeal() {
        var ranges = new ArrayList<>(oneDeal());
        ranges.set(UTG.ordinal(), List.of(combo("AS", "AH", 1), combo("5S", "5H", 1)));
        var oracle = new SharedBoardMultiwayShowdownOracle(1000, 2026);
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        CashRakeRule.none(),
                        oracle);
        assertEquals(2, game.chanceOutcomes(game.initialState()).size());
        assertEquals(2000, oracle.boardsEvaluated());
        assertTrue(game.maximumTerminalPayoffStandardErrorBb() > 0);
        var dealt = game.chanceOutcomes(game.initialState()).getFirst().state();
        assertEquals(UTG.ordinal(), game.currentPlayer(dealt));
        assertEquals(2000, oracle.boardsEvaluated());
    }

    @Test
    void rejectsBlockedRangesOversizedChanceAndInvalidShowdownShares() {
        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var blocked = new ArrayList<>(oneDeal());
        blocked.set(HJ.ordinal(), List.of(combo("AS", "KH", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopCheckdownGame(
                                rules, blocked, CashRakeRule.none(), equalShareOracle()));
        var tooWide = new ArrayList<>(oneDeal());
        tooWide.set(
                HJ.ordinal(),
                List.of(combo("2C", "2D", 1), combo("3C", "3D", 1), combo("4C", "4D", 1)));
        tooWide.set(
                CO.ordinal(),
                List.of(combo("5C", "5D", 1), combo("6C", "6D", 1), combo("7C", "7D", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopCheckdownGame(
                                rules, tooWide, CashRakeRule.none(), equalShareOracle()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopCheckdownGame(
                                rules,
                                oneDeal(),
                                CashRakeRule.none(),
                                (hands, mask) -> MultiwayShowdownEstimate.certain(new double[6])));
    }

    private static int visitTerminals(
            SixMaxPreflopCheckdownGame game, SixMaxPreflopCheckdownGame.State state) {
        if (game.isTerminal(state)) {
            assertEquals(-game.terminalRakeBb(state), sum(game.terminalUtilities(state)), 1e-8);
            double[] returned = game.terminalUtilities(state);
            returned[0] = 999;
            assertNotEquals(999, game.terminalUtilities(state)[0]);
            return 1;
        }
        int leaves = 0;
        for (String action : game.legalActions(state))
            leaves += visitTerminals(game, game.afterAction(state, action));
        return leaves;
    }

    private static double sum(double[] utilities) {
        double sum = 0;
        for (double utility : utilities) sum += utility;
        return sum;
    }
}
