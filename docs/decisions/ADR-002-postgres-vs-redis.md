# ADR 002: PostgreSQL authority, optional Redis cache

Accepted. PostgreSQL owns simulations, batch identities, attempts, progress, outbox delivery and results. Relational constraints and transaction boundaries protect correctness. JSONB holds small immutable scenarios and result payloads; searchable lifecycle fields, counts, timestamps and identifiers remain typed columns.

Redis serves a justified ephemeral key/value workload. `pokerlab:v1:status:<uuid>` caches a status snapshot for two seconds; `pokerlab:v1:result:<uuid>` caches immutable completed results for one hour. Expiration is the invalidation strategy. A concurrent reader may populate an older status snapshot after a completion, delaying its visibility by at most the short TTL. The results endpoint checks durable terminal state before consulting the result cache. Cache values never drive writes or aggregation.

Redis connection and command timeouts are 200 ms. Misses, flushes, corrupt entries and outages fall back to PostgreSQL. Cache errors/hits/misses are metered. Optional Redis does not make the database-backed readiness check unhealthy. Use `CACHE_ENABLED=false` for a lower-cost deployment without Redis; Terraform offers an opt-in private ElastiCache instance.

Indexes serve recent-history ordering, composite batch identity, and pending outbox scanning. Result identity is unique per simulation. Separate relational counters make progress queries cheap without parsing each batch payload.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`77accdef878a5fb2cfb4c672f716e30f67da7ec6` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared 77accde”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
