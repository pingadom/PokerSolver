package com.pokerlab.core.hand;

import java.util.List;
import java.util.Objects;

public record HandRank(HandCategory category, List<Integer> tiebreakers)
        implements Comparable<HandRank> {

    public HandRank {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(tiebreakers, "tiebreakers must not be null");
        tiebreakers = List.copyOf(tiebreakers);
    }

    @Override
    public int compareTo(HandRank other) {
        int categoryComparison =
                Integer.compare(this.category.strength(), other.category.strength());
        if (categoryComparison != 0) {
            return categoryComparison;
        }

        int max = Math.min(this.tiebreakers.size(), other.tiebreakers.size());
        for (int i = 0; i < max; i++) {
            int comparison = Integer.compare(this.tiebreakers.get(i), other.tiebreakers.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }

        return Integer.compare(this.tiebreakers.size(), other.tiebreakers.size());
    }

    public boolean beats(HandRank other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return category.displayName() + " " + tiebreakers;
    }
}
