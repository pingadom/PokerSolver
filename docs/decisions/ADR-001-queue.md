# ADR 001: SQS with a durable development transport

Accepted. AWS SQS standard queues provide at-least-once delivery and a managed dead-letter queue. LocalStack runs the same SQS adapter under Docker Compose. `QUEUE_MODE=postgres` offers a clearly separate development transport for machines without Docker; it is not an SQS emulator or the production deployment path.

Each message includes schema version 1, simulation ID, batch ID, validated scenario, iteration count and derived seed. SQS receive uses ten-second long polling and a 120-second visibility timeout, renewed every thirty seconds during computation. Five receives permit transient failures to recover while bounding repeated expensive failures. Terraform and local queue setup use the same receive limit. Size limits keep each message well below SQS limits.

The worker acknowledges only after durable processing. A dead-letter consumer records terminal failure before acknowledging a valid exhausted batch. Unparseable poison messages remain in the DLQ for operator inspection and retention expiry, are counted and logged without raw payloads, and trigger queue alarms. A message whose identifiers cannot be recovered cannot safely be mapped to a simulation; inspect the DLQ and durable batch rows before manual remediation. Mismatched configurations are likewise retained rather than failing an unrelated simulation.

The PostgreSQL development transport persists messages, leases one with `FOR UPDATE SKIP LOCKED`, rotates receipts, retries expired leases, and selects messages with five attempts for dead-letter handling. Handled dead letters remain in its table for inspection. This permits actual API and worker processes to run separately with restart-safe jobs while preserving the production transport abstraction.

Sources: [SQS at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html).
