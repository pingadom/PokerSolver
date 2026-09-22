package com.pokerlab.core.batch;

import com.pokerlab.core.simulation.*;
import java.util.OptionalLong;

public final class BatchExecutor {
    public BatchResult execute(BatchJob job) {
        var result =
                MonteCarloSimulation.run(
                        SimulationRequest.quickWithSeed(
                                job.configuration().players(),
                                job.configuration().board(),
                                job.iterationCount(),
                                OptionalLong.of(job.seed())));
        var players =
                job.configuration().players().stream()
                        .map(
                                player -> {
                                    var name = player.playerName();
                                    return new PlayerResult(
                                            name,
                                            result.wins(name),
                                            result.ties(name),
                                            result.losses(name),
                                            result.equityShare(name));
                                })
                        .toList();
        return new BatchResult(
                job.simulationId(),
                job.batchId(),
                job.iterationCount(),
                players,
                result.runtimeMillis());
    }
}
