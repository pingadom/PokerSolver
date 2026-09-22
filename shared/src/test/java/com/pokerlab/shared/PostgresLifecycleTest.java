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
