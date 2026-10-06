package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxTexturePayoffTableTest {
    static List<Card> cards(String text) {
        return Arrays.stream(text.split(" ")).map(Card::parse).toList();
    }

    @Test
    void sixClassesAreExhaustiveAndInvariantToCardOrder() {
        var examples =
                List.of("2c 3c 4c", "2c 3c 4d", "2c 3d 4h", "2c 2d 3c", "2c 2d 3h", "2c 2d 2h");
        for (int t = 0; t < 6; t++) {
            var flop = cards(examples.get(t));
            for (var order :
                    List.of(
                            List.of(0, 1, 2),
                            List.of(0, 2, 1),
                            List.of(1, 0, 2),
                            List.of(1, 2, 0),
                            List.of(2, 0, 1),
                            List.of(2, 1, 0)))
                assertEquals(
                        SixMaxTexturePayoffTable.Texture.values()[t],
                        SixMaxTexturePayoffTable.classify(
                                flop.get(order.get(0)),
                                flop.get(order.get(1)),
                                flop.get(order.get(2))));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxTexturePayoffTable.classify(
                                Card.parse("2c"), Card.parse("2c"), Card.parse("3c")));
    }

    @Test
    void fiveCardBoardOptimizationMatchesIndependentFlopThenRunoutEnumerationForEveryPair() {
        var hands =
                SixMaxPreflopConvergenceMain.ranges("button-mix").stream()
                        .map(List::getFirst)
                        .toList();
        var deck = cards("2c 2d 2h 3c 3d 4c 5d 6h");
        var table = SixMaxTexturePayoffTable.enumerate(hands, deck);
        long[] counts = new long[6];
        long[][] wins = new long[64][6], ties = new long[64][6];
        // Independently select each flop first, remove it, then enumerate its unordered runouts.
        for (int a = 0; a < deck.size(); a++)
            for (int b = a + 1; b < deck.size(); b++)
                for (int c = b + 1; c < deck.size(); c++) {
                    var flop = List.of(deck.get(a), deck.get(b), deck.get(c));
                    int t =
                            SixMaxTexturePayoffTable.classify(flop.get(0), flop.get(1), flop.get(2))
                                    .ordinal();
                    counts[t]++;
                    var remaining = new ArrayList<>(deck);
                    remaining.removeAll(flop);
                    for (int x = 0; x < remaining.size(); x++)
                        for (int y = x + 1; y < remaining.size(); y++) {
                            var scores = new ArrayList<com.pokerlab.core.hand.EvaluatedHand>();
                            for (var hand : hands)
                                scores.add(
                                        HandEvaluator.evaluateBest(
                                                hand.first(),
                                                hand.second(),
                                                flop.get(0),
                                                flop.get(1),
                                                flop.get(2),
                                                remaining.get(x),
                                                remaining.get(y)));
                            for (int i = 0; i < 6; i++)
                                for (int j = i + 1; j < 6; j++) {
                                    int mask = (1 << i) | (1 << j),
                                            comparison = scores.get(i).compareTo(scores.get(j));
                                    if (comparison > 0) wins[mask][t]++;
                                    else if (comparison == 0) ties[mask][t]++;
                                }
                        }
                }
        assertEquals(Arrays.stream(counts).boxed().toList(), table.flopCounts());
        assertTrue(table.flopCounts().stream().allMatch(count -> count > 0));
        assertEquals(56, Arrays.stream(counts).sum());
        for (var pair : table.pairs()) {
            assertEquals(Arrays.stream(wins[pair.activeMask()]).boxed().toList(), pair.firstWins());
            assertEquals(Arrays.stream(ties[pair.activeMask()]).boxed().toList(), pair.ties());
            for (int t = 0; t < 6; t++) {
                int first = Integer.numberOfTrailingZeros(pair.activeMask()),
                        second = Integer.numberOfTrailingZeros(pair.activeMask() ^ (1 << first));
                assertEquals(
                        1,
                        pair.share(first, t, counts[t] * 10)
                                + pair.share(second, t, counts[t] * 10),
                        1e-15);
            }
        }
    }

    @Test
    void rejectsOverlappingPhysicalCardsAndInvalidPairQueries() {
        var hands =
                SixMaxPreflopConvergenceMain.ranges("button-mix").stream()
                        .map(List::getFirst)
                        .toList();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTexturePayoffTable.enumerate(hands, cards("As 2c 3c 4c 5c")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTexturePayoffTable.enumerate(hands, cards("2c 2c 3c 4c 5c")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxTexturePayoffTable.Pair(7, List.of(), List.of()));
        var pair =
                new SixMaxTexturePayoffTable.Pair(
                        3, List.of(0L, 0L, 0L, 0L, 0L, 0L), List.of(0L, 0L, 0L, 0L, 0L, 0L));
        for (int seat : List.of(-1, 2, 32))
            assertThrows(IllegalArgumentException.class, () -> pair.share(seat, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> pair.share(0, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> pair.share(0, 0, 0));
    }
}
