package com.pokerlab.shared;

import com.pokerlab.core.batch.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SimulationStore {
    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public SimulationStore(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public UUID create(SimulationConfiguration configuration) {
        UUID id = UUID.randomUUID();
        var jobs = BatchPlanner.plan(id, configuration);
        jdbc.update(
                "INSERT INTO simulations(id,status,configuration,requested_iterations,seed,batch_size,total_batches) VALUES (?,'QUEUED',?::jsonb,?,?,?,?)",
                id,
                json.write(configuration),
                configuration.iterations(),
                configuration.seed(),
                configuration.batchSize(),
                jobs.size());
        for (var job : jobs) {
            jdbc.update(
                    "INSERT INTO simulation_batches(simulation_id,batch_id,iterations,seed) VALUES (?,?,?,?)",
                    id,
                    job.batchId(),
                    job.iterationCount(),
                    job.seed());
            jdbc.update(
                    "INSERT INTO batch_outbox(simulation_id,batch_id,payload) VALUES (?,?,?::jsonb)",
                    id,
                    job.batchId(),
                    json.write(job));
        }
        return id;
    }

    public SimulationView get(UUID id) {
        return jdbc.query("SELECT * FROM simulations WHERE id=?", this::mapView, id).stream()
                .findFirst()
                .orElseThrow(() -> new SimulationNotFoundException(id));
    }

    public List<SimulationView> recent(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid pagination");
        return jdbc.query(
                "SELECT * FROM simulations ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                this::mapView,
                limit,
                offset);
    }

    public Optional<AggregatedSimulationResult> result(UUID id) {
        get(id);
        return jdbc
                .query(
                        "SELECT payload::text FROM simulation_results WHERE simulation_id=?",
                        (rs, n) -> json.read(rs.getString(1), AggregatedSimulationResult.class),
                        id)
                .stream()
                .findFirst();
    }

    private SimulationView mapView(ResultSet row, int index) throws SQLException {
        return new SimulationView(
                row.getObject("id", UUID.class),
                SimulationStatus.valueOf(row.getString("status")),
                json.read(row.getString("configuration"), SimulationConfiguration.class),
                row.getLong("completed_iterations"),
                row.getLong("requested_iterations"),
                row.getInt("completed_batches"),
                row.getInt("total_batches"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getString("error_message"),
                row.getLong("version"));
    }
}
