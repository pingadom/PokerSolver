package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;
import java.util.SplittableRandom;

/** Samples one seeded board stream per physical deal and values every active-player subset. */
public final class SharedBoardMultiwayShowdownOracle implements MultiwayShowdownOracle {
    private final int trials;
    private final long baseSeed;
    private List<String> cachedDeal;
    private MultiwayShowdownEstimate[] cachedEstimates;
    private long boardsEvaluated;

    public SharedBoardMultiwayShowdownOracle(int trials, long baseSeed) {
        if (trials < 1) throw new IllegalArgumentException("trials must be positive");
        this.trials = trials;
        this.baseSeed = baseSeed;
    }

    @Override
    public synchronized MultiwayShowdownEstimate estimate(
            List<WeightedCombo> dealt, int activeMask) {
        if (dealt == null || dealt.size() < 2 || dealt.size() > 6)
            throw new IllegalArgumentException("Expected two to six dealt players");
        if ((activeMask & ~((1 << dealt.size()) - 1)) != 0 || Integer.bitCount(activeMask) < 2)
            throw new IllegalArgumentException("At least two dealt players must reach showdown");
        Deck deck = new Deck();
        long seed = baseSeed;
        for (WeightedCombo combo : dealt) {
            if (combo == null || !deck.remove(combo.first()) || !deck.remove(combo.second()))
                throw new IllegalArgumentException("Dealt cards must be distinct");
            seed = 31 * seed + combo.key().hashCode();
        }
        List<String> key = dealt.stream().map(WeightedCombo::key).toList();
        if (!key.equals(cachedDeal)) {
            cachedEstimates = sample(dealt, deck.cards().toArray(Card[]::new), seed);
            cachedDeal = key;
            boardsEvaluated += trials;
        }
        return cachedEstimates[activeMask];
    }

    /** Counts boards sampled to populate the bounded one-deal cache. */
    public synchronized long boardsEvaluated() {
        return boardsEvaluated;
    }

    private MultiwayShowdownEstimate[] sample(List<WeightedCombo> dealt, Card[] deck, long seed) {
        int players = dealt.size();
        int subsets = 1 << players;
        double[][] sums = new double[subsets][players];
        double[][] squareSums = new double[subsets][players];
        int[] scores = new int[players];
        int[] best = new int[subsets];
        int[] winners = new int[subsets];
        Card[][] hands = new Card[players][7];
        for (int player = 0; player < players; player++) {
            hands[player][0] = dealt.get(player).first();
            hands[player][1] = dealt.get(player).second();
        }
        SplittableRandom random = new SplittableRandom(seed);
        for (int trial = 0; trial < trials; trial++) {
            // The first five positions are a uniformly sampled board without replacement.
            for (int index = 0; index < 5; index++) {
                int selected = index + random.nextInt(deck.length - index);
                Card swap = deck[index];
                deck[index] = deck[selected];
                deck[selected] = swap;
            }
            for (int player = 0; player < players; player++) {
                Card[] hand = hands[player];
                System.arraycopy(deck, 0, hand, 2, 5);
                scores[player] = HandEvaluator.evaluateBestScore(hand);
            }
            // Dynamic subset maximum shares a player's board score across all masks.
            for (int mask = 1; mask < subsets; mask++) {
                int bit = Integer.lowestOneBit(mask);
                int player = Integer.numberOfTrailingZeros(bit);
                int rest = mask ^ bit;
                if (rest == 0 || scores[player] > best[rest]) {
                    best[mask] = scores[player];
                    winners[mask] = bit;
                } else {
                    best[mask] = best[rest];
                    winners[mask] = winners[rest] | (scores[player] == best[rest] ? bit : 0);
                }
                if (rest == 0) continue;
                double share = 1.0 / Integer.bitCount(winners[mask]);
                for (int winning = winners[mask]; winning != 0; winning &= winning - 1) {
                    int seat = Integer.numberOfTrailingZeros(winning);
                    sums[mask][seat] += share;
                    squareSums[mask][seat] += share * share;
                }
            }
        }
        MultiwayShowdownEstimate[] estimates = new MultiwayShowdownEstimate[subsets];
        for (int mask = 1; mask < subsets; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            double[] shares = new double[players];
            double[] errors = new double[players];
            for (int seat = 0; seat < players; seat++) {
                shares[seat] = sums[mask][seat] / trials;
                errors[seat] =
                        Math.sqrt(
                                Math.max(
                                                0,
                                                squareSums[mask][seat] / trials
                                                        - shares[seat] * shares[seat])
                                        / trials);
            }
            estimates[mask] = new MultiwayShowdownEstimate(shares, errors, trials);
        }
        return estimates;
    }
}
