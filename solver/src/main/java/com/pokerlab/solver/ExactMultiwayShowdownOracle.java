package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;

/** Enumerates every board once per joint deal and reuses hand scores across all active subsets. */
public final class ExactMultiwayShowdownOracle implements MultiwayShowdownOracle {
    private List<String> cachedDeal;
    private MultiwayShowdownEstimate[] cachedEstimates;

    /** Cache is bounded to one joint deal; weights do not affect showdown shares. */
    @Override
    public synchronized MultiwayShowdownEstimate estimate(
            List<WeightedCombo> dealt, int activeMask) {
        if (dealt == null || dealt.size() < 2 || dealt.size() > 6)
            throw new IllegalArgumentException("Expected two to six dealt players");
        if ((activeMask & ~((1 << dealt.size()) - 1)) != 0 || Integer.bitCount(activeMask) < 2)
            throw new IllegalArgumentException("At least two dealt players must reach showdown");
        Deck deck = new Deck();
        for (WeightedCombo combo : dealt)
            if (combo == null || !deck.remove(combo.first()) || !deck.remove(combo.second()))
                throw new IllegalArgumentException("Dealt cards must be distinct");
        List<String> key = dealt.stream().map(WeightedCombo::key).toList();
        if (!key.equals(cachedDeal)) {
            cachedEstimates = enumerate(dealt, deck.cards().toArray(Card[]::new));
            cachedDeal = key;
        }
        return cachedEstimates[activeMask];
    }

    private MultiwayShowdownEstimate[] enumerate(List<WeightedCombo> dealt, Card[] deck) {
        int players = dealt.size();
        int subsets = 1 << players;
        double[][] sums = new double[subsets][players];
        int[] scores = new int[players];
        int[] best = new int[subsets];
        int[] winners = new int[subsets];
        Card[][] hands = new Card[players][7];
        for (int player = 0; player < players; player++) {
            hands[player][0] = dealt.get(player).first();
            hands[player][1] = dealt.get(player).second();
        }
        long boards = 0;
        for (int a = 0; a < deck.length - 4; a++)
            for (int b = a + 1; b < deck.length - 3; b++)
                for (int c = b + 1; c < deck.length - 2; c++)
                    for (int d = c + 1; d < deck.length - 1; d++)
                        for (int e = d + 1; e < deck.length; e++) {
                            for (int player = 0; player < players; player++) {
                                Card[] hand = hands[player];
                                hand[2] = deck[a];
                                hand[3] = deck[b];
                                hand[4] = deck[c];
                                hand[5] = deck[d];
                                hand[6] = deck[e];
                                scores[player] = HandEvaluator.evaluateBestScore(hand);
                            }
                            // Dynamic subset maximum: each mask adds one player to a smaller mask.
                            for (int mask = 1; mask < subsets; mask++) {
                                int bit = Integer.lowestOneBit(mask);
                                int player = Integer.numberOfTrailingZeros(bit);
                                int rest = mask ^ bit;
                                if (rest == 0 || scores[player] > best[rest]) {
                                    best[mask] = scores[player];
                                    winners[mask] = bit;
                                } else {
                                    best[mask] = best[rest];
                                    winners[mask] =
                                            winners[rest]
                                                    | (scores[player] == best[rest] ? bit : 0);
                                }
                                if (rest == 0) continue;
                                double share = 1.0 / Integer.bitCount(winners[mask]);
                                for (int winning = winners[mask];
                                        winning != 0;
                                        winning &= winning - 1)
                                    sums[mask][Integer.numberOfTrailingZeros(winning)] += share;
                            }
                            boards++;
                        }
        MultiwayShowdownEstimate[] result = new MultiwayShowdownEstimate[subsets];
        for (int mask = 1; mask < subsets; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            for (int player = 0; player < players; player++) sums[mask][player] /= boards;
            result[mask] = new MultiwayShowdownEstimate(sums[mask], new double[players], boards);
        }
        return result;
    }
}
