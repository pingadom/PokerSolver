package com.pokerlab.api;

import com.pokerlab.shared.*;
import java.time.Instant;
import java.util.*;

/** Explicit public DTO; decimal strings preserve all 64 seed bits in JavaScript clients. */
public record SimulationStatusResponse(
        UUID simulationId,
        SimulationStatus status,
        Configuration configuration,
        long completedIterations,
        long requestedIterations,
        int completedBatches,
        int totalBatches,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage,
        long version) {
    public record Player(String name, List<String> cards) {}

    public record Configuration(
            List<Player> players,
            List<String> board,
            long iterations,
            int batchSize,
            String seed) {}

    public static SimulationStatusResponse from(SimulationView view) {
        var source = view.configuration();
        var config =
                new Configuration(
                        source.players().stream()
                                .map(
                                        p ->
                                                new Player(
                                                        p.playerName(),
                                                        p.cards().stream()
                                                                .map(c -> c.compact())
                                                                .toList()))
                                .toList(),
                        source.board().stream().map(c -> c.compact()).toList(),
                        source.iterations(),
                        source.batchSize(),
                        Long.toString(source.seed()));
        return new SimulationStatusResponse(
                view.simulationId(),
                view.status(),
                config,
                view.completedIterations(),
                view.requestedIterations(),
                view.completedBatches(),
                view.totalBatches(),
                view.createdAt(),
                view.updatedAt(),
                view.errorMessage(),
                view.version());
    }
}
