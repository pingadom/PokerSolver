package com.pokerlab.core.hand;

import com.pokerlab.core.card.Card;

import java.util.List;
import java.util.Objects;

public record EvaluatedHand(HandRank rank, List<Card> cards) implements Comparable<EvaluatedHand> {

    public EvaluatedHand {
        Objects.requireNonNull(rank, "rank must not be null");
        Objects.requireNonNull(cards, "cards must not be null");
        cards = List.copyOf(cards);
    }

    public HandCategory category() {
        return rank.category();
    }

    @Override
    public int compareTo(EvaluatedHand other) {
        return this.rank.compareTo(other.rank);
    }

    @Override
    public String toString() {
        return rank.category().displayName() + " using " + cards;
    }
}
