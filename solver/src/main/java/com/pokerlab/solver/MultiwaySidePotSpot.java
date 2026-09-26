package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Canonical assumptions for an unequal-stack, forced-shove call/fold research game. */
public record MultiwaySidePotSpot(
        String id,
        List<PreflopAllInSpot.Seat> seats,
        List<List<WeightedCombo>> ranges,
        List<Double> committedBb,
        List<Double> stacksBb,
        double deadMoneyBb) {
    public MultiwaySidePotSpot {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Spot id must be lowercase and hyphenated");
        if (seats == null || ranges == null || committedBb == null || stacksBb == null)
            throw new IllegalArgumentException(
                    "Seats, ranges, commitments and stacks are required");
        seats = List.copyOf(seats);
        committedBb =
                committedBb.stream()
                        .map(value -> value != null && value == 0 ? 0.0 : value)
                        .toList();
        stacksBb = List.copyOf(stacksBb);
        ranges = ranges.stream().map(MultiwaySidePotSpot::canonicalRange).toList();
        if (deadMoneyBb == 0) deadMoneyBb = 0;
        MultiwayPreflopCallGame validated =
                new MultiwayPreflopCallGame(
                        seats,
                        ranges,
                        committedBb,
                        stacksBb,
                        deadMoneyBb,
                        (dealt, mask) -> {
                            double[] shares = new double[dealt.size()];
                            for (int seat = 0; seat < shares.length; seat++)
                                if ((mask & (1 << seat)) != 0)
                                    shares[seat] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        List<Set<String>> possible = new ArrayList<>();
        for (int seat = 0; seat < seats.size(); seat++) possible.add(new HashSet<>());
        for (var outcome : validated.chanceOutcomes(validated.initialState())) {
            var dealt = validated.dealtCombos(outcome.state());
            for (int seat = 0; seat < seats.size(); seat++)
                possible.get(seat).add(dealt.get(seat).key());
        }
        for (int seat = 0; seat < seats.size(); seat++)
            if (possible.get(seat).size() != ranges.get(seat).size())
                throw new IllegalArgumentException(
                        "Every range combo needs an unblocked joint deal");
    }

    public MultiwayPreflopCallGame game(MultiwayShowdownOracle oracle) {
        return new MultiwayPreflopCallGame(
                seats, ranges, committedBb, stacksBb, deadMoneyBb, oracle);
    }

    public String contentHash() {
        StringBuilder canonical = new StringBuilder("multiway-side-pot-spot/v1|preflop|no-rake|");
        append(canonical, id);
        append(canonical, Double.toHexString(deadMoneyBb));
        append(canonical, Integer.toString(seats.size()));
        for (int seat = 0; seat < seats.size(); seat++) {
            append(canonical, seats.get(seat).name());
            append(canonical, Double.toHexString(stacksBb.get(seat)));
            append(canonical, Double.toHexString(committedBb.get(seat)));
            append(canonical, Integer.toString(ranges.get(seat).size()));
            for (WeightedCombo combo : ranges.get(seat)) {
                append(canonical, combo.key());
                append(canonical, Double.toHexString(combo.weight()));
            }
        }
        return MultiwayCallSpot.sha256(canonical.toString());
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private static List<WeightedCombo> canonicalRange(List<WeightedCombo> range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Every seat needs a nonempty range");
        Set<String> seen = new HashSet<>();
        List<WeightedCombo> sorted = new ArrayList<>();
        for (WeightedCombo combo : range) {
            if (combo == null || !seen.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate combo in range");
            sorted.add(combo);
        }
        sorted.sort(Comparator.comparing(WeightedCombo::key));
        return List.copyOf(sorted);
    }
}
