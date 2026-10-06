package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable exact-range assumptions for a six-seat mandatory-checkdown research game. */
public record SixMaxPreflopResearchSpot(
        String id,
        SixMaxPreflopBetting.Rules rules,
        List<List<WeightedCombo>> ranges,
        CashRakeRule rake,
        String continuationModel) {
    public static final String MANDATORY_CHECKDOWN = "MANDATORY_CHECKDOWN";

    public SixMaxPreflopResearchSpot {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Spot id must be lowercase and hyphenated");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(rake, "rake");
        if (!MANDATORY_CHECKDOWN.equals(continuationModel))
            throw new IllegalArgumentException("Unsupported preflop continuation model");
        if (ranges == null || ranges.size() != 6)
            throw new IllegalArgumentException("Exactly six seat ranges are required");
        ranges = ranges.stream().map(SixMaxPreflopResearchSpot::canonicalRange).toList();
        var checked =
                new SixMaxPreflopCheckdownGame(
                        rules,
                        ranges,
                        rake,
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            for (int seat = 0; seat < 6; seat++)
                                if ((mask & (1 << seat)) != 0)
                                    shares[seat] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        List<Set<String>> possible = new ArrayList<>();
        for (int seat = 0; seat < 6; seat++) possible.add(new HashSet<>());
        for (var outcome : checked.chanceOutcomes(checked.initialState()))
            for (int seat = 0; seat < 6; seat++)
                possible.get(seat).add(checked.dealtHands(outcome.state()).get(seat).key());
        for (int seat = 0; seat < 6; seat++)
            if (possible.get(seat).size() != ranges.get(seat).size())
                throw new IllegalArgumentException(
                        "Every range combo needs an unblocked joint deal");
    }

    public SixMaxPreflopCheckdownGame game(MultiwayShowdownOracle oracle) {
        return new SixMaxPreflopCheckdownGame(rules, ranges, rake, oracle);
    }

    public String contentHash() {
        String version =
                rules.raiseSchedule() == SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET
                        ? "six-max-preflop-spot/v2"
                        : "six-max-preflop-spot/v1";
        return MultiwayCallSpot.sha256(
                version + "|EXACT_RANGE_PRODUCT|" + MultiwayPackJson.writeFullRoundSpot(this));
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
