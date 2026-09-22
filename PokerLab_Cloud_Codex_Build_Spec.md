# PokerLab Cloud - Codex Full Build Specification
**Distributed Monte Carlo Poker Simulation & Analytics Platform**

## 1. Purpose and Codex operating instructions

Build the project described in this document end-to-end as a production-style undergraduate portfolio project. The aim is not merely to make a poker equity calculator; the finished repository must demonstrate distributed systems, multi-tier architecture, algorithms, relational and NoSQL databases, AWS, testing, CI/CD, observability, version control, technical documentation, and disciplined engineering practice.

Codex should work autonomously through the phases below. Do not stop for minor implementation decisions that can be resolved sensibly from this specification. Prefer simple, robust choices over unnecessary complexity. Keep the project runnable after every phase.

MANDATORY GIT WORKFLOW:
- Initialise Git immediately if the repository is not already a Git repository.
- Work on a feature branch such as feature/pokerlab-cloud rather than making all work directly on main.
- Make frequent, meaningful commits throughout development. Do not produce one giant final commit.
- Commit after each coherent unit of work and whenever the project reaches a stable milestone.
- Target roughly 20-40 commits over the full build if starting from scratch, depending on existing code.
- Use Conventional Commit style where practical: feat:, fix:, refactor:, test:, docs:, chore:, ci:, perf:.
- Example commits: "feat(engine): add deterministic Monte Carlo simulation service", "feat(queue): add SQS-backed simulation job dispatch", "test(worker): cover duplicate batch processing", "docs(architecture): document idempotent aggregation design".
- Before every commit: run the relevant formatter, tests, and build for the files changed. Do not knowingly commit a broken build.
- Never commit secrets, credentials, .env files containing secrets, AWS keys, generated build directories, IDE caches, or local database data.
- Maintain a useful .gitignore from the first commit.
- Prefer small PR-sized changes. If GitHub access is available, open pull requests for major phases; if not, structure commits as if each phase could be reviewed as a PR.
- Tag a polished final release, for example v1.0.0, only after all acceptance criteria pass.

## 2. Product definition

PokerLab Cloud is a web-based distributed poker simulation platform. A user submits a poker scenario (hole cards, optional community cards, number of players/ranges where supported, and simulation count). The API validates and stores the request, partitions the requested simulation into independent batches, sends those batches to a job queue, multiple workers process batches concurrently, and the system aggregates the results into a final equity estimate.

The system must support local development with Docker Compose and a cloud deployment path on AWS. It should be engineered as a credible production-style service rather than as a toy demo.

Primary demonstration scenario: AA vs KK vs QQ over 10,000,000 Monte Carlo trials, partitioned into many independent batches and processed concurrently.

## 3. High-level architecture

Required logical components:
1. Frontend: React + TypeScript single-page application.
2. API: Java 21 + Spring Boot REST service.
3. Core engine: Java library containing card models, hand evaluator integration/implementation, deterministic Monte Carlo simulation, random seed support, and batch execution.
4. Relational persistence: PostgreSQL for users if authentication is included, simulation requests, batches, status, timestamps, configuration, and durable metadata.
5. Queue: AWS SQS in cloud. For local development use LocalStack SQS or a clearly abstracted in-memory/dev implementation.
6. Worker service: Java/Spring Boot or lightweight Java service consuming queue messages and executing simulation batches.
7. NoSQL/cache: Redis for fast result/status caching and aggregation assistance; optionally DynamoDB can be implemented as an additional cloud path, but Redis alone is acceptable if used for a justified key/value workload and PostgreSQL remains the durable source of truth.
8. Object storage: S3 for large exported result files / benchmark artefacts if implemented.
9. Observability: structured logs plus CloudWatch-compatible metrics; local metrics should be viewable through Spring Actuator and optionally Prometheus/Grafana.
10. Infrastructure as Code: Terraform for AWS resources.
11. CI/CD: GitHub Actions.

