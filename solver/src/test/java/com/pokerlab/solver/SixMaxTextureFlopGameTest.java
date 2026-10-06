package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxTextureFlopGameTest {
    // Synthetic all-tie oracle for chip accounting, not a generated poker equity table.
    static SixMaxPreflopSolutionPack source() {
        return source(100);
    }

    static SixMaxPreflopSolutionPack source(double stack) {
        var ranges = new ArrayList<>(SixMaxPreflopConvergenceMain.ranges("utg-mix"));
        ranges.set(
                0,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("As Ah", 1),
                        SixMaxConnectedPreflopGameTest.combo("Ac Ad", 3)));
        var spot =
                new SixMaxPreflopResearchSpot(
                        "texture-accounting-test",
                        new SixMaxPreflopBetting.Rules(stack, .5, List.of(3.0)),
                        ranges,
                        CashRakeRule.none(),
                        SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
        return SixMaxPreflopPackBuilder.build(
                spot,
                2,
                CfrSolver.Variant.CFR_PLUS,
                (hands, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & (1 << i)) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return new MultiwayShowdownEstimate(
                            shares, new double[6], SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
                },
                MultiwaySolutionPack.EXACT_ENUMERATION,
                0,
                "2026-10-06T12:00:00Z");
    }

    static SixMaxTexturePayoffTable.Artifact table(SixMaxPreflopSolutionPack source) {
        var game = source.rebuildGame();
        var deals = new ArrayList<SixMaxTexturePayoffTable.Deal>();
        for (var root : game.chanceOutcomes(game.initialState())) {
            var hands = game.dealtHands(root.state());
            var counts = SixMaxTexturePayoffTable.physicalCounts(hands);
            var pairs = new ArrayList<SixMaxTexturePayoffTable.Pair>();
            for (int mask = 0; mask < 64; mask++)
                if (Integer.bitCount(mask) == 2)
                    pairs.add(
                            new SixMaxTexturePayoffTable.Pair(
                                    mask,
                                    List.of(0L, 0L, 0L, 0L, 0L, 0L),
                                    counts.stream().map(n -> n * 666).toList()));
            deals.add(
                    new SixMaxTexturePayoffTable.Deal(
                            hands.stream().map(WeightedCombo::key).toList(), counts, pairs));
        }
        return new SixMaxTexturePayoffTable.Artifact(
                SixMaxTexturePayoffTable.SCHEMA,
                "VALIDATION_ONLY",
                SixMaxTexturePayoffTable.CLASSIFIER,
                MultiwayPackJson.fullRoundContentHash(source),
                source.spotHash(),
                deals);
    }

    static SixMaxTextureFlopGame game(SixMaxPreflopSolutionPack source) {
        return new SixMaxTextureFlopGame(
                source,
                table(source),
                List.of(
                        new SixMaxTextureFlopGame.Selection(
                                SixMaxConnectedPreflopGameTest.HISTORY, .5)));
    }

    static SixMaxTextureFlopGame.State selected(SixMaxTextureFlopGame game, int deal) {
        var state = game.chanceOutcomes(game.initialState()).get(deal).state();
        for (var action : SixMaxConnectedPreflopGameTest.HISTORY)
            state = game.afterAction(state, action.action());
        return state;
    }

    @Test
    void coversAllPhysicalFlopsWithFoldedBlockersAndNoPrivateInformationLeak() {
        var source = source();
        var game = game(source);
        assertEquals(.75, game.chanceOutcomes(game.initialState()).getFirst().probability(), 1e-15);
        var first = game.chanceOutcomes(selected(game, 0));
        var second = game.chanceOutcomes(selected(game, 1));
        assertEquals(6, first.size());
        assertEquals(1, first.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-15);
        assertNotEquals(
                first.stream().map(ChanceOutcome::probability).toList(),
                second.stream().map(ChanceOutcome::probability).toList());
        for (int t = 0; t < 6; t++) {
            var a = first.get(t).state();
            var b = second.get(t).state();
            assertEquals(game.informationSet(a), game.informationSet(b));
            assertEquals(Seat.BB.ordinal(), game.currentPlayer(a));
            assertTrue(game.informationSet(a).contains("9h 9s"));
            assertFalse(game.informationSet(a).contains("Ah As"));
            assertFalse(game.informationSet(a).contains("Jh Js"));
            assertEquals(
                    -.5, game.inactivePlayerUtility(a, Seat.SB.ordinal()).orElseThrow(), 1e-15);
        }
        assertNotEquals(
                game.informationSet(first.getFirst().state()),
                game.informationSet(first.getLast().state()));
        assertEquals(9880, game.coverage().getFirst().physicalFlopsPerDeal());
    }

    @Test
    void everyTerminalRefundsUncalledBetsAndPreservesFoldedLosses() {
        var game = game(source());
        var root = game.chanceOutcomes(selected(game, 0)).getFirst().state();
        for (String path : List.of("kk", "bf", "bc", "kbf", "kbc")) {
            var state = root;
            for (char action : path.toCharArray())
                state = game.afterAction(state, String.valueOf(action));
            var utilities = game.terminalUtilities(state);
            assertEquals(0, Arrays.stream(utilities).sum(), 1e-12);
            assertEquals(-.5, utilities[Seat.SB.ordinal()], 1e-12);
            assertEquals(
                    path.equals("bf") ? 3.5 : path.equals("kbf") ? -3 : .25,
                    utilities[Seat.BB.ordinal()],
                    1e-12);
            assertEquals(
                    path.equals("kbf") ? 3.5 : path.equals("bf") ? -3 : .25,
                    utilities[Seat.BTN.ordinal()],
                    1e-12);
            utilities[0] = 100;
            assertEquals(0, game.terminalUtilities(state)[0], 1e-12);
            assertThrows(IllegalArgumentException.class, () -> game.afterAction(root, "raise"));
        }
    }

    @Test
    void liftedCheckdownRecoversSourceAndWholeTreeAccountingExactly() {
        var source = source();
        var game = game(source);
        var lifted = game.checkdownBaseline(source.solution());
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(source.rebuildGame(), source.solution()),
                MultiPlayerStrategyEvaluator.utilities(game, lifted),
                1e-10);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, lifted, SixMaxTextureFlopGame.MAX_COMPLETE_STATES);
        assertEquals(0, complete.addedInformationSets());
        assertEquals(game.completeTreeStates(), complete.visitedStates());
        assertEquals(
                1 + 2L * source.rebuildGame().treeSummary().totalStates() + 2 * 6 * 9,
                game.completeTreeStates());
        assertThrows(
                IllegalArgumentException.class,
                () -> game.checkdownBaseline(new CfrSolution(1, Map.of())));
    }

    @Test
    void rejectsInvalidMenusAndForgedPublicStates() {
        var source = source();
        var table = table(source);
        var selection =
                new SixMaxTextureFlopGame.Selection(SixMaxConnectedPreflopGameTest.HISTORY, .5);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxTextureFlopGame(source, table, List.of(selection, selection)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxTextureFlopGame(
                                source,
                                table,
                                List.of(new SixMaxTextureFlopGame.Selection(List.of(), .5))));
        for (double size : List.of(0.0, -1.0, 2.1, Double.NaN, Double.POSITIVE_INFINITY))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxTextureFlopGame.Selection(List.of(), size));
        var game = game(source);
        var state = selected(game, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxTextureFlopGame.State(state.preflop(), 6, "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxTextureFlopGame.State(state.preflop(), 0, "bb")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.isTerminal(new SixMaxTextureFlopGame.State(state.preflop(), null, "k")));
    }

    @Test
    void unselectedMultiwayAndUncontestedPathsRetainSourcePayoffs() {
        var game = game(source());
        for (boolean call : List.of(false, true)) {
            var state = game.chanceOutcomes(game.initialState()).getFirst().state();
            while (!game.isTerminal(state)) {
                var legal = game.legalActions(state);
                state =
                        game.afterAction(
                                state,
                                call && legal.contains("call")
                                        ? "call"
                                        : legal.contains("check") ? "check" : "fold");
            }
            assertNull(state.texture());
            assertArrayEquals(
                    game.sourceGame().terminalUtilities(state.preflop()),
                    game.terminalUtilities(state));
        }
    }

    @Test
    void flopBetIsCappedAtBothRemainingStacks() {
        var game = game(source(4));
        assertEquals(1, game.coverage().getFirst().betBb());
        var state = game.chanceOutcomes(selected(game, 0)).getFirst().state();
        state = game.afterAction(game.afterAction(state, "b"), "c");
        assertEquals(0, Arrays.stream(game.terminalUtilities(state)).sum(), 1e-12);
    }
}
