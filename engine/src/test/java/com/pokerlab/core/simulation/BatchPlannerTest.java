package com.pokerlab.core.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.batch.*;
import com.pokerlab.core.card.Card;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchPlannerTest {
    private SimulationConfiguration config(long trials, int batchSize) {
        return new SimulationConfiguration(
                List.of(
                        new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                        new PlayerHand("KK", Card.parse("KS"), Card.parse("KH"))),
                List.of(),
                trials,
                batchSize,
                42);
    }

    @Test
    void partitionsRemainderAndReproducesSeeds() {
        var id = UUID.randomUUID();
        var jobs = BatchPlanner.plan(id, config(1001, 100));
        assertEquals(11, jobs.size());
        assertEquals(1, jobs.getLast().iterationCount());
        assertEquals(1001, jobs.stream().mapToInt(BatchJob::iterationCount).sum());
        assertEquals(jobs, BatchPlanner.plan(id, config(1001, 100)));
        assertEquals(11, jobs.stream().map(BatchJob::seed).distinct().count());
    }

    @Test
    void primaryDemoCreatesOneHundredBatches() {
        assertEquals(100, BatchPlanner.plan(UUID.randomUUID(), config(10_000_000, 100_000)).size());
    }

    @Test
    void repeatsBatchExactlyApartFromElapsedTime() {
        var job = BatchPlanner.plan(UUID.randomUUID(), config(1000, 1000)).getFirst();
        var executor = new BatchExecutor();
        var first = executor.execute(job);
        var second = executor.execute(job);
        assertEquals(first.players(), second.players());
        assertEquals(1000, first.trials());
    }

    @Test
    void rejectsTamperedMessageAndResourceAbuse() {
        assertThrows(IllegalArgumentException.class, () -> config(100_000_001, 100_000));
        assertThrows(IllegalArgumentException.class, () -> config(100_000_000, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BatchJob(2, UUID.randomUUID(), 0, config(100, 100), 100, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BatchJob(1, UUID.randomUUID(), 0, config(100, 100), 99, 42));
    }
}