Expected request flow:
React UI -> Spring Boot API -> PostgreSQL -> batch planner -> SQS -> N workers -> result aggregation -> PostgreSQL/Redis -> API -> UI.

## 4. Technology choices

Use these defaults unless the existing repository provides a strong reason to preserve an equivalent technology:
- Java 21
- Maven multi-module project
- Spring Boot 3.x
- PostgreSQL 16+
- Redis 7+
- React 18+ with TypeScript and Vite
- Docker and Docker Compose
- AWS: SQS, RDS PostgreSQL, ElastiCache Redis where practical, ECS Fargate for API/workers, S3, CloudWatch, IAM, ECR; Application Load Balancer if exposing the API publicly
- Terraform for infrastructure
- GitHub Actions for CI and deployment workflow templates
- OpenAPI/Swagger for REST documentation
- JUnit 5, Mockito where appropriate, Testcontainers for integration tests
- Flyway for SQL schema migrations
- Jackson for JSON
- Micrometer/Spring Actuator for metrics

Avoid adding technologies only for keyword coverage. Every dependency should have a clear role documented in an Architecture Decision Record (ADR) where the choice is non-obvious.

## 5. Repository structure

Prefer a monorepo with a structure close to:

pokerlab-cloud/
  README.md
  pom.xml
  .gitignore
  .editorconfig
  docker-compose.yml
  .env.example
  engine/
  api/
  worker/
  shared/
  frontend/
  infrastructure/
    terraform/
  docs/
    architecture.md
    local-development.md
    aws-deployment.md
    benchmarks.md
    ai-development.md
    decisions/
  scripts/
  .github/workflows/

If an existing PokerLab codebase is present, preserve useful functionality and refactor it into engine/ rather than rewriting everything unnecessarily.

## 6. Core domain model

At minimum define clear immutable or strongly validated models for:
- Card (rank, suit)
- HoleCards / PlayerHand
- CommunityBoard
- SimulationRequest
- SimulationConfiguration
- SimulationJob / BatchJob
- BatchResult
- AggregatedSimulationResult
- SimulationStatus: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED

A SimulationRequest must support at least:
- 2-9 players with exact hole cards for the first release
- zero to five community cards
- number of iterations
- optional deterministic seed

Validation must prevent duplicate cards and impossible states.

## 7. Monte Carlo engine requirements

The engine must be deterministic when supplied with a seed and independently testable without Spring or AWS.

Required behaviour:
- Build a deck excluding all known cards.
- Randomly sample remaining board cards and, where applicable, unknown player cards.
- Evaluate all hands.
- Track wins, ties, losses, equity, trials, and elapsed time.
- Correctly split ties between players when calculating equity.
- Return numeric results with documented precision.
- Support execution of an arbitrary batch size.

Correctness tests should include known deterministic hand-ranking examples and sanity checks such as preflop AA having higher equity than a dominated lower pair in sufficiently large simulations. Do not create brittle tests that assert an exact Monte Carlo percentage without a fixed seed.

Performance matters. Benchmark the hot path, avoid needless object allocation inside the inner simulation loop where sensible, and document optimisation trade-offs.

## 8. Distributed job model

A large simulation must be partitioned into independent batches. Example: 10,000,000 trials with batchSize=100,000 creates 100 batches.

Each batch message must contain enough data for a worker to execute independently, including:
- simulationId
- batchId
- scenario/configuration or stable reference to it
- iterationCount
- deterministic derived seed
- schemaVersion

Seed derivation must ensure different batches receive distinct reproducible seeds when a parent seed is supplied.

Use at-least-once delivery semantics and design the consumer to be idempotent. Never assume a queue message is delivered exactly once.

## 9. Idempotency and failure handling

This is a central learning objective and must be implemented and documented, not merely mentioned.

