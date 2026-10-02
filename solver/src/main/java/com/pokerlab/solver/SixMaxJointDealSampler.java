package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Rejection-samples a bounded empirical six-hand support from independent weighted ranges. */
public final class SixMaxJointDealSampler {
    public record JointDeal(List<WeightedCombo> hands, int occurrences) {
        public JointDeal {
            hands = List.copyOf(hands);
            if (hands.size() != Seat.values().length || occurrences < 1)
                throw new IllegalArgumentException("Expected six hands and a positive count");
            Set<Card> cards = new HashSet<>();
            for (WeightedCombo combo : hands)
                if (!cards.add(combo.first()) || !cards.add(combo.second()))
                    throw new IllegalArgumentException("Joint deal has overlapping cards");
        }
    }

    /** Counts describe an empirical chance distribution, not the exact range product. */
    public record Sample(long seed, int acceptedDraws, int rejectedDraws, List<JointDeal> deals) {
        public Sample {
            deals = List.copyOf(deals);
            if (acceptedDraws < 1 || rejectedDraws < 0 || deals.isEmpty())
                throw new IllegalArgumentException("Sample needs accepted draws and legal deals");
            long total = 0;
            Set<List<String>> unique = new HashSet<>();
            for (JointDeal deal : deals) {
                total += deal.occurrences();
                if (!unique.add(deal.hands().stream().map(WeightedCombo::key).toList()))
                    throw new IllegalArgumentException("Repeated joint deal in sample support");
            }
            if (total != acceptedDraws)
                throw new IllegalArgumentException("Joint-deal counts must total accepted draws");
        }
    }

    private static final class CountedDeal {
        private final List<WeightedCombo> hands;
        private int count;

        private CountedDeal(List<WeightedCombo> hands) {
            this.hands = List.copyOf(hands);
            count = 1;
        }
    }

    private record RangeDistribution(
            List<WeightedCombo> combos, double[] scaledWeights, double total) {
        private WeightedCombo draw(SplittableRandom random) {
            double draw = random.nextDouble(total);
            double cumulative = 0;
            for (int index = 0; index < combos.size(); index++) {
                cumulative += scaledWeights[index];
                if (draw < cumulative) return combos.get(index);
            }
            return combos.getLast();
        }
    }

    private SixMaxJointDealSampler() {}

    public static Sample sample(
            List<List<WeightedCombo>> ranges, int acceptedDraws, int maxAttempts, long seed) {
        if (acceptedDraws < 1 || maxAttempts < acceptedDraws)
            throw new IllegalArgumentException("Need positive draws and enough attempts");
        List<RangeDistribution> distributions = prepare(ranges);
        SplittableRandom random = new SplittableRandom(seed);
        Map<List<String>, CountedDeal> counts = new LinkedHashMap<>();
        int accepted = 0;
        int attempts = 0;
        while (accepted < acceptedDraws && attempts < maxAttempts) {
            attempts++;
            List<WeightedCombo> hands = new ArrayList<>();
            for (RangeDistribution distribution : distributions)
                hands.add(distribution.draw(random));
            Set<Card> cards = new HashSet<>();
            boolean legal = true;
            for (WeightedCombo combo : hands)
                if (!cards.add(combo.first()) || !cards.add(combo.second())) legal = false;
            if (!legal) continue;
            accepted++;
            List<String> key = hands.stream().map(WeightedCombo::key).toList();
            CountedDeal existing = counts.get(key);
            if (existing == null) counts.put(key, new CountedDeal(hands));
            else existing.count++;
        }
        if (accepted < acceptedDraws)
            throw new IllegalArgumentException(
                    "Could not draw enough unblocked joint deals within maxAttempts");
        List<JointDeal> deals = new ArrayList<>();
        for (CountedDeal counted : counts.values())
            deals.add(new JointDeal(counted.hands, counted.count));
        return new Sample(seed, accepted, attempts - accepted, deals);
    }

    private static List<RangeDistribution> prepare(List<List<WeightedCombo>> ranges) {
        if (ranges == null || ranges.size() != Seat.values().length)
            throw new IllegalArgumentException("Exactly six seat ranges are required");
        List<RangeDistribution> result = new ArrayList<>();
        for (List<WeightedCombo> candidate : ranges) {
            if (candidate == null || candidate.isEmpty())
                throw new IllegalArgumentException("Every seat needs a nonempty range");
            List<WeightedCombo> combos = List.copyOf(candidate);
            Set<String> unique = new HashSet<>();
            double max = 0;
            for (WeightedCombo combo : combos) {
                if (combo == null || !unique.add(combo.key()))
                    throw new IllegalArgumentException("Range has a missing or duplicate combo");
                max = Math.max(max, combo.weight());
            }
            double[] scaled = new double[combos.size()];
            double total = 0;
            for (int index = 0; index < combos.size(); index++) {
                scaled[index] = combos.get(index).weight() / max;
                total += scaled[index];
            }
            result.add(new RangeDistribution(combos, scaled, total));
        }
        return List.copyOf(result);
    }
}
