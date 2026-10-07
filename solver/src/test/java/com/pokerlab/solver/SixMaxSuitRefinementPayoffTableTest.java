package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuitRefinementPayoffTableTest {
    static List<Card> cards(String text) {
        return java.util.Arrays.stream(text.split(" ")).map(Card::parse).toList();
    }

    // Synthetic all-tie refinement for accounting tests only, never generated poker evidence.
    static SixMaxSuitRefinementPayoffTable.Artifact table(
            SixMaxPreflopSolutionPack source, SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        var refined =
                List.of(
                        SixMaxRankTexturePayoffTable.Signal.from(
                                Card.parse("2s"), Card.parse("3s"), Card.parse("As")));
        var game = source.rebuildGame();
        var hands =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(r -> game.dealtHands(r.state()))
                        .toList();
        var palette = new TreeSet<SixMaxSuitRefinementPayoffTable.Observation>();
        for (var world : hands)
            palette.addAll(SixMaxSuitRefinementPayoffTable.physicalCounts(world, refined).keySet());
        var observations = List.copyOf(palette);
        var deals = new ArrayList<SixMaxSuitRefinementPayoffTable.Deal>();
        for (var world : hands) {
            var physical = SixMaxSuitRefinementPayoffTable.physicalCounts(world, refined);
            var counts = observations.stream().map(o -> physical.getOrDefault(o, 0L)).toList();
            var pairs = new ArrayList<SixMaxSuitRefinementPayoffTable.Pair>();
            for (int mask = 0; mask < 64; mask++)
                if (Integer.bitCount(mask) == 2)
                    pairs.add(
                            new SixMaxSuitRefinementPayoffTable.Pair(
                                    mask,
                                    counts.stream().map(n -> 0L).toList(),
                                    counts.stream().map(n -> n * 666).toList()));
            deals.add(
                    new SixMaxSuitRefinementPayoffTable.Deal(
                            world.stream().map(WeightedCombo::key).toList(), counts, pairs));
        }
        return new SixMaxSuitRefinementPayoffTable.Artifact(
                SixMaxSuitRefinementPayoffTable.SCHEMA,
                "VALIDATION_ONLY",
                SixMaxSuitRefinementPayoffTable.CLASSIFIER,
                MultiwayPackJson.fullRoundContentHash(source),
                source.spotHash(),
                SixMaxRankTexturePayoffTable.hash(parent),
                refined,
                observations,
                deals);
    }

    @Test
    void fixedFlopMatchesIndependentObjectEvaluatorForAllFifteenPairs() {
        var source = SixMaxTextureFlopGameTest.source();
        var game = source.rebuildGame();
        var hands = game.dealtHands(game.chanceOutcomes(game.initialState()).getFirst().state());
        var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
        var board = deck.subList(0, 3);
        var remaining = deck.subList(3, 11);
        var actual = SixMaxConditionalPayoffEnumeration.fixedFlop(hands, board, remaining);
        assertEquals(List.of(1L), actual.flopCounts());
        for (var pair : actual.pairs()) {
            int first = Integer.numberOfTrailingZeros(pair.activeMask());
            int second = Integer.numberOfTrailingZeros(pair.activeMask() ^ (1 << first));
            long wins = 0, ties = 0;
            for (int a = 0; a < remaining.size() - 1; a++)
                for (int b = a + 1; b < remaining.size(); b++) {
                    var one = new ArrayList<>(board);
                    one.add(remaining.get(a));
                    one.add(remaining.get(b));
                    var two = new ArrayList<>(one);
                    one.add(hands.get(first).first());
                    one.add(hands.get(first).second());
                    two.add(hands.get(second).first());
                    two.add(hands.get(second).second());
                    int comparison =
                            HandEvaluator.evaluateBest(one)
                                    .compareTo(HandEvaluator.evaluateBest(two));
                    if (comparison > 0) wins++;
                    if (comparison == 0) ties++;
                }
            assertEquals(wins, pair.firstWins().getFirst());
            assertEquals(ties, pair.ties().getFirst());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPayoffEnumeration.fixedFlop(
                                hands,
                                List.of(board.get(0), board.get(0), board.get(1)),
                                remaining));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPayoffEnumeration.fixedFlop(
                                hands,
                                List.of(hands.getFirst().first(), board.get(1), board.get(2)),
                                remaining));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPayoffEnumeration.fixedFlop(
                                hands, board, List.of(remaining.get(0), remaining.get(0))));
    }

    @Test
    void publicRefinementDistinguishesSuitsOnlyInDeclaredGroups() {
        var refined =
                List.of(
                        SixMaxRankTexturePayoffTable.Signal.from(
                                Card.parse("2s"), Card.parse("3s"), Card.parse("4s")));
        var spades = SixMaxSuitRefinementPayoffTable.observe(cards("4s 2s 3s"), refined);
        var diamonds = SixMaxSuitRefinementPayoffTable.observe(cards("2d 3d 4d"), refined);
        assertTrue(spades.physical());
        assertNotEquals(spades, diamonds);
        assertEquals("board:2s3s4s", spades.key());
        assertEquals(spades, SixMaxSuitRefinementPayoffTable.observe(cards("3s 4s 2s"), refined));
        assertEquals(
                SixMaxSuitRefinementPayoffTable.observe(cards("5c 6c 7c"), refined),
                SixMaxSuitRefinementPayoffTable.observe(cards("5h 6h 7h"), refined));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSuitRefinementPayoffTable.Observation(
                                spades.signal(), List.of("4s", "2s", "3s")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitRefinementPayoffTable.observe(cards("2s 2s 3s"), refined));
    }

    @Test
    void physicalBlockingAndExactRegroupingAreRequiredBeforeIo(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var table = table(source, parent);
        SixMaxSuitRefinementPayoffTable.validate(table, source, parent);
        for (var deal : table.deals())
            assertEquals(9880L, deal.flopCounts().stream().mapToLong(Long::longValue).sum());
        var blocked =
                SixMaxSuitRefinementPayoffTable.observe(cards("2s 3s As"), table.refinedSignals());
        int index = table.observations().indexOf(blocked);
        assertTrue(index >= 0);
        assertEquals(1L, table.deals().get(0).flopCounts().get(index));
        assertEquals(0L, table.deals().get(1).flopCounts().get(index));
        var path = temp.resolve("table.json.gz");
        SixMaxSuitRefinementPayoffTable.write(path, table, source, parent);
        var bytes = Files.readAllBytes(path);
        assertEquals(table, SixMaxSuitRefinementPayoffTable.read(path, source, parent));
        SixMaxSuitRefinementPayoffTable.write(path, table, source, parent);
        assertArrayEquals(bytes, Files.readAllBytes(path));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().readTree(SixMaxTextureStudy.json(table));
        ((com.fasterxml.jackson.databind.node.ArrayNode) tree.at("/deals/0/pairs/0/ties"))
                .set(index, com.fasterxml.jackson.databind.node.LongNode.valueOf(665));
        Files.writeString(path, "sentinel");
        var tampered =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(tree, SixMaxSuitRefinementPayoffTable.Artifact.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitRefinementPayoffTable.write(path, tampered, source, parent));
        assertEquals("sentinel", Files.readString(path));
        tree.put("schemaVersion", SixMaxRankTexturePayoffTable.SCHEMA);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(tree, SixMaxSuitRefinementPayoffTable.Artifact.class));
    }
}
