package com.pokerlab.shared;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lifecycle counts reflect committed rows, not events that could later roll back. */
@Component
public class LifecycleMetrics {
    private final JdbcTemplate jdbc;

    public LifecycleMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        FunctionCounter.builder(
                        "pokerlab.simulations.submitted",
                        this,
                        self -> self.count("SELECT count(*) FROM simulations"))
                .register(registry);
        FunctionCounter.builder(
                        "pokerlab.simulations.completed",
                        this,
                        self ->
                                self.count(
                                        "SELECT count(*) FROM simulations WHERE status='COMPLETED'"))
                .register(registry);
        FunctionCounter.builder(
                        "pokerlab.simulations.failed",
                        this,
                        self ->
                                self.count(
                                        "SELECT count(*) FROM simulations WHERE status='FAILED'"))
                .register(registry);
        Gauge.builder(
                        "pokerlab.simulations.active",
                        this,
                        self ->
                                self.count(
                                        "SELECT count(*) FROM simulations WHERE status IN ('QUEUED','RUNNING')"))
                .register(registry);
        Gauge.builder(
                        "pokerlab.outbox.pending",
                        this,
                        self ->
                                self.count(
                                        "SELECT count(*) FROM batch_outbox WHERE published_at IS NULL"))
                .register(registry);
    }

    private double count(String sql) {
        return jdbc.queryForObject(sql, Long.class).doubleValue();
    }
}
