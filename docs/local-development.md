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

The validation-only drill is disabled by default. The easiest complete local demo uses the Compose overlay below from the repository root. It mounts only the exact synthetic pack into the API container and enables the two-player research route. Open [http://localhost:8080/#trainer](http://localhost:8080/#trainer) to play ten decisions; the equity simulator remains at the home page. The overlay is for local development only.

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer.yml up --build -d --scale worker=2
```

If running from an IDE instead, start the API on loopback with `TRAINER_RESEARCH_ENABLED=true` and `TRAINER_RESEARCH_PACK_PATH` set to the absolute path of `solver/src/test/resources/diverse-validation-pack.json`. The API reads the file at startup. It rejects missing, sampled-payoff or numerically unscreened packs; it does not solve a game on a request or bundle the synthetic pack into the API artifact. Keep this unauthenticated research route off public deployments. Attempts are not stored on the server.

For example, in PowerShell from the repository root, after starting the usual local dependencies:

```powershell
$env:TRAINER_RESEARCH_ENABLED = 'true'
$env:TRAINER_RESEARCH_PACK_PATH = (Resolve-Path 'solver/src/test/resources/diverse-validation-pack.json').Path
mvn -pl api -am -DskipTests package
java -jar api/target/api-1.0.0.jar --server.port=18080
```

`GET /api/v1/trainer/research` returns the two-player spot assumptions, the exact combos covered by both ranges, solution quality and the full `packHash`. `GET /api/v1/trainer/research/questions/42` returns the public spot, exact hero cards, legal `SHOVE`/`FOLD` actions, a decimal-string seed and that pack hash. It omits opponent private cards and action EVs. Submit that seed and hash to `POST /api/v1/trainer/research/grade` with `{"seed":"42","packHash":"<hash from question>","action":"FOLD"}` to see both action EVs, solution frequencies and selected-action EV loss in big blinds. Grading recomputes the question from the seed; it rejects a stale full-pack hash or any browser-supplied EV. These responses set `Cache-Control: no-store`.

For a ten-decision drill, `GET /api/v1/trainer/research/sessions/42/questions/0` returns the first question; index ranges from 0 to 9. `POST /api/v1/trainer/research/sessions/grade` accepts `{"sessionSeed":"42","index":0,"packHash":"<hash from question>","action":"SHOVE"}`. After all ten answers, `POST /api/v1/trainer/research/sessions/review` accepts the same seed and pack hash with `actions` containing exactly ten `SHOVE`/`FOLD` strings in order. It returns every reconstructed question and feedback plus total and average EV loss. No session is persisted on the server; replay uses the string seed and pack hash.

The pack has synthetic, narrow ranges and a no-rake all-in tree. Its exact payoff table and small best-response gap validate this bounded model only. Do not treat its feedback as advice for ordinary 100bb cash-game preflop decisions.

The trainer page shows the same label and limits, the six-seat action history, exact hero cards, possible combos in both assumed ranges, solver frequencies and action EVs after each choice. The final review lists all ten decisions. The seed and full pack hash are kept in the URL for repeatable questions; refreshing starts at decision one because attempts are not persisted yet. A stale URL prompts a new session if the saved solution changes.

The same local Compose overlay also enables the separate [bounded river research API and website drill](river-solver-research.md) using `river-validation-pack.json`; open [http://localhost:8080/#river](http://localhost:8080/#river). When running the API directly, set `TRAINER_RIVER_RESEARCH_ENABLED=true` and `TRAINER_RIVER_RESEARCH_PACK_PATH` to the absolute path of that file. Inspect `GET /api/v1/trainer/research/river`, then request `GET /api/v1/trainer/research/river/questions/42`. Submit one of that question's legal action codes to `POST /api/v1/trainer/research/river/grade` with the same seed and returned pack hash. This route is disabled without the explicit flag.

The overlay also enables the [turn-to-river research drill](turn-river-solver-research.md) from `turn-river-validation-pack.json`; open [http://localhost:8080/#turn-river](http://localhost:8080/#turn-river). For a directly run API, set `TRAINER_TURN_RIVER_RESEARCH_ENABLED=true` and `TRAINER_TURN_RIVER_RESEARCH_PACK_PATH` to the absolute pack path. `GET /api/v1/trainer/research/turn-river` reports the saved game; `GET /api/v1/trainer/research/turn-river/questions/42` supplies one turn or river decision; `POST /api/v1/trainer/research/turn-river/grade` takes `{"seed":"42","packHash":"<hash from question>","action":"k"}` with a legal action code. The route stays absent unless enabled.

The same opt-in pack also powers [http://localhost:8080/#turn-river-hand](http://localhost:8080/#turn-river-hand), a connected turn-to-river partial hand. `POST /api/v1/trainer/research/turn-river/hands/replay` takes a seed, pack hash, hero player `0` or `1`, and an array of the hero's action codes. Send `"actions":[]` to deal; append each chosen action to replay through the next decision or terminal hand. The opponent follows the saved policy and keeps the same hidden cards through both streets. This research replay is stateless and does not save progress.

The overlay also enables a [flop-to-river research hand](flop-turn-river-solver-research.md) from the exact-deck compressed pack; open [http://localhost:8080/#flop-hand](http://localhost:8080/#flop-hand). For a directly run API, set `TRAINER_FLOP_RESEARCH_ENABLED=true` and `TRAINER_FLOP_RESEARCH_PACK_PATH` to its absolute path. `GET /api/v1/trainer/research/flop` returns the pack hash and game assumptions. Send `{"seed":"42","packHash":"<hash from metadata>","heroPlayer":0,"actions":[]}` to `POST /api/v1/trainer/research/flop/hands/replay`; append each chosen action code and resend. The opponent's private cards stay hidden until showdown. This route is absent without the explicit flag.

## Six-seat research sessions

The separate multiway route is also disabled by default. The local trainer Compose overlay enables it and serves the website drill at [http://localhost:8080/#multiway](http://localhost:8080/#multiway). For a directly run API, set `TRAINER_MULTIWAY_RESEARCH_ENABLED=true` and `TRAINER_MULTIWAY_RESEARCH_PACK_PATH` to the absolute path of `solver/src/test/resources/six-seat-exact-pack.json` before starting it. Packs load once at startup, must be at most 16 MiB, and must pass structural validation plus the exact-payoff and 0.05bb deviation gates. Keep this research API local; it has no account or attempt persistence yet.

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
