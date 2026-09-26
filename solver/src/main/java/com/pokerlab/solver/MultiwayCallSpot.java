package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Immutable assumptions for a bounded equal-stack, no-rake call/fold subgame. */
public record MultiwayCallSpot(
        String id,
        List<PreflopAllInSpot.Seat> seats,
        List<List<WeightedCombo>> ranges,
        List<Double> committedBb,
        double stackBb,
        double deadMoneyBb) {
    public MultiwayCallSpot {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Spot id must be lowercase and hyphenated");
        if (seats == null || ranges == null || committedBb == null)
            throw new IllegalArgumentException("Seats, ranges and commitments are required");
        seats = List.copyOf(seats);
        committedBb =
                committedBb.stream()
                        .map(value -> value != null && value == 0 ? 0.0 : value)
                        .toList();
        ranges = ranges.stream().map(MultiwayCallSpot::canonicalRange).toList();
        if (deadMoneyBb == 0) deadMoneyBb = 0;
        // Reuse the game's bounded-deal and chip-accounting checks without computing equities.
        MultiwayPreflopCallGame validated =
                new MultiwayPreflopCallGame(
                        seats,
                        ranges,
                        committedBb,
                        stackBb,
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
                seats, ranges, committedBb, stackBb, deadMoneyBb, oracle);
    }

    /** Stable SHA-256 of all assumptions; range input order does not affect it. */
    public String contentHash() {
        StringBuilder canonical =
                new StringBuilder("multiway-call-spot/v1|preflop|no-rake|equal-stack|");
        append(canonical, id);
        append(canonical, Double.toHexString(stackBb));
        append(canonical, Double.toHexString(deadMoneyBb));
        append(canonical, Integer.toString(seats.size()));
        for (int seat = 0; seat < seats.size(); seat++) {
            append(canonical, seats.get(seat).name());
            append(canonical, Double.toHexString(committedBb.get(seat)));
            append(canonical, Integer.toString(ranges.get(seat).size()));
            for (WeightedCombo combo : ranges.get(seat)) {
                append(canonical, combo.key());
                append(canonical, Double.toHexString(combo.weight()));
            }
        }
        return sha256(canonical.toString());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
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
