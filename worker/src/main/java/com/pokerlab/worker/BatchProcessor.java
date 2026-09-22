package com.pokerlab.worker;

import com.pokerlab.core.batch.*;
import com.pokerlab.shared.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class BatchProcessor {
    private final ResultAggregator aggregator;
    private final JobQueue queue;
    private final JsonCodec json;
    private final MeterRegistry metrics;

    public BatchProcessor(
            ResultAggregator aggregator, JobQueue queue, JsonCodec json, MeterRegistry metrics) {
        this.aggregator = aggregator;
        this.queue = queue;
        this.json = json;
        this.metrics = metrics;
    }

    public void process(JobQueue.Delivery delivery) {
        try (var heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            var job = json.read(delivery.body(), BatchJob.class);
            MDC.put("simulationId", job.simulationId().toString());
            MDC.put("batchId", Integer.toString(job.batchId()));
            if (delivery.receiveCount() > 1)
                metrics.counter("pokerlab.batches.retried").increment();
            if (aggregator.start(job)) {
                heartbeat.scheduleAtFixedRate(
                        () -> {
                            try {
                                queue.extend(delivery);
                            } catch (Exception e) {
                                LoggerFactory.getLogger(getClass())
                                        .warn(
                                                "Visibility extension failed; duplicate computation remains safe",
                                                e);
                            }
                        },
                        30,
                        30,
                        TimeUnit.SECONDS);
                var result =
                        metrics.timer("pokerlab.batch.execution")
                                .record(() -> new BatchExecutor().execute(job));
                if (aggregator.submit(job, result)) {
                    metrics.counter("pokerlab.batches.processed").increment();
                    metrics.counter("pokerlab.iterations.processed").increment(result.trials());
                }
            }
            queue.acknowledge(delivery);
            LoggerFactory.getLogger(getClass()).info("Batch delivery acknowledged");
            heartbeat.shutdownNow();
        } catch (Exception error) {
            metrics.counter("pokerlab.batches.failed").increment();
            LoggerFactory.getLogger(getClass())
                    .warn("Batch delivery failed; left unacknowledged for retry/redrive", error);
        } finally {
            MDC.remove("simulationId");
            MDC.remove("batchId");
        }
    }

    public void deadLetter(JobQueue.Delivery delivery) {
        BatchJob job;
        try {
            job = json.read(delivery.body(), BatchJob.class);
        } catch (IllegalArgumentException invalid) {
            // Keep poison messages in the DLQ for inspection until retention expires.
            metrics.counter("pokerlab.deadletters.poison").increment();
            LoggerFactory.getLogger(getClass())
                    .error(
                            "Unparseable dead letter retained; inspect DLQ without logging raw payload");
            return;
        }
        try {
            aggregator.fail(
                    job, "Batch exhausted five delivery attempts; inspect worker logs and DLQ");
            queue.acknowledgeDeadLetter(delivery);
            metrics.counter("pokerlab.deadletters.handled").increment();
        } catch (Exception error) {
            LoggerFactory.getLogger(getClass())
                    .error("Dead-letter failure recording will retry", error);
        }
    }
}