Required design:
- Database uniqueness constraint on (simulation_id, batch_id) for persisted batch results.
- A duplicate delivery of the same batch must not double-count results.
- Worker processing should safely retry transient failures.
- Configure an SQS dead-letter queue in Terraform.
- Define maximum receive count and document why it was chosen.
- Handle poison messages cleanly.
- API status must distinguish running, completed, and terminally failed simulations.
- Aggregation must tolerate out-of-order batch completion.
- Concurrent completion of the final few batches must not create duplicate finalisation.
- Use transaction boundaries intentionally and document them.

Add tests that simulate duplicate batch delivery and concurrent result submission.

## 10. Relational database design

Use PostgreSQL as the durable source of truth. Create Flyway migrations.

Suggested tables:
- simulations(id UUID PK, created_at, updated_at, status, requested_iterations, completed_iterations, player_config_json or normalised equivalent, board_config, seed, batch_size, total_batches, completed_batches, error_message, version)
- simulation_batches(id UUID PK or composite key, simulation_id FK, batch_id, iterations, status, attempts, seed, started_at, completed_at, UNIQUE(simulation_id,batch_id))
- batch_results(simulation_id, batch_id, result payload / winner counts, trials, elapsed_ms, created_at, UNIQUE(simulation_id,batch_id))
- simulation_results(simulation_id UNIQUE FK, aggregated counts/equities, total_trials, elapsed_ms, finalised_at)

JSONB may be used where it simplifies scenario storage, but do not put everything into unstructured JSON without reason. Add relevant indexes and explain them.

## 11. Redis / NoSQL responsibilities

Use Redis for workloads that are naturally key/value or ephemeral rather than forcing it into the system.

Good uses:
- GET /simulations/{id} status cache with short TTL
- final result cache
- rate-limiting counters if implemented
- temporary progress counters, provided PostgreSQL remains authoritative

Document cache keys, TTLs, invalidation, and fallback behaviour when Redis is unavailable. The application must remain correct if the cache is flushed.

## 12. REST API

Provide OpenAPI documentation and validation errors with a consistent error schema.

Minimum endpoints:
POST /api/v1/simulations
  Creates a simulation and returns 202 Accepted with simulationId and status URL.

GET /api/v1/simulations/{id}
  Returns request metadata, status, progress, and timestamps.

GET /api/v1/simulations/{id}/results
  Returns 200 when complete; use an appropriate non-success/processing response while still running.

GET /api/v1/health or Spring Actuator health endpoint.

Optional:
DELETE /api/v1/simulations/{id} to cancel a queued/running simulation.
GET /api/v1/simulations for paginated history.

Use DTOs at the API boundary. Do not expose persistence entities directly.

## 13. Frontend

Build a clean, restrained React/TypeScript interface. Portfolio quality matters more than flashy animation.

Required screens:
- New Simulation form
- Simulation status/progress view
- Results view showing per-player wins/ties/equity
- Recent simulations/history if the list endpoint is implemented

Form requirements:
- card picker or validated card text input
- board input
- player count / hole-card pairs
- iteration count with safe min/max
- optional seed
- clear validation feedback

While a simulation runs, poll the status endpoint at a sensible interval. Stop polling on terminal state. Display progress as completedIterations/requestedIterations.

Include accessibility basics: labels, keyboard-friendly controls, semantic HTML, sufficient contrast, and loading/error states.

## 14. Local development environment

A new developer should be able to clone the repository and run the complete local system with documented commands.

Docker Compose should provide at minimum:
- PostgreSQL
- Redis
- LocalStack for SQS (preferred) or a documented local queue substitute

Application services may run in Docker Compose too, but also support running API/worker from the IDE.

Provide .env.example with non-secret development placeholders. Never commit real credentials.

README quick start should take no more than roughly 10 commands from clone to a successful simulation.

## 15. AWS deployment

Create Terraform that can provision a sensible demonstration environment.

Target architecture:
- ECR repositories for API and worker images
- ECS Fargate API service
- ECS Fargate worker service with configurable desired count
- SQS main queue + DLQ
- RDS PostgreSQL
- Redis via ElastiCache where cost/practicality allows; if omitted from the deployable demo due cost, clearly document a lower-cost alternative while keeping Terraform modules ready
- CloudWatch log groups and alarms/metrics
- S3 bucket for optional exported reports/benchmark results
- IAM roles following least privilege
- Secrets Manager or SSM Parameter Store for credentials
- ALB for the API where required

