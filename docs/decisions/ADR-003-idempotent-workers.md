# ADR 003: Transactional outbox and idempotent result submission

Accepted. SQS standard delivery is at least once. Exactly-once computation is neither assumed nor required.

Creating the simulation, its batches and outbox messages uses one PostgreSQL transaction. A dispatcher selects pending outbox rows with `FOR UPDATE SKIP LOCKED`, publishes them, then marks them published in the same transaction. A crash after send causes a duplicate on retry. A failed send rolls back the marks. Network calls are bounded and publication transactions process only ten messages.

Workers compute outside database transactions. All lifecycle changes acquire the simulation row lock before changing batch rows. A result insert has a primary key on `(simulation_id,batch_id)` and `ON CONFLICT DO NOTHING`. Only successful inserts increment progress. The final successful insert sums durable batch results in batch order, writes the unique final result and completes the simulation in that transaction. Duplicate and concurrent final deliveries cannot double count or finalise twice. Unrelated simulations retain concurrency; completion writes within one simulation are intentionally serialised.

Message scenarios and results are checked against the durable configuration. Batches that already completed are not failed by late dead-letter deliveries. Once a simulation is terminal, subsequent results cannot revive it. Failure recording is retriable and must commit before a dead-letter message is acknowledged.

Equity aggregation sums fractional pot counts and divides by total trials. The batch ordering keeps floating-point addition reproducible. End-to-end elapsed time includes queue and database time; batch elapsed time measures computation separately.

Trade-off: finalisation currently reads all batch results (bounded to ten thousand). This is simple to audit; incremental SQL counters can replace it if measured workloads justify the complexity.
