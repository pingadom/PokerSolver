package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Samples one seeded board stream per physical deal and values every active-player subset. */
public final class SharedBoardMultiwayShowdownOracle implements MultiwayShowdownOracle {
    public static final int MAX_CACHED_DEALS = 64;
    private int targetTrials;
    private final long baseSeed;
    private final int maximumCachedDeals;
    private final Map<List<String>, Accumulator> cache = new LinkedHashMap<>(16, 0.75f, true);
    private long boardsEvaluated;

    public SharedBoardMultiwayShowdownOracle(int trials, long baseSeed) {
        this(trials, baseSeed, 1);
    }

    /** Keeps at most the requested number of physical-deal streams, evicting least recent first. */
    public SharedBoardMultiwayShowdownOracle(int trials, long baseSeed, int maximumCachedDeals) {
        if (trials < 1) throw new IllegalArgumentException("trials must be positive");
        if (maximumCachedDeals < 1 || maximumCachedDeals > MAX_CACHED_DEALS)
            throw new IllegalArgumentException("Cache needs one to 64 physical deals");
        this.targetTrials = trials;
        this.baseSeed = baseSeed;
        this.maximumCachedDeals = maximumCachedDeals;
    }

    /** Extends retained streams on their next estimate request; budgets may never decrease. */
    public synchronized void increaseTrialsTo(int trials) {
        if (trials < targetTrials)
            throw new IllegalArgumentException("Board budgets must not decrease");
        targetTrials = trials;
    }

    public synchronized int cachedDeals() {
        return cache.size();
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
        Accumulator accumulator = cache.get(key);
        if (accumulator == null) {
            accumulator = new Accumulator(dealt, deck.cards().toArray(Card[]::new), seed);
            if (cache.size() == maximumCachedDeals) cache.remove(cache.keySet().iterator().next());
            cache.put(key, accumulator);
        }
        int previousTrials = accumulator.completedTrials;
        accumulator.advanceTo(targetTrials);
        boardsEvaluated += accumulator.completedTrials - previousTrials;
        return accumulator.estimates[activeMask];
    }

    /** Counts actual board evaluations, including work repeated after a cache eviction. */
    public synchronized long boardsEvaluated() {
        return boardsEvaluated;
    }

    private static final class Accumulator {
        private final int players;
        private final int subsets;
        private final double[][] sums;
        private final double[][] squareSums;
        private final int[] scores;
        private final int[] best;
        private final int[] winners;
        private final Card[][] hands;
        private final Card[] deck;
        private final SplittableRandom random;
        private int completedTrials;
        private MultiwayShowdownEstimate[] estimates;

        private Accumulator(List<WeightedCombo> dealt, Card[] deck, long seed) {
            players = dealt.size();
            subsets = 1 << players;
            sums = new double[subsets][players];
            squareSums = new double[subsets][players];
            scores = new int[players];
            best = new int[subsets];
            winners = new int[subsets];
            hands = new Card[players][7];
            this.deck = deck;
            random = new SplittableRandom(seed);
            for (int player = 0; player < players; player++) {
                hands[player][0] = dealt.get(player).first();
                hands[player][1] = dealt.get(player).second();
            }
        }

        private void advanceTo(int trials) {
            if (completedTrials == trials) return;
            for (; completedTrials < trials; completedTrials++) {
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
            estimates = new MultiwayShowdownEstimate[subsets];
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
        }
    }
}