Avoid embedding secrets in Terraform state or variables files. Supply example tfvars only.

Cost awareness is important: defaults should avoid expensive always-on resources where possible, and documentation should call out potentially billable components before deployment.

## 16. Infrastructure as Code quality

Terraform must be formatted and validated. Organise modules sensibly without over-engineering.

Required outputs should include useful values such as API endpoint, queue URL/name, cluster name, and repository URLs.

Add docs explaining:
terraform init
terraform plan
terraform apply
terraform destroy

Do not automatically deploy infrastructure from Codex unless credentials and explicit permission exist. Building the Terraform code and validating it locally is sufficient by default.

## 17. CI/CD

Create GitHub Actions workflows.

Pull request / push CI should:
- checkout
- set up Java
- run Maven formatting/lint if configured
- run unit tests
- run integration tests where practical
- package all Java modules
- set up Node
- install frontend dependencies with lockfile
- run frontend lint/typecheck/tests
- build frontend
- optionally validate Terraform fmt/validate

A separate deployment workflow may:
- build Docker images
- authenticate to AWS through GitHub OIDC (preferred over static keys)
- push to ECR
- update ECS services

Do not make deployment run automatically on every branch. Gate it to main, a release tag, or workflow_dispatch.

## 18. Testing strategy

Tests are a first-class deliverable.

Engine unit tests:
- card/deck validation
- duplicate-card rejection
- hand evaluation examples
- deterministic seeded simulation
- tie splitting

API/service tests:
- request validation
- create simulation
- status progression
- result response
- failure states

Integration tests with Testcontainers:
- PostgreSQL repositories and migrations
- Redis cache behaviour
- worker result persistence
- duplicate batch idempotency

Concurrency/failure tests:
- same batch delivered twice
- two workers racing to persist the same batch result
- final batches completing concurrently
- transient failure followed by retry

Frontend tests:
- validation
- API loading/error state
- completed result rendering

Aim for meaningful coverage rather than chasing an arbitrary percentage. Critical distributed and numerical logic should be strongly covered.

## 19. Observability

Implement structured logging with IDs that make a distributed job traceable: simulationId, batchId, request/correlation ID where applicable.

Expose metrics such as:
- simulations submitted/completed/failed
- batches processed/failed/retried
- active/running simulations
- queue depth where available
- batch execution latency
- simulations/iterations per second
- cache hit/miss counters

Use Spring Boot Actuator and Micrometer. Configure CloudWatch-friendly logging. Never log secrets or full sensitive environment variables.

## 20. Performance benchmarking

Produce a reproducible benchmark demonstrating scaling.

At minimum compare the same workload using 1, 2, 4, and 8 local worker processes/threads or cloud worker tasks where available. Record:
- total trials
- worker count
- wall-clock time
- trials/second
- speedup relative to one worker
- efficiency = speedup / worker count

Create docs/benchmarks.md and preferably a generated CSV/plot script. Discuss why scaling is not perfectly linear: scheduling, queue overhead, database writes, aggregation, CPU contention, container limits, network latency, JVM warmup, etc.

Never fabricate benchmark figures. Only include measured values.

## 21. Security and robustness

- Validate all API inputs.
- Set maximum simulation size to protect cost/resources.
- Add API rate limiting if straightforward, but do not let it derail core work.
- Use least-privilege IAM.
- Keep credentials in environment variables / Secrets Manager.
- No AWS keys in repository or Git history.
- Add dependency scanning through Dependabot and/or GitHub dependency review if available.
- Use safe CORS defaults; document development vs production configuration.
- Do not expose database or Redis directly to the public internet in the target AWS architecture.

## 22. Documentation deliverables

The repository must contain polished documentation, written for both users and interviewers.

