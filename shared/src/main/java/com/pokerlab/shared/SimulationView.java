package com.pokerlab.shared;

import com.pokerlab.core.batch.SimulationConfiguration;
import java.time.Instant;
import java.util.UUID;

public record SimulationView(
        UUID simulationId,
        SimulationStatus status,
        SimulationConfiguration configuration,
        long completedIterations,
        long requestedIterations,
        int completedBatches,
        int totalBatches,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage,
        long version) {}
