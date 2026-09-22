package com.pokerlab.shared;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.batch.*;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = PostgresLifecycleTest.TestApplication.class)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostgresLifecycleTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.pokerlab.shared")
    static class TestApplication {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "pokerlab"));
        registry.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "pokerlab"));
    }

    @Autowired SimulationStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonCodec json;
    @Autowired ResultAggregator aggregator;
    @Autowired OutboxDispatcher dispatcher;

    @Test
    void duplicateAndConcurrentFinalBatchesCountExactlyOnce() throws Exception {
        var config = configuration();
        var id = store.create(config);
        var jobs = BatchPlanner.plan(id, config);
        var executor = new BatchExecutor();
        var results = jobs.stream().map(executor::execute).toList();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int delivery = 0; delivery < 6; delivery++) {
                int index = delivery % jobs.size();
                futures.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    return aggregator.submit(jobs.get(index), results.get(index));
                                }));
            }
            gate.countDown();
            int accepted = 0;
            for (var future : futures)
                if (future.get(15, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertEquals(3, accepted);
        }
        assertEquals(SimulationStatus.COMPLETED, store.get(id).status());
        assertEquals(201, store.get(id).completedIterations());
        assertEquals(3, store.get(id).completedBatches());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM simulation_results WHERE simulation_id=?",
                        Integer.class,
                        id));
        var finalResult = store.result(id).orElseThrow();
        assertEquals(201, finalResult.totalTrials());
        assertEquals(
                1, finalResult.players().stream().mapToDouble(PlayerResult::equity).sum(), 1e-12);
        assertFalse(aggregator.submit(jobs.getFirst(), results.getFirst()));
        aggregator.fail(jobs.getFirst(), "late dead letter");
        assertEquals(SimulationStatus.COMPLETED, store.get(id).status());
    }

    @Test
    void outOfOrderCompletionAndRetryAreSafe() {
        var config = configuration();
        var id = store.create(config);
        var jobs = BatchPlanner.plan(id, config);
        assertTrue(aggregator.start(jobs.getFirst()));
        assertTrue(aggregator.start(jobs.getFirst()));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT attempts FROM simulation_batches WHERE simulation_id=? AND batch_id=0",
                        Integer.class,
                        id));
        assertEquals(SimulationStatus.RUNNING, store.get(id).status());
        var executor = new BatchExecutor();
        for (var job : jobs.reversed()) assertTrue(aggregator.submit(job, executor.execute(job)));
        assertEquals(SimulationStatus.COMPLETED, store.get(id).status());
        assertFalse(aggregator.start(jobs.getFirst()));
    }

    @Test
    void terminalFailureCannotBeRevived() {
        var id = store.create(configuration());
        var job = BatchPlanner.plan(id, configuration()).getFirst();
        aggregator.fail(job, "Retry budget exhausted");
        assertEquals(SimulationStatus.FAILED, store.get(id).status());
        assertFalse(aggregator.submit(job, new BatchExecutor().execute(job)));
        assertTrue(store.result(id).isEmpty());
    }

    @Test
    void failedPublicationRollsBackAndCanBeRetried() {
        var id = store.create(configuration());
        var queue = org.mockito.Mockito.mock(JobQueue.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("transient queue outage"))
                .when(queue)
                .send(org.mockito.ArgumentMatchers.any());
        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(queue));
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_outbox WHERE simulation_id=? AND published_at IS NULL",
                        Integer.class,
                        id));
        org.mockito.Mockito.doNothing().when(queue).send(org.mockito.ArgumentMatchers.any());
        int rounds = 0;
        while (dispatcher.dispatch(queue) > 0) assertTrue(rounds++ < 100);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_outbox WHERE simulation_id=? AND published_at IS NULL",
                        Integer.class,
                        id));
    }

    static SimulationConfiguration configuration() {
        return new SimulationConfiguration(
                List.of(
                        new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                        new PlayerHand("KK", Card.parse("KS"), Card.parse("KH"))),
                List.of(),
                201,
                100,
                42);
    }

    @Test
    void persistsScenarioAndOutboxAtomically() {
        var config = configuration();
        var id = store.create(config);
        var view = store.get(id);
        assertEquals(SimulationStatus.QUEUED, view.status());
        assertEquals(config, view.configuration());
        assertEquals(3, view.totalBatches());
        assertEquals(0, view.completedIterations());
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_outbox WHERE simulation_id=?",
                        Integer.class,
                        id));
        assertEquals(
                201,
                jdbc.queryForObject(
                        "SELECT sum(iterations) FROM simulation_batches WHERE simulation_id=?",
                        Long.class,
                        id));
        assertTrue(store.result(id).isEmpty());
        var message =
                jdbc.queryForObject(
                        "SELECT payload::text FROM batch_outbox WHERE simulation_id=? AND batch_id=0",
                        String.class,
                        id);
        assertEquals(BatchPlanner.plan(id, config).getFirst(), json.read(message, BatchJob.class));
    }
}