README.md:
- concise project pitch
- architecture diagram (Mermaid is acceptable)
- features
- technology stack
- quick start
- example API request/response
- test commands
- benchmark summary using measured numbers only
- AWS deployment pointer

Additional docs:
- docs/architecture.md: request flow, component responsibilities, data flow, failure modes
- docs/local-development.md
- docs/aws-deployment.md
- docs/benchmarks.md
- docs/ai-development.md
- docs/decisions/ADR-001-queue.md
- docs/decisions/ADR-002-postgres-vs-redis.md
- docs/decisions/ADR-003-idempotent-workers.md

Include at least one Mermaid sequence diagram showing submission -> queue -> worker -> aggregation -> result.

## 23. AI-assisted development documentation

Because AI-assisted development is one of the target skills, maintain docs/ai-development.md during the build.

Record representative examples such as:
- generating or expanding edge-case tests
- reviewing concurrency logic
- suggesting refactors
- explaining AWS configuration
- debugging a failing test

For each example, briefly state:
1. the task,
2. how AI was used,
3. what was independently verified,
4. any AI suggestion that was modified or rejected and why.

Do not claim AI work that did not occur. Codex itself may log the categories of assistance it provided, but the final document should remain concise and factual.

## 24. Git and engineering discipline - detailed requirements

This section is mandatory and should be treated as an acceptance criterion.

Initial commits should establish the repository safely before large implementation work. A sensible sequence could include:
1. chore(repo): initialise monorepo structure and gitignore
2. feat(engine): migrate core poker domain models
3. feat(engine): implement deterministic simulation batches
4. test(engine): add evaluator and simulation coverage
5. feat(api): scaffold Spring Boot API
6. feat(db): add PostgreSQL schema and Flyway migrations
7. feat(api): create simulation lifecycle endpoints
8. feat(queue): add batch planning and queue abstraction
9. feat(worker): implement queue consumer and batch execution
10. feat(worker): add idempotent result persistence
11. test(distributed): add duplicate-delivery and concurrency tests
12. feat(cache): add Redis status/result caching
13. feat(frontend): add simulation form and progress UI
14. feat(frontend): add results view
15. chore(docker): add local multi-service environment
16. feat(infra): add Terraform AWS resources
17. ci: add build and test workflows
18. feat(observability): add metrics and structured logging
19. perf: add reproducible benchmark tooling
20. docs: complete architecture and deployment documentation

Codex does not need to follow these exact commit boundaries if the existing codebase suggests better ones, but it MUST maintain similar granularity.

For every significant implementation phase:
- inspect git status first,
- make the change,
- run tests/build,
- inspect git diff,
- commit with an accurate message,
- continue from a clean or intentionally understood working tree.

Do not rewrite or squash all history at the end. The development history itself is part of the portfolio evidence.

## 25. Development phases

PHASE 0 - Repository audit and baseline
- Inspect existing PokerLab source.
- Run current tests/build.
- Document what is reusable.
- Create branch.
- Add/verify .gitignore and baseline README.
- Make baseline commit if needed.

PHASE 1 - Extract and harden core engine
- Refactor existing solver into engine module.
- Add immutable domain models and validation.
- Add deterministic seed handling.
- Add comprehensive engine tests.
- Commit in multiple logical steps.

PHASE 2 - API and PostgreSQL
- Scaffold API module.
- Add Flyway migrations and repositories.
- Implement simulation creation/status/results.
- Initially allow a local synchronous or internal executor if needed to prove lifecycle.
- Add tests.

PHASE 3 - Queue and distributed workers
- Introduce queue abstraction and SQS implementation.
- Add batch planner.
- Build worker service.
- Implement idempotent persistence, retries, and aggregation.
- Add concurrency/failure tests.

PHASE 4 - Redis
- Add cache for status/final results.
- Ensure correctness without cache.
- Add cache tests and docs.

PHASE 5 - Frontend
- Build form, progress, results, error handling.
- Add tests/typecheck/lint.

