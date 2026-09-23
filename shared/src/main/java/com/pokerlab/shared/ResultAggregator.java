package com.pokerlab.shared;

import com.pokerlab.core.batch.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every mutation locks the simulation first, establishing one consistent lock order. */
@Service
public class ResultAggregator {
    private final JdbcTemplate jdbc;
    private final SimulationStore store;
    private final JsonCodec json;

    public ResultAggregator(JdbcTemplate jdbc, SimulationStore store, JsonCodec json) {
        this.jdbc = jdbc;
        this.store = store;
        this.json = json;
    }

    private SimulationView lock(UUID id) {
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM simulations WHERE id=? FOR UPDATE", UUID.class, id);
        if (ids.isEmpty()) throw new SimulationNotFoundException(id);
        return store.get(id);
    }

    private void validateJob(BatchJob job, SimulationView view) {
        if (!view.configuration().equals(job.configuration()))
            throw new IllegalArgumentException(
                    "Message configuration differs from durable request");
    }

    @Transactional
    public boolean start(BatchJob job) {
        var view = lock(job.simulationId());
        validateJob(job, view);
        if (view.status().terminal()) return false;
        int changed =
                jdbc.update(
                        "UPDATE simulation_batches SET status='RUNNING',attempts=attempts+1,started_at=coalesce(started_at,now()) WHERE simulation_id=? AND batch_id=? AND status <> 'COMPLETED'",
                        job.simulationId(),
                        job.batchId());
        if (changed == 0) return false;
        jdbc.update(
                "UPDATE simulations SET status='RUNNING',updated_at=now(),version=version+1 WHERE id=?",
                job.simulationId());
        return true;
    }

    @Transactional
    public boolean submit(BatchJob job, BatchResult result) {
        var view = lock(job.simulationId());
        validateJob(job, view);
        if (!job.simulationId().equals(result.simulationId())
                || job.batchId() != result.batchId()
                || job.iterationCount() != result.trials()
                || !result.players().stream()
                        .map(PlayerResult::name)
                        .toList()
                        .equals(
                                job.configuration().players().stream()
                                        .map(p -> p.playerName())
                                        .toList()))
            throw new IllegalArgumentException("Result does not match batch");
        if (view.status().terminal()) return false;
        int inserted =
                jdbc.update(
                        "INSERT INTO batch_results(simulation_id,batch_id,payload,trials,elapsed_ms) VALUES (?,?,?::jsonb,?,?) ON CONFLICT (simulation_id,batch_id) DO NOTHING",
                        result.simulationId(),
                        result.batchId(),
                        json.write(result),
                        result.trials(),
                        result.elapsedMs());
        if (inserted == 0) return false;
        jdbc.update(
                "UPDATE simulation_batches SET status='COMPLETED',completed_at=now() WHERE simulation_id=? AND batch_id=?",
                job.simulationId(),
                job.batchId());
        jdbc.update(
                "UPDATE simulations SET status='RUNNING',completed_iterations=completed_iterations+?,completed_batches=completed_batches+1,updated_at=now(),version=version+1 WHERE id=?",
                result.trials(),
                job.simulationId());
        if (view.completedBatches() + 1 == view.totalBatches()) finalise(view);
        return true;
    }

    private void finalise(SimulationView view) {
        var batches =
                jdbc.query(
                        "SELECT payload::text FROM batch_results WHERE simulation_id=? ORDER BY batch_id",
                        (rs, n) -> json.read(rs.getString(1), BatchResult.class),
                        view.simulationId());
        long trials = batches.stream().mapToLong(BatchResult::trials).sum();
        if (trials != view.requestedIterations())
            throw new IllegalStateException("Final trial count mismatch");
        List<PlayerResult> players = new ArrayList<>();
        for (int index = 0; index < view.configuration().players().size(); index++) {
            long wins = 0, ties = 0, losses = 0;
            double shares = 0;
            for (var batch : batches) {
                var player = batch.players().get(index);
                wins += player.wins();
                ties += player.ties();
                losses += player.losses();
                shares += player.equityShares();
            }
            players.add(
                    new PlayerResult(
                            view.configuration().players().get(index).playerName(),
                            wins,
                            ties,
                            losses,
                            shares));
        }
        long elapsed = Math.max(0, Duration.between(view.createdAt(), Instant.now()).toMillis());
        var result =
                new AggregatedSimulationResult(
                        view.simulationId(), SimulationStatus.COMPLETED, trials, players, elapsed);
        jdbc.update(
                "INSERT INTO simulation_results(simulation_id,payload,total_trials,elapsed_ms) VALUES (?,?::jsonb,?,?)",
                view.simulationId(),
                json.write(result),
                trials,
                elapsed);
        jdbc.update(
                "UPDATE simulations SET status='COMPLETED',updated_at=now(),version=version+1 WHERE id=?",
                view.simulationId());
    }

    @Transactional
    public void fail(BatchJob job, String reason) {
        var view = lock(job.simulationId());
        validateJob(job, view);
        if (view.status().terminal()) return;
        var status =
                jdbc.queryForObject(
                        "SELECT status FROM simulation_batches WHERE simulation_id=? AND batch_id=?",
                        String.class,
                        job.simulationId(),
                        job.batchId());
        if ("COMPLETED".equals(status)) return;
        jdbc.update(
                "UPDATE simulation_batches SET status='FAILED',completed_at=now() WHERE simulation_id=? AND batch_id=?",
                job.simulationId(),
                job.batchId());
        jdbc.update(
                "UPDATE simulations SET status='FAILED',error_message=?,updated_at=now(),version=version+1 WHERE id=?",
                reason,
                job.simulationId());
    }
}
