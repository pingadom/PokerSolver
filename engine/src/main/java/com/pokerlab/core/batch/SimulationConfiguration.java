package com.pokerlab.core.batch;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.*;
import java.util.List;
import java.util.OptionalLong;

/** Validated transport-friendly scenario. The chosen seed is always persisted. */
public record SimulationConfiguration(
        List<PlayerHand> players, List<Card> board, long iterations, int batchSize, long seed) {
    public static final long MAX_ITERATIONS = 100_000_000L;

    public SimulationConfiguration {
        players = List.copyOf(players);
        board = List.copyOf(board);
        if (iterations < 1 || iterations > MAX_ITERATIONS)
            throw new IllegalArgumentException("iterations must be between 1 and 100000000");
        if (batchSize < 1 || batchSize > 1_000_000)
            throw new IllegalArgumentException("batchSize must be between 1 and 1000000");
        if ((iterations - 1) / batchSize + 1 > 10_000)
            throw new IllegalArgumentException("at most 10000 batches are allowed");
        SimulationRequest.quickWithSeed(players, board, 1, OptionalLong.of(seed));
    }
}
