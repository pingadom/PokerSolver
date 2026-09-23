package com.pokerlab.core.batch;

import java.util.Objects;
import java.util.UUID;

public record BatchJob(
        int schemaVersion,
        UUID simulationId,
        int batchId,
        SimulationConfiguration configuration,
        int iterationCount,
        long seed) {
    public BatchJob {
        Objects.requireNonNull(simulationId);
        Objects.requireNonNull(configuration);
        if (schemaVersion != 1) throw new IllegalArgumentException("unsupported batch schema");
        long count = (configuration.iterations() - 1) / configuration.batchSize() + 1;
        if (batchId < 0 || batchId >= count) throw new IllegalArgumentException("invalid batchId");
        long expected =
                Math.min(
                        configuration.batchSize(),
                        configuration.iterations() - (long) batchId * configuration.batchSize());
        if (iterationCount != expected
                || seed != BatchPlanner.deriveSeed(configuration.seed(), batchId))
            throw new IllegalArgumentException("batch does not match its configuration");
    }
}
