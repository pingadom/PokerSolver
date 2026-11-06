# Changelog

## 1.0.0 — 22 September 2026

- Extracted the existing evaluator and CLI into a Java 21 engine module; added validated seeded batch contracts and complete win/tie/loss/equity reporting.
- Added the API, standalone workers, durable PostgreSQL lifecycle/outbox, SQS redrive and idempotent aggregation, with optional Redis caching.
- Added a React workspace, Docker/LocalStack development stack, restricted AWS Terraform and CI/deployment templates.
- Verified concurrency, retries, cache outages, full 64-bit seed transport and every five-card category frequency; recorded measured 1/2/4/8-thread scaling.

Cloud provisioning and deployment are manual. Authentication, ranges, cancellation endpoints, automatic scaling and exports are not part of this release.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`f689eecd3bac03d51079bdec55f0d9ca1067c1f0` and cleared PR creation on
2026-11-06 (Europe/London), with “cleared f689eec”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
