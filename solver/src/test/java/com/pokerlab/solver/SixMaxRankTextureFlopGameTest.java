package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SixMaxRankTextureFlopGameTest {
    // Synthetic all-tie oracle for chip/information accounting; never generated poker evidence.
    static SixMaxRankTexturePayoffTable.Artifact table(SixMaxPreflopSolutionPack source) {
        var base = source.rebuildGame();
        var roots = base.chanceOutcomes(base.initialState());
        var palette = new TreeSet<SixMaxRankTexturePayoffTable.Signal>();
        for (var root : roots)
            palette.addAll(
                    SixMaxRankTexturePayoffTable.physicalCounts(base.dealtHands(root.state()))
                            .keySet());
        var signals = List.copyOf(palette);
        var deals = new ArrayList<SixMaxRankTexturePayoffTable.Deal>();
        for (var root : roots) {
            var hands = base.dealtHands(root.state());
            var physical = SixMaxRankTexturePayoffTable.physicalCounts(hands);
            var counts = signals.stream().map(s -> physical.getOrDefault(s, 0L)).toList();
            var pairs = new ArrayList<SixMaxRankTexturePayoffTable.Pair>();
            for (int mask = 0; mask < 64; mask++)
                if (Integer.bitCount(mask) == 2)
                    pairs.add(
                            new SixMaxRankTexturePayoffTable.Pair(
                                    mask,
                                    signals.stream().map(s -> 0L).toList(),
                                    counts.stream().map(c -> c * 666).toList()));
            deals.add(
                    new SixMaxRankTexturePayoffTable.Deal(
                            hands.stream().map(WeightedCombo::key).toList(), counts, pairs));
        }
        return new SixMaxRankTexturePayoffTable.Artifact(
                SixMaxRankTexturePayoffTable.SCHEMA,
                "VALIDATION_ONLY",
                SixMaxRankTexturePayoffTable.CLASSIFIER,
                MultiwayPackJson.fullRoundContentHash(source),
                source.spotHash(),
                signals,
                deals);
    }

    static List<SixMaxRankTextureFlopGame.Selection> menu() {
        return List.of(
                new SixMaxRankTextureFlopGame.Selection(
                        SixMaxConnectedPreflopGameTest.HISTORY, .5));
    }

    static SixMaxRankTextureFlopGame.State history(SixMaxRankTextureFlopGame game, int deal) {
        var state = game.chanceOutcomes(game.initialState()).get(deal).state();
        for (var action : SixMaxConnectedPreflopGameTest.HISTORY)
            state = game.afterAction(state, action.action());
        return state;
    }

    @Test
    void fullPhysicalPartitionCompleteSupportAndSourceCheckdownRecovery() {
        var source = SixMaxTextureFlopGameTest.source();
        var table = table(source);
        var game = new SixMaxRankTextureFlopGame(source, table, menu());
        for (int deal = 0; deal < table.deals().size(); deal++) {
            var outcomes = game.chanceOutcomes(history(game, deal));
            assertEquals(1, outcomes.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
            assertEquals(
                    9880,
                    table.deals().get(deal).flopCounts().stream().mapToLong(Long::longValue).sum());
            assertTrue(outcomes.size() > 1000);
        }
        var lifted = game.checkdownBaseline(source.solution());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(game, lifted, 1000000)
                        .addedInformationSets());
        assertEquals(
                game.completeTreeStates(),
                MultiPlayerStrategyCompletion.uniformAtUnseen(game, lifted, 1000000)
                        .visitedStates());
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution()),
                MultiPlayerStrategyEvaluator.utilities(game, lifted),
                1e-9);
        assertTrue(game.completeTreeStates() > 20000);
        long predicted =
                1 + (long) game.sourceGame().treeSummary().totalStates() * table.deals().size();
        for (var deal : table.deals())
            predicted += 9 * deal.flopCounts().stream().filter(n -> n > 0).count();
        assertEquals(predicted, game.completeTreeStates());
    }

    @Test
    void publicKeysRevealRanksWhileConcealingFoldedDealsAndActualSuits() {
        var source = SixMaxTextureFlopGameTest.source();
        var table = table(source);
        var game = new SixMaxRankTextureFlopGame(source, table, menu());
        var low =
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("2c"), Card.parse("3d"), Card.parse("4h"));
        var high =
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("6c"), Card.parse("7d"), Card.parse("8h"));
        var first =
                new SixMaxRankTextureFlopGame.State(
                        history(game, 0).preflop(), table.signals().indexOf(low), "");
        var otherWorld =
                new SixMaxRankTextureFlopGame.State(
                        history(game, 1).preflop(), table.signals().indexOf(low), "");
        var differentRanks =
                new SixMaxRankTextureFlopGame.State(
                        first.preflop(), table.signals().indexOf(high), "");
        assertEquals(game.informationSet(first), game.informationSet(otherWorld));
        assertNotEquals(game.informationSet(first), game.informationSet(differentRanks));
        assertTrue(game.informationSet(first).contains(":" + low.key() + ":"));
        assertFalse(game.informationSet(first).contains("Ah As"));
        assertEquals(List.of("k", "b"), game.legalActions(first));
        assertEquals(List.of("k", "b"), game.legalActions(game.afterAction(first, "k")));
        assertEquals(List.of("f", "c"), game.legalActions(game.afterAction(first, "b")));
    }

    @Test
    void chipsAndFoldedProfitHoldForEveryFlopSettlement() {
        var source = SixMaxTextureFlopGameTest.source();
        var game = new SixMaxRankTextureFlopGame(source, table(source), menu());
        var state = game.chanceOutcomes(history(game, 0)).getFirst().state();
        for (String actions : List.of("kk", "bf", "bc", "kbf", "kbc")) {
            var terminal = state;
            for (char action : actions.toCharArray())
                terminal = game.afterAction(terminal, String.valueOf(action));
            var utilities = game.terminalUtilities(terminal);
            assertEquals(0, java.util.Arrays.stream(utilities).sum(), 1e-12);
            assertEquals(-.5, utilities[PreflopAllInSpot.Seat.SB.ordinal()], 0);
            assertEquals(0, utilities[PreflopAllInSpot.Seat.UTG.ordinal()], 0);
            if (actions.endsWith("f")) {
                int winner =
                        (actions.equals("bf")
                                        ? PreflopAllInSpot.Seat.BB
                                        : PreflopAllInSpot.Seat.BTN)
                                .ordinal();
                assertEquals(3.5, utilities[winner], 1e-12);
                assertEquals(-3, utilities[winner == 5 ? 3 : 5], 1e-12);
            } else {
                assertEquals(.25, utilities[3], 1e-12);
                assertEquals(.25, utilities[5], 1e-12);
            }
        }
        assertEquals(-.5, game.inactivePlayerUtility(state, 4).orElseThrow(), 0);
        assertTrue(game.inactivePlayerUtility(state, 5).isEmpty());
    }

    @Test
    void invalidHistoriesSizingSignalsAndPartialPoliciesFail() {
        var source = SixMaxTextureFlopGameTest.source();
        var table = table(source);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxRankTextureFlopGame(source, table, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRankTextureFlopGame(
                                source, table, List.of(menu().getFirst(), menu().getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxRankTextureFlopGame.Selection(List.of(), Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRankTextureFlopGame(
                                source,
                                table,
                                List.of(new SixMaxRankTextureFlopGame.Selection(List.of(), .5))));
        var game = new SixMaxRankTextureFlopGame(source, table, menu());
        var state = history(game, 0);
        for (int invalid : List.of(-1, table.signals().size()))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            game.isTerminal(
                                    new SixMaxRankTextureFlopGame.State(
                                            state.preflop(), invalid, "")));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.checkdownBaseline(new CfrSolution(1, java.util.Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.afterAction(game.chanceOutcomes(state).getFirst().state(), "raise"));
    }
}
