package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxHistoryPhysicalPayoffTableTest {
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact parent;
    private static SixMaxHistoryPhysicalPayoffTable.Menu menu;
    private static SixMaxHistoryPhysicalPayoffTable.Verified table;

    @BeforeAll
    static void fixture() throws Exception {
        source = SixMaxTextureFlopGameTest.source();
        parent = SixMaxRankTextureFlopGameTest.table(source);
        var actions =
                List.of(
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.UTG, "raise:3.0"),
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.HJ, "fold"),
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.CO, "fold"),
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.BTN, "fold"),
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.SB, "fold"),
                        new SixMaxPreflopResearchTrainer.PublicAction(
                                PreflopAllInSpot.Seat.BB, "call"));
        menu =
                new SixMaxHistoryPhysicalPayoffTable.Menu(
                        List.of(
                                SixMaxRankTextureFlopGameTest.menu().getFirst(),
                                new SixMaxRankTextureFlopGame.Selection(actions, .5)),
                        List.of(
                                new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                        0, List.of("2s", "3s", "As"))));
        table = synthetic(menu);
    }

    static SixMaxHistoryPhysicalPayoffTable.Verified synthetic(
            SixMaxHistoryPhysicalPayoffTable.Menu m) throws Exception {
        return SixMaxHistoryPhysicalPayoffTable.generate(
                source,
                parent,
                m,
                n -> {},
                (hands, board, remaining, mask) ->
                        new SixMaxConditionalPayoffEnumeration.Pair(
                                mask, List.of(0L), List.of(666L)));
    }

    @Test
    void selectedHistoryRevealsPublicCardsAndOtherHistoriesKeepRankFallback() {
        var cards = SixMaxSuitRefinementPayoffTableTest.cards("As 3s 2s");
        var physical = SixMaxHistoryPhysicalPayoffTable.observe(menu, 0, cards);
        var fallback = SixMaxHistoryPhysicalPayoffTable.observe(menu, 1, cards);
        assertTrue(physical.physical());
        assertFalse(fallback.physical());
        assertEquals(physical.signal(), fallback.signal());
        assertEquals("board:2s3sAs", physical.key());
        assertEquals(
                physical,
                SixMaxHistoryPhysicalPayoffTable.observe(
                        menu, 0, SixMaxSuitRefinementPayoffTableTest.cards("2s As 3s")));
        assertFalse(
                SixMaxHistoryPhysicalPayoffTable.observe(
                                menu, 0, SixMaxSuitRefinementPayoffTableTest.cards("2h 3h Ah"))
                        .physical());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalPayoffTable.observe(menu, 2, cards));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalPayoffTable.observe(
                                menu, 0, SixMaxSuitRefinementPayoffTableTest.cards("2s 2s 3s")));
    }

    @Test
    void foldedCardsStillBlockRevelationsAndComplementsExactlyRecoverParent() throws Exception {
        var t = table.artifact();
        var o =
                SixMaxHistoryPhysicalPayoffTable.observe(
                        menu, 0, SixMaxSuitRefinementPayoffTableTest.cards("2s 3s As"));
        int physical = t.observations().indexOf(o);
        int rank =
                t.observations()
                        .indexOf(
                                new SixMaxSuitRefinementPayoffTable.Observation(
                                        o.signal(), List.of()));
        int parentRank = parent.signals().indexOf(o.signal());
        // UTG folds in history zero, but its As still removes this flop in the second world.
        assertEquals(1L, t.histories().get(0).deals().get(0).flopCounts().get(physical));
        assertEquals(0L, t.histories().get(0).deals().get(1).flopCounts().get(physical));
        for (int h = 0; h < 2; h++)
            for (int d = 0; d < 2; d++) {
                var row = t.histories().get(h).deals().get(d);
                long expected = h == 0 && d == 0 ? 1 : 0;
                assertEquals(
                        parent.deals().get(d).flopCounts().get(parentRank) - expected,
                        row.flopCounts().get(rank));
                assertEquals(9880L, row.flopCounts().stream().mapToLong(Long::longValue).sum());
            }
        SixMaxHistoryPhysicalPayoffTable.validate(t, source, parent);
        var view = SixMaxHistoryPhysicalPayoffTable.view(table);
        assertThrows(IllegalArgumentException.class, () -> view.counts(0));
        assertThrows(IllegalArgumentException.class, () -> view.counts("unknown", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> view.share(t.histories().get(0).publicHistory(), 0, 3, 0, physical));
    }

    @Test
    void completeOffPolicySupportAndCheckdownUtilitiesSurviveHistoryDependentChance()
            throws Exception {
        var game = new SixMaxHistoryPhysicalFlopGame(source, parent, table);
        var baseline = game.checkdownBaseline(source.solution());
        var complete = MultiPlayerStrategyCompletion.uniformAtUnseen(game, baseline, 1000000);
        assertEquals(0, complete.addedInformationSets());
        assertEquals(game.completeTreeStates(), complete.visitedStates());
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution()),
                MultiPlayerStrategyEvaluator.utilities(game, baseline),
                1e-9);
        for (var root : game.chanceOutcomes(game.initialState()))
            for (var s : menu.selections()) {
                var state = root.state();
                for (var a : s.history()) state = game.afterAction(state, a.action());
                var chance = game.chanceOutcomes(state);
                assertEquals(
                        1, chance.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
                for (var c : chance) {
                    var end = game.afterAction(game.afterAction(c.state(), "k"), "k");
                    assertEquals(0, Arrays.stream(game.terminalUtilities(end)).sum(), 1e-12);
                }
            }
    }

    @Test
    void allFifteenActivePairKernelsEqualLegacyEnumerationAndRejectFoldedCardRunouts() {
        var game = source.rebuildGame();
        var hands = game.dealtHands(game.chanceOutcomes(game.initialState()).getFirst().state());
        var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
        var board = deck.subList(0, 3);
        var remaining = deck.subList(3, 11);
        var expected = SixMaxConditionalPayoffEnumeration.fixedFlop(hands, board, remaining);
        for (var pair : expected.pairs())
            assertEquals(
                    pair,
                    SixMaxConditionalPayoffEnumeration.fixedFlopPair(
                            hands, board, remaining, pair.activeMask()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalPayoffEnumeration.fixedFlopPair(hands, board, remaining, 7));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPayoffEnumeration.fixedFlopPair(
                                hands, board, List.of(hands.get(0).first(), remaining.get(0)), 40));
    }

    @Test
    void invalidMenusFailBeforeAnyExactPayoffWork() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHistoryPhysicalPayoffTable.Menu(
                                menu.selections(),
                                List.of(
                                        menu.revelations().getFirst(),
                                        menu.revelations().getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalPayoffTable.Reveal(0, List.of("As", "3s", "2s")));
        var blocked =
                new SixMaxHistoryPhysicalPayoffTable.Menu(
                        menu.selections(),
                        List.of(
                                new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                        0, List.of("2s", "3s", "Ks"))));
        int[] called = {0};
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalPayoffTable.generate(
                                source,
                                parent,
                                blocked,
                                n -> {},
                                (hands, board, remaining, mask) -> {
                                    called[0]++;
                                    return null;
                                }));
        assertEquals(0, called[0]);
    }

    @Test
    void strictBoundedMenuIoAndCliProtectInputs(@TempDir Path temp) throws Exception {
        var path = temp.resolve("menu.json");
        SixMaxHistoryPhysicalPayoffTable.writeMenu(path, menu);
        assertEquals(menu, SixMaxHistoryPhysicalPayoffTable.readMenu(path));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalPayoffTableMain.main(
                                new String[] {
                                    "generate",
                                    path.toString(),
                                    path.toString(),
                                    path.toString(),
                                    path.toString()
                                }));
        assertEquals(menu, SixMaxHistoryPhysicalPayoffTable.readMenu(path));
        java.nio.file.Files.writeString(path, "{\"unknown\":true}");
        assertThrows(Exception.class, () -> SixMaxHistoryPhysicalPayoffTable.readMenu(path));
        java.nio.file.Files.write(
                path, new byte[SixMaxHistoryPhysicalPayoffTable.MAX_MENU_BYTES + 1]);
        assertThrows(Exception.class, () -> SixMaxHistoryPhysicalPayoffTable.readMenu(path));
    }

    @Test
    void freshJointSolveReplaysAndRejectsOldNamespaceOrChangedMenu(@TempDir Path temp)
            throws Exception {
        var result =
                SixMaxHistoryPhysicalStudy.solve(
                        source,
                        parent,
                        table,
                        2,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        assertEquals(2, result.checkpoint().solution().iterations());
        assertEquals(0, result.traversal().sampledChanceNodes());
        assertFalse(result.report().trainerAdmission());
        assertTrue(result.inactiveUtilityPrunedNodes() > 0);
        var path = temp.resolve("checkpoint.json.gz");
        SixMaxHistoryPhysicalStudy.write(path, result.checkpoint(), source, parent, table);
        assertEquals(
                result.checkpoint(), SixMaxHistoryPhysicalStudy.read(path, source, parent, table));
        var report = temp.resolve("report.json.gz");
        SixMaxHistoryPhysicalStudy.writeReport(report, result.report());
        assertEquals(
                result.report(),
                SixMaxHistoryPhysicalStudy.replay(
                        report, source, parent, table, result.checkpoint()));
        var traversal = temp.resolve("traversal.json");
        SixMaxHistoryPhysicalStudy.writeTrainingEvidence(traversal, result);
        assertEquals(
                SixMaxHistoryPhysicalStudy.trainingEvidence(result),
                SixMaxHistoryPhysicalStudy.replayTrainingEvidence(
                        traversal, source, parent, table, result.checkpoint()));
        var altered =
                SixMaxTexturePayoffTable.mapper()
                        .valueToTree(SixMaxHistoryPhysicalStudy.trainingEvidence(result));
        ((com.fasterxml.jackson.databind.node.ObjectNode) altered.get("traversal"))
                .put("visitedNodes", result.traversal().visitedNodes() + 1);
        java.nio.file.Files.writeString(traversal, altered.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalStudy.replayTrainingEvidence(
                                traversal, source, parent, table, result.checkpoint()));
        var changed =
                synthetic(
                        new SixMaxHistoryPhysicalPayoffTable.Menu(
                                menu.selections(),
                                List.of(
                                        new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                                1, List.of("2s", "3s", "As")))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalStudy.rebuild(
                                source, parent, changed, result.checkpoint()));
        var oldGame = new SixMaxRankTextureFlopGame(source, parent, menu.selections());
        var old =
                SixMaxHistoryPhysicalStudy.checkpoint(
                        table,
                        oldGame.checkdownBaseline(source.solution()),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalStudy.rebuild(source, parent, table, old));
    }
}
