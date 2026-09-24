# Local development

## Complete stack with Docker

Install Docker Engine/Desktop with Linux containers and Compose v2. From the repository root:

```sh
cp .env.example .env
docker compose up --build -d --scale worker=2
```

On PowerShell, use `Copy-Item .env.example .env` for the first command. Open [http://localhost:8080](http://localhost:8080). The API listens at [http://localhost:18080](http://localhost:18080), with Swagger at `/swagger-ui/index.html`. Health is `/actuator/health`. PostgreSQL, Redis, API and LocalStack host ports bind only to loopback. Initial image builds take longer than subsequent starts.

```sh
./scripts/smoke.sh
docker compose logs -f api worker
docker compose down
```

`smoke.sh` requires curl/jq. Windows users can run `./scripts/smoke.ps1` without jq. Both submit AA vs KK vs QQ, wait with a deadline, check total trials and assert equity conservation. Compose creates queues through executable, LF-terminated `scripts/init-sqs.sh`. Scale with `docker compose up -d --scale worker=4`.

`docker compose down` preserves PostgreSQL volume data. `docker compose down -v` deletes the local database, so use it only when intentionally resetting demo data. Stop Redis with `docker compose stop redis` to exercise cache fallback, then restart it with `docker compose start redis`.

The GitHub CI Compose job starts from a clean checkout, builds images, starts two workers, completes a simulation through LocalStack, checks OpenAPI/frontend availability, stops Redis, and completes a second simulation.

## API and worker from an IDE

Install Java 21, Maven 3.8+ and Node 24 with pnpm 11.25. Start just dependencies:

```sh
docker compose up -d postgres redis localstack
mvn spotless:check package
```

Set the environment variables in `.env.example` in the IDE run configuration. Spring does not automatically read a `.env` file when run directly. Run `com.pokerlab.api.ApiApplication` on port 18080 and `com.pokerlab.worker.WorkerApplication` on 18081. Alternatively:

```sh
java -jar api/target/api-1.0.0.jar --server.port=18080
java -jar worker/target/worker-1.0.0.jar --server.port=18081
```

Each command needs its own terminal. Additional workers can use `--server.port=0` for a free Actuator port. The Docker API image bundles the frontend; local JAR builds do not, so start Vite separately:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). Vite proxies `/api` to port 18080, avoiding development CORS exceptions. For a different API port, edit `frontend/vite.config.ts`.

## Without Docker

Use a dedicated PostgreSQL 16+ database and set `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Run the same packaged services with `QUEUE_MODE=postgres`. This selects a durable, lease-based development transport stored in PostgreSQL; API and workers remain separate processes. Use `CACHE_ENABLED=false` if Redis is absent, or leave it enabled to exercise fallback. Flyway applies both migrations at service startup.

Do not point tests at a database containing useful application data. For an isolated native PostgreSQL test database, set `TEST_DATABASE_URL`, `TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD`, then run `mvn test`. The same migration/lifecycle/concurrency contract runs against that database. Without these variables, native tests skip; with Docker, separate Testcontainers suites use disposable PostgreSQL/Redis instances. Reports show which path ran.

During development on the original Windows host, a separate PostgreSQL cluster ran on loopback port 55432 under ignored `.local/`. No installed database or global configuration was changed. Modern Node and a locally downloaded, checksum-verified Terraform binary were used because system Node was obsolete and Terraform was absent. These local tool paths are not part of repository configuration.

## Research trainer API

The validation-only drill is disabled by default. To try it locally, run the API on loopback with `TRAINER_RESEARCH_ENABLED=true` and `TRAINER_RESEARCH_PACK_PATH` set to the absolute path of `solver/src/test/resources/diverse-validation-pack.json`. The API reads the file at startup. It rejects missing, sampled-payoff or numerically unscreened packs; it does not solve a game on a request or bundle the synthetic pack into the API artifact. Keep this unauthenticated research route off public deployments. It has no attempt storage or trainer UI yet.

For example, in PowerShell from the repository root, after starting the usual local dependencies:

```powershell
$env:TRAINER_RESEARCH_ENABLED = 'true'
$env:TRAINER_RESEARCH_PACK_PATH = (Resolve-Path 'solver/src/test/resources/diverse-validation-pack.json').Path
mvn -pl api -am -DskipTests package
java -jar api/target/api-1.0.0.jar --server.port=18080
```

`GET /api/v1/trainer/research/questions/42` returns the public spot, exact hero cards, legal `SHOVE`/`FOLD` actions, a decimal-string seed and the pack's `spotHash`. It omits opponent private cards and action EVs. Submit that seed and hash to `POST /api/v1/trainer/research/grade` with `{"seed":"42","spotHash":"<hash from question>","action":"FOLD"}` to see both action EVs, solution frequencies and selected-action EV loss in big blinds. Grading recomputes the question from the seed; it rejects a stale hash or any browser-supplied EV. Both responses set `Cache-Control: no-store`.

The pack has synthetic, narrow ranges and a no-rake all-in tree. Its exact payoff table and small best-response gap validate this bounded model only. Do not treat its feedback as advice for ordinary 100bb cash-game preflop decisions.

## Six-seat research sessions

The separate multiway route is also disabled by default. Set `TRAINER_MULTIWAY_RESEARCH_ENABLED=true` and `TRAINER_MULTIWAY_RESEARCH_PACK_PATH` to the absolute path of `solver/src/test/resources/six-seat-exact-pack.json` before starting the API. Packs load once at startup, must be at most 16 MiB, and must pass structural validation plus the exact-payoff and 0.05bb deviation gates. Keep this research API local; it has no account or attempt persistence yet.

- `GET /api/v1/trainer/research/multiway` returns the game assumptions, six seats, pack hash, numeric quality and session length.
- `GET /api/v1/trainer/research/multiway/sessions/42/questions/0?player=0` starts a deterministic ten-question session. Index is 0–9. Player 0 mixes responding seats; 1–5 fixes HJ through BB for the supplied fixture. UTG has already shoved and is not a decision to practise.
- `POST /api/v1/trainer/research/multiway/grade` accepts `{"sessionSeed":"42","index":0,"player":0,"packHash":"<hash from question>","action":"CALL"}`. Choose `CALL` or `FOLD`; the server reconstructs the question and computes conditional action EVs.
- `POST /api/v1/trainer/research/multiway/review` accepts the same seed, player and pack hash, with `actions` containing exactly ten `CALL`/`FOLD` strings in question order. It returns every reconstructed question, feedback and total/average EV loss.

Seeds are decimal strings to preserve signed 64-bit values in JavaScript. Every response uses `no-store`; grade and review reject an outdated full-pack hash and unknown JSON fields. Sessions are replayable but not persisted or anti-cheating assessments. See [the solver model and reproduction commands](multiway-solver-research.md) for the forced-shove, equal-stack, no-rake limits.

## Tests and formatting

```sh
mvn spotless:apply
mvn spotless:check verify
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

The engine includes an exhaustive distribution check of every five-card hand, named category/tiebreaker examples, seeded reproducibility, multiway ties and partial-batch planning. Integration contracts cover duplicates, concurrent finalisation, out-of-order results, retry and terminal failure. See [benchmarks](benchmarks.md).

On Windows, stop a service before rebuilding its running JAR, or copy the JAR to an ignored runtime directory first; Windows can lock a running JAR and prevent Maven repackage. If a default port is occupied, stop only the relevant local service or adjust Compose mappings. If progress stalls, inspect queue connectivity, outbox rows and worker logs rather than resubmitting blindly.
