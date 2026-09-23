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
