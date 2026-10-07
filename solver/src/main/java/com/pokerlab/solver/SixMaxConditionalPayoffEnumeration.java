package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Shared exact integer kernel; a public flop partition must depend on its three cards only. */
final class SixMaxConditionalPayoffEnumeration {
    @FunctionalInterface
    interface Partition {
        int classify(Card first, Card second, Card third);
    }

    record Pair(int activeMask, List<Long> firstWins, List<Long> ties) {}

    record Result(List<Long> flopCounts, List<Pair> pairs) {}

    private SixMaxConditionalPayoffEnumeration() {}

    static List<Card> undealt(List<WeightedCombo> hands) {
        if (hands.size() != 6) throw new IllegalArgumentException("Expected six physical hands");
        var deck = new Deck();
        for (var hand : hands)
            if (!deck.remove(hand.first()) || !deck.remove(hand.second()))
                throw new IllegalArgumentException("Private cards overlap");
        return deck.cards();
    }

    static Result enumerate(
            List<WeightedCombo> dealt, List<Card> deckList, int classes, Partition partition) {
        if (dealt.size() != 6
                || deckList.size() < 5
                || deckList.size() > 40
                || classes < 1
                || classes > 1183)
            throw new IllegalArgumentException("Invalid bounded six-hand enumeration");
        var unique = new HashSet<Card>();
        for (var hand : dealt)
            if (!unique.add(hand.first()) || !unique.add(hand.second()))
                throw new IllegalArgumentException("Overlapping private cards");
        for (var card : deckList)
            if (!unique.add(card)) throw new IllegalArgumentException("Overlapping deck cards");
        Card[] deck = deckList.toArray(Card[]::new);
        int n = deck.length;
        int[] flopClasses = new int[n * n * n];
        long[] flopCounts = new long[classes], observedRunouts = new long[classes];
        for (int a = 0; a < n - 2; a++)
            for (int b = a + 1; b < n - 1; b++)
                for (int c = b + 1; c < n; c++) {
                    int signal = partition.classify(deck[a], deck[b], deck[c]);
                    if (signal < 0 || signal >= classes)
                        throw new IllegalArgumentException("Flop partition index out of bounds");
                    flopClasses[(a * n + b) * n + c] = signal;
                    flopCounts[signal]++;
                }
        int[] masks = new int[15], first = new int[15], second = new int[15];
        int pairIndex = 0;
        for (int mask = 0; mask < 64; mask++)
            if (Integer.bitCount(mask) == 2) {
                masks[pairIndex] = mask;
                first[pairIndex] = Integer.numberOfTrailingZeros(mask);
                second[pairIndex] =
                        Integer.numberOfTrailingZeros(mask ^ Integer.lowestOneBit(mask));
                pairIndex++;
            }
        long[][] wins = new long[15][classes], ties = new long[15][classes];
        Card[][] hands = new Card[6][7];
        int[] scores = new int[6], indices = new int[5], signals = new int[10];
        for (int seat = 0; seat < 6; seat++) {
            hands[seat][0] = dealt.get(seat).first();
            hands[seat][1] = dealt.get(seat).second();
        }
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++) {
                            indices[0] = a;
                            indices[1] = b;
                            indices[2] = c;
                            indices[3] = d;
                            indices[4] = e;
                            int offset = 0;
                            for (int x = 0; x < 3; x++)
                                for (int y = x + 1; y < 4; y++)
                                    for (int z = y + 1; z < 5; z++) {
                                        int signal =
                                                flopClasses[
                                                        (indices[x] * n + indices[y]) * n
                                                                + indices[z]];
                                        signals[offset++] = signal;
                                        observedRunouts[signal]++;
                                    }
                            for (int seat = 0; seat < 6; seat++) {
                                for (int card = 0; card < 5; card++)
                                    hands[seat][card + 2] = deck[indices[card]];
                                scores[seat] = HandEvaluator.evaluateBestScore(hands[seat]);
                            }
                            for (int p = 0; p < 15; p++) {
                                int comparison =
                                        Integer.compare(scores[first[p]], scores[second[p]]);
                                if (comparison < 0) continue;
                                for (int signal : signals)
                                    if (comparison == 0) ties[p][signal]++;
                                    else wins[p][signal]++;
                            }
                        }
        long perFlop = (long) (n - 3) * (n - 4) / 2;
        for (int signal = 0; signal < classes; signal++)
            if (observedRunouts[signal] != flopCounts[signal] * perFlop)
                throw new IllegalStateException("Per-signal flop/runout identity failed");
        var pairs = new ArrayList<Pair>();
        for (int p = 0; p < 15; p++) pairs.add(new Pair(masks[p], longs(wins[p]), longs(ties[p])));
        return new Result(longs(flopCounts), List.copyOf(pairs));
    }

    private static List<Long> longs(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    /**
     * Exact named-flop refinement. Production supplies all 37 remaining cards; tests may use fewer.
     */
    static Result fixedFlop(List<WeightedCombo> dealt, List<Card> flop, List<Card> remaining) {
        if (dealt.size() != 6 || flop.size() != 3 || remaining.size() < 2 || remaining.size() > 37)
            throw new IllegalArgumentException("Invalid bounded fixed-flop enumeration");
        var unique = new HashSet<Card>();
        for (var hand : dealt)
            if (!unique.add(hand.first()) || !unique.add(hand.second()))
                throw new IllegalArgumentException("Overlapping private cards");
        for (var card : flop)
            if (!unique.add(card)) throw new IllegalArgumentException("Blocked or duplicate flop");
        for (var card : remaining)
            if (!unique.add(card))
                throw new IllegalArgumentException("Blocked or duplicate runout card");
        long[] wins = new long[64], ties = new long[64];
        int[] scores = new int[6];
        Card[][] hands = new Card[6][7];
        for (int seat = 0; seat < 6; seat++) {
            hands[seat][0] = dealt.get(seat).first();
            hands[seat][1] = dealt.get(seat).second();
            for (int c = 0; c < 3; c++) hands[seat][c + 2] = flop.get(c);
        }
        long runouts = 0;
        for (int a = 0; a < remaining.size() - 1; a++)
            for (int b = a + 1; b < remaining.size(); b++) {
                for (int seat = 0; seat < 6; seat++) {
                    hands[seat][5] = remaining.get(a);
                    hands[seat][6] = remaining.get(b);
                    scores[seat] = HandEvaluator.evaluateBestScore(hands[seat]);
                }
                for (int first = 0; first < 6; first++)
                    for (int second = first + 1; second < 6; second++) {
                        int mask = (1 << first) | (1 << second);
                        int comparison = Integer.compare(scores[first], scores[second]);
                        if (comparison > 0) wins[mask]++;
                        else if (comparison == 0) ties[mask]++;
                    }
                runouts++;
            }
        if (runouts != (long) remaining.size() * (remaining.size() - 1) / 2)
            throw new IllegalStateException("Fixed-flop runout count differs");
        var pairs = new ArrayList<Pair>();
        for (int mask = 0; mask < 64; mask++)
            if (Integer.bitCount(mask) == 2)
                pairs.add(new Pair(mask, List.of(wins[mask]), List.of(ties[mask])));
        return new Result(List.of(1L), List.copyOf(pairs));
    }
}
