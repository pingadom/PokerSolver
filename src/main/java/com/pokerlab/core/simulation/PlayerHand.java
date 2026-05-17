package com.pokerlab.core.simulation;

import com.pokerlab.core.card.Card;

import java.util.List;
import java.util.Objects;

public record PlayerHand(
        String playerName,
        Card card1,
        Card card2
) {
    public PlayerHand {
        Objects.requireNonNull(playerName, "playerName cannot be null");
        Objects.requireNonNull(card1, "card1 cannot be null");
        Objects.requireNonNull(card2, "card2 cannot be null");

        if (playerName.isBlank()) {
            throw new IllegalArgumentException("playerName cannot be blank");
        }

        if (card1.equals(card2)) {
            throw new IllegalArgumentException("A player cannot have duplicate hole cards");
        }
    }

    public List<Card> cards() {
        return List.of(card1, card2);
    }

    @Override
    public String toString() {
        return playerName + ": " + card1 + " " + card2;
    }
}