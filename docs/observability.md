# Observability and operations

Both services write Spring Boot ECS-format JSON logs suitable for CloudWatch ingestion. The API echoes a sanitised `X-Request-ID` (or generates a UUID) and includes it in the logging context. Worker execution attaches `simulationId` and `batchId`; these IDs bridge the HTTP submission and asynchronous lifecycle without propagating arbitrary user data into queue logs.

Actuator exposes `/actuator/health`, `/actuator/info` and `/actuator/metrics`. Query `/actuator/metrics/<metric-name>` for values. Lifecycle function counters query committed database rows, so rollbacks are not mistaken for successful submissions. In a multi-replica deployment these are global counts: do not sum them across instances.

- `pokerlab.simulations.submitted`, `.completed`, `.failed`: durable lifecycle totals.
- `pokerlab.simulations.active`, `pokerlab.outbox.pending`: gauges for stuck processing/publication.
- `pokerlab.batches.processed`, `.failed`, `.retried`: process-local execution/delivery events.
- `pokerlab.batch.execution`: batch timing, count and total time.
- `pokerlab.iterations.processed`: derive throughput from its rate.
- `pokerlab.cache.hit`, `.miss`, `.error`: cache effectiveness and fallback.
- `pokerlab.deadletters.handled`, `.poison`: terminal processing and malformed messages.

AWS additionally supplies SQS queue depth/age metrics. Terraform creates alarms for visible dead letters, oldest-message age over ten minutes for five periods, and repeated ALB 5xx responses. Set `alarm_actions` to an existing SNS topic to receive alerts; an empty list creates dashboard-visible alarms without notifications. ECS Container Insights is enabled and billable. Custom application metrics are available through Actuator; automatic custom metric export to CloudWatch is not configured.

If progress stalls, inspect outbox pending count, queue age, worker logs and batch attempts in that order. Never manually increment progress. Retry a transient outage by restoring its dependency; redrive of a failed simulation does not revive a terminal record. Submit a new request with the recorded scenario and seed after fixing the underlying problem. Inspect malformed DLQ bodies privately and do not paste credentials or raw untrusted payloads into public logs.

API and worker shutdown are graceful. Interrupted workers leave unacknowledged queue messages for another worker after their lease expires. Redis remains optional; PostgreSQL readiness is essential.
