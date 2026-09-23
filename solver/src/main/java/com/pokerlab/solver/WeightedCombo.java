package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.Objects;

/** One physical two-card combination and its positive range weight. */
public record WeightedCombo(Card first, Card second, double weight) {
    public WeightedCombo {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second))
            throw new IllegalArgumentException("A combo needs two distinct cards");
        if (!Double.isFinite(weight) || weight <= 0)
            throw new IllegalArgumentException("Range weight must be finite and positive");
        if (first.compact().compareTo(second.compact()) > 0) {
            Card swap = first;
            first = second;
            second = swap;
        }
    }

    public String key() {
        return first.compact() + " " + second.compact();
    }

    public boolean conflictsWith(WeightedCombo other) {
        return first.equals(other.first)
                || first.equals(other.second)
                || second.equals(other.first)
                || second.equals(other.second);
    }
}
