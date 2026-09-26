package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;
import java.util.SplittableRandom;

/** Reproducible board sampling for 2-6 way all-ins; not an exact payoff oracle. */
public final class SeededMultiwayShowdownOracle implements MultiwayShowdownOracle {
    private final int trials;
    private final long baseSeed;

    public SeededMultiwayShowdownOracle(int trials, long baseSeed) {
        if (trials < 1) throw new IllegalArgumentException("trials must be positive");
        this.trials = trials;
        this.baseSeed = baseSeed;
    }

    @Override
    public MultiwayShowdownEstimate estimate(List<WeightedCombo> dealt, int activeMask) {
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
        seed = 31 * seed + activeMask;
        Card[] remaining = deck.cards().toArray(Card[]::new);
        SplittableRandom random = new SplittableRandom(seed);
        double[] shares = new double[dealt.size()];
        double[] secondMoments = new double[dealt.size()];
        int[] scores = new int[dealt.size()];
        for (int trial = 0; trial < trials; trial++) {
            // Partial Fisher-Yates: only the five board cards need to be sampled.
            for (int index = 0; index < 5; index++) {
                int selected = index + random.nextInt(remaining.length - index);
                Card swap = remaining[index];
                remaining[index] = remaining[selected];
                remaining[selected] = swap;
            }
            int best = -1;
            int winners = 0;
            for (int player = 0; player < dealt.size(); player++) {
                if ((activeMask & (1 << player)) == 0) continue;
                WeightedCombo combo = dealt.get(player);
                int score =
                        HandEvaluator.evaluateBestScore(
                                combo.first(),
                                combo.second(),
                                remaining[0],
                                remaining[1],
                                remaining[2],
                                remaining[3],
                                remaining[4]);
                scores[player] = score;
                if (score > best) {
                    best = score;
                    winners = 1;
                } else if (score == best) winners++;
            }
            for (int player = 0; player < dealt.size(); player++)
                if ((activeMask & (1 << player)) != 0 && scores[player] == best) {
                    shares[player] += 1.0 / winners;
                    secondMoments[player] += 1.0 / (winners * (double) winners);
                }
        }
        double[] standardErrors = new double[shares.length];
        for (int player = 0; player < shares.length; player++) {
            shares[player] /= trials;
            standardErrors[player] =
                    Math.sqrt(
                            Math.max(
                                            0,
                                            secondMoments[player] / trials
                                                    - shares[player] * shares[player])
                                    / trials);
        }
        return new MultiwayShowdownEstimate(shares, standardErrors, trials);
    }
}
