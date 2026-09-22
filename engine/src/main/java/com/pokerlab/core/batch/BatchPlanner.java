package com.pokerlab.core.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BatchPlanner {
    private BatchPlanner() {}

    public static List<BatchJob> plan(UUID simulationId, SimulationConfiguration configuration) {
        List<BatchJob> jobs = new ArrayList<>();
        for (long offset = 0;
                offset < configuration.iterations();
                offset += configuration.batchSize()) {
            int batchId = jobs.size();
            jobs.add(
                    new BatchJob(
                            1,
                            simulationId,
                            batchId,
                            configuration,
                            (int)
                                    Math.min(
                                            configuration.batchSize(),
                                            configuration.iterations() - offset),
                            deriveSeed(configuration.seed(), batchId)));
        }
        return List.copyOf(jobs);
    }

    /** SplitMix64 bijective mixing: distinct batch indices yield distinct 64-bit seeds. */
    public static long deriveSeed(long parentSeed, int batchId) {
        if (batchId < 0) throw new IllegalArgumentException("batchId must be nonnegative");
        long value = parentSeed + 0x9e3779b97f4a7c15L * (batchId + 1L);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
