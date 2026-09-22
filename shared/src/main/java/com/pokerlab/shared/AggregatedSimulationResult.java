package com.pokerlab.shared;

import com.pokerlab.core.batch.PlayerResult;
import java.util.List;
import java.util.UUID;

public record AggregatedSimulationResult(
        UUID simulationId,
        SimulationStatus status,
        long totalTrials,
        List<PlayerResult> players,
        long elapsedMs) {
    public AggregatedSimulationResult {
        players = List.copyOf(players);
    }
}