PHASE 6 - Containerised local environment
- Dockerfiles.
- Docker Compose.
- LocalStack queue.
- One-command startup where practical.
- End-to-end smoke test.

PHASE 7 - Observability
- Structured logs.
- Actuator/Micrometer metrics.
- Correlation identifiers.

PHASE 8 - Terraform/AWS
- ECR, SQS/DLQ, ECS, RDS, IAM, logs, optional Redis/S3, networking.
- terraform fmt/validate.
- Deployment documentation.

PHASE 9 - CI/CD
- GitHub Actions for backend/frontend/Terraform.
- Optional gated deployment workflow.

PHASE 10 - Benchmarks and polish
- Run reproducible benchmarks.
- Improve performance based on evidence.
- Complete docs, diagrams, sample screenshots if available.
- Run entire acceptance suite.
- Tag v1.0.0 after final passing commit.

## 26. API example

Example request:
POST /api/v1/simulations
Content-Type: application/json

{
  "players": [
    {"name": "Player 1", "cards": ["AS", "AH"]},
    {"name": "Player 2", "cards": ["KS", "KH"]},
    {"name": "Player 3", "cards": ["QS", "QH"]}
  ],
  "board": [],
  "iterations": 10000000,
  "batchSize": 100000,
  "seed": 123456789
}

Example 202 response:
{
  "simulationId": "<uuid>",
  "status": "QUEUED",
  "statusUrl": "/api/v1/simulations/<uuid>"
}

Example final result shape:
{
  "simulationId": "<uuid>",
  "status": "COMPLETED",
  "totalTrials": 10000000,
  "players": [
    {"name": "Player 1", "wins": 0, "ties": 0, "equity": 0.0}
  ],
  "elapsedMs": 0
}

The zeros above are placeholders for schema illustration only; runtime values must come from actual computation.

## 27. Quality gates before declaring complete

Codex must not declare the project complete until all applicable gates pass:
- git status is clean except intentionally untracked local-only files
- no secrets appear in tracked files
- mvn test passes for all Java modules
- mvn package passes
- frontend lint passes
- frontend typecheck passes
- frontend tests pass
- frontend production build passes
- Docker images build
- Docker Compose local stack starts successfully
- an end-to-end simulation completes through queue + worker + aggregation
- duplicate batch delivery test proves no double counting
- PostgreSQL migrations work from an empty database
- Redis can be unavailable without corrupting correctness
- Terraform fmt check passes
- Terraform validate passes where provider setup permits
- GitHub Actions YAML is syntactically valid
- OpenAPI docs load
- README quick-start has been executed from a clean environment as far as practical
- benchmark documentation contains measured, not invented, results
- architecture and failure-mode docs are complete
- meaningful Git commit history exists across the build

## 28. Definition of done

The project is done when a reviewer can clone it, follow the README, submit a simulation, observe it split into batches, see multiple workers process jobs, observe progress, retrieve the final result, inspect tests proving duplicate-message safety, review the AWS Terraform, see CI configuration, and understand the engineering trade-offs from the documentation.

The finished project should provide strong interview evidence for:
- distributed and multi-tier systems
- algorithms and performance
- relational database design
- key/value caching / NoSQL-style workloads
- AWS cloud architecture
- version control and collaborative engineering practices
- CI/CD and automated testing
- handling ambiguity, failures, concurrency, and retries
- technical written communication
- responsible use of AI-assisted development tools

## 29. Stretch goals - only after the core is complete

Do not implement these until the full core acceptance criteria pass:
- player ranges rather than exact hole cards
- WebSocket/SSE progress updates instead of polling
- authenticated users and saved simulation history
- DynamoDB implementation for selected high-throughput state
- Kubernetes deployment variant
- autoscaling worker count based on SQS queue depth
- downloadable CSV/JSON reports in S3
- comparison mode across multiple poker scenarios
- tournament-format simulation integration with the user's wider research project
- contribution upstream to a real open-source dependency or tooling project used by PokerLab

Stretch work must follow the same Git discipline: small changes, tests, documentation, and regular commits.
