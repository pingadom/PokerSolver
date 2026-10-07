package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxRankTexturePayoffTableTest {
    @Test
    void signalsExposeActualRanksButKeepSuitIdentityAndAssociationHidden() {
        var low = SixMaxTexturePayoffTableTest.cards("2c 3d 4h");
        var signal = SixMaxRankTexturePayoffTable.Signal.from(low.get(0), low.get(1), low.get(2));
        assertEquals(2, signal.lowRank());
        assertEquals(3, signal.middleRank());
        assertEquals(4, signal.highRank());
        assertEquals(
                signal,
                SixMaxRankTexturePayoffTable.Signal.from(low.get(2), low.get(0), low.get(1)));
        assertEquals(
                signal,
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("2s"), Card.parse("3c"), Card.parse("4d")));
        assertNotEquals(
                signal,
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("Tc"), Card.parse("Jd"), Card.parse("Qh")));
        assertEquals(
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("2c"), Card.parse("3c"), Card.parse("4d")),
                SixMaxRankTexturePayoffTable.Signal.from(
                        Card.parse("2d"), Card.parse("3c"), Card.parse("4c")));
        for (var invalid :
                List.of(
                        new int[] {1, 3, 4},
                        new int[] {2, 4, 3},
                        new int[] {2, 3, 15},
                        new int[] {2, 2, 3}))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxRankTexturePayoffTable.Signal(
                                    invalid[0],
                                    invalid[1],
                                    invalid[2],
                                    SixMaxTexturePayoffTable.Texture.DISTINCT_RAINBOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRankTexturePayoffTable.Signal.from(
                                Card.parse("Ac"), Card.parse("Ac"), Card.parse("Kd")));
    }

    @Test
    void rankPartitionMatchesIndependentFlopFirstEnumerationAndRecoversEveryCoarseEntry() {
        var hands =
                SixMaxPreflopConvergenceMain.ranges("button-mix").stream()
                        .map(List::getFirst)
                        .toList();
        var deck = SixMaxTexturePayoffTableTest.cards("2c 2d 2h 3c 3d 4c 5d 6h");
        var palette =
                List.copyOf(SixMaxRankTexturePayoffTable.physicalCountsForDeck(deck).keySet());
        var exact = SixMaxRankTexturePayoffTable.enumerate(hands, deck, palette);
        long[] counts = new long[palette.size()];
        long[][] wins = new long[64][palette.size()], ties = new long[64][palette.size()];
        for (int a = 0; a < deck.size(); a++)
            for (int b = a + 1; b < deck.size(); b++)
                for (int c = b + 1; c < deck.size(); c++) {
                    var flop = List.of(deck.get(a), deck.get(b), deck.get(c));
                    int signal =
                            palette.indexOf(
                                    SixMaxRankTexturePayoffTable.Signal.from(
                                            flop.get(0), flop.get(1), flop.get(2)));
                    counts[signal]++;
                    var remaining = new ArrayList<>(deck);
                    remaining.removeAll(flop);
                    for (int turn = 0; turn < remaining.size(); turn++)
                        for (int river = turn + 1; river < remaining.size(); river++) {
                            var scored = new ArrayList<com.pokerlab.core.hand.EvaluatedHand>();
                            for (var hand : hands)
                                scored.add(
                                        HandEvaluator.evaluateBest(
                                                hand.first(),
                                                hand.second(),
                                                flop.get(0),
                                                flop.get(1),
                                                flop.get(2),
                                                remaining.get(turn),
                                                remaining.get(river)));
                            for (int first = 0; first < 6; first++)
                                for (int second = first + 1; second < 6; second++) {
                                    int mask = (1 << first) | (1 << second);
                                    int comparison =
                                            scored.get(first).compareTo(scored.get(second));
                                    if (comparison > 0) wins[mask][signal]++;
                                    else if (comparison == 0) ties[mask][signal]++;
                                }
                        }
                }
        assertEquals(Arrays.stream(counts).boxed().toList(), exact.flopCounts());
        var coarse = SixMaxTexturePayoffTable.enumerate(hands, deck);
        for (var pair : exact.pairs()) {
            assertEquals(Arrays.stream(wins[pair.activeMask()]).boxed().toList(), pair.firstWins());
            assertEquals(Arrays.stream(ties[pair.activeMask()]).boxed().toList(), pair.ties());
            long[] coarseWins = new long[6], coarseTies = new long[6];
            for (int signal = 0; signal < palette.size(); signal++) {
                int texture = palette.get(signal).texture().ordinal();
                coarseWins[texture] += pair.firstWins().get(signal);
                coarseTies[texture] += pair.ties().get(signal);
                int first = Integer.numberOfTrailingZeros(pair.activeMask()),
                        second = Integer.numberOfTrailingZeros(pair.activeMask() ^ (1 << first));
                assertEquals(
                        1,
                        pair.share(first, signal, counts[signal] * 10)
                                + pair.share(second, signal, counts[signal] * 10),
                        1e-15);
            }
            assertEquals(
                    Arrays.stream(coarseWins).boxed().toList(),
                    coarse.pair(pair.activeMask()).firstWins());
            assertEquals(
                    Arrays.stream(coarseTies).boxed().toList(),
                    coarse.pair(pair.activeMask()).ties());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRankTexturePayoffTable.enumerate(
                                hands, deck, palette.subList(1, palette.size())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPayoffEnumeration.enumerate(
                                hands, deck, 6, (a, b, c) -> -1));
    }

    @Test
    void strictSourceBindingPaletteAndBoundedGzipRejectCorruptInputs(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxRankTextureFlopGameTest.table(source);
        var path = temp.resolve("table.json.gz");
        var bytes =
                SixMaxRankTexturePayoffTable.json(table)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SixMaxRankTexturePayoffTable.writeBytes(path, bytes, 4 * 1024 * 1024);
        assertEquals(table, SixMaxRankTexturePayoffTable.read(path, source));
        var compressed = Files.readAllBytes(path);
        SixMaxRankTexturePayoffTable.writeBytes(path, bytes, 4 * 1024 * 1024);
        assertArrayEquals(compressed, Files.readAllBytes(path));
        // Gzip headers can exceed a small raw cap; never export an artifact its reader rejects.
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.writeBytes(path, new byte[16], 16));
        assertArrayEquals(compressed, Files.readAllBytes(path));
        var reordered = new ArrayList<>(table.deals());
        java.util.Collections.reverse(reordered);
        var changed =
                new SixMaxRankTexturePayoffTable.Artifact(
                        table.schemaVersion(),
                        table.publicationStatus(),
                        table.classifier(),
                        table.sourcePackHash(),
                        table.sourceSpotHash(),
                        table.signals(),
                        reordered);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.validate(changed, source));
        var plain = temp.resolve("table.json");
        String json = SixMaxRankTexturePayoffTable.json(table);
        for (String bad :
                List.of(
                        json + "{}",
                        json.replaceFirst("\"lowRank\" : 2", "\"lowRank\" : 2.5"),
                        json.replaceFirst("\"lowRank\" : 2", "\"lowRank\" : \"2\""),
                        json.replaceFirst("\"lowRank\" : 2", "\"lowRank\" : null"),
                        json.replaceFirst("\"lowRank\" : 2", "\"lowRank\" : 2, \"lowRank\" : 2"))) {
            assertNotEquals(json, bad);
            Files.writeString(plain, bad);
            assertThrows(Exception.class, () -> SixMaxRankTexturePayoffTable.read(plain, source));
        }
        var tree = SixMaxTexturePayoffTable.mapper().readTree(json);
        var counts =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        tree.get("deals").get(0).get("flopCounts");
        counts.set(
                0,
                SixMaxTexturePayoffTable.mapper()
                        .getNodeFactory()
                        .numberNode(counts.get(0).asLong() + 1));
        Files.writeString(plain, tree.toString());
        assertThrows(Exception.class, () -> SixMaxRankTexturePayoffTable.read(plain, source));
        Files.write(path, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> SixMaxRankTexturePayoffTable.read(path, source));
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(path))) {
            zip.write(new byte[4 * 1024 * 1024 + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.read(path, source));
        var alias = temp.resolve("alias.json");
        Files.createLink(alias, plain);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRankTexturePayoffTableMain.main(
                                new String[] {plain.toString(), alias.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTableMain.main(new String[0]));
    }
}
