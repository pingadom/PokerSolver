# Complete backend CI evidence

Backend CI runs two solver groups and one service group. Every job retains the 35-minute timeout, and the solver retains its 2 GiB heap. All modules are compiled, packaged and checked for formatting in each clean job before tests run. `-DskipTests` applies only to dependency preparation; the following verification step runs the assigned tests. No solver quality, model, numerical or replay budget changes.

`scripts/backend-ci.py` discovers all Java test sources matching [Surefire's four default class-name patterns](https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html): `Test*`, `*Test`, `*Tests`, `*TestCase`. A fixed SHA-256 hash of the fully qualified class name assigns solver classes to group 0 or 1. New matching classes are assigned automatically. Engine, shared, API and worker tests run together through their normal Maven `verify` lifecycle; solver groups also run `verify`, with complete class selectors and no method filters. The root module list is checked explicitly so a new module cannot silently disappear.

Each job saves its exact checkout commit, source inventory hash, assigned classes and successful Maven completion in a manifest alongside its JUnit XML. The final `backend-evidence` job downloads groups into separate directories, preserving duplicate evidence instead of overwriting it. It requires:

- All three completed manifests for its exact checkout and current inventory.
- Every assigned class represented in the correct module and group, exactly once across groups.
- Positive test counts, complete per-case accounting and matching failure/error/skip counts.
- Zero failures and errors. The existing PostgreSQL lifecycle tests may skip only for the explicit missing `TEST_DATABASE_URL` condition; other skipped checks reject.

Missing groups/classes, wrong commits, duplicate/unassigned suites, inconsistent counters and new unassigned sources/modules fail the evidence check. Nine independent Python controls exercise these failure cases, failed Maven execution and stale local evidence before real evidence is accepted. The combined `junit-results` artifact contains all original group XML/manifests plus `backend-ci-summary.json`. Its upload explicitly includes the downloaded evidence directory and fails if files are missing. Totals come from actual reports; expected historical totals are not substituted for evidence. All matrix jobs and the final evidence check must succeed before a solver PR is merged.

Inspect assignments without running or modifying tests:

```powershell
python scripts/backend-ci.py plan solver-0
python scripts/backend-ci.py plan solver-1
python scripts/backend-ci.py plan services
python -m unittest discover -s scripts -p test_backend_ci.py
```

Normal local verification remains `mvn -B spotless:check verify`. The shard runner intentionally rejects stale local JUnit reports or an existing manifest; use a clean checkout for shard reproduction. After building dependencies with `mvn -B spotless:check install -DskipTests`, run one group with `python scripts/backend-ci.py run solver-0` (or `solver-1` / `services`). On Windows, `--maven` can supply the absolute `mvn.cmd` path. To audit downloaded group artifacts, run `python scripts/backend-ci.py verify <directory-containing-the-three-named-group-directories>` from the same tested commit.

This change follows two timed-out serial runs of the owned sequence-form milestone. The second completed all 692 solver tests and 792 backend tests overall with zero failures/errors, then timed out during API checks; that partial run was not accepted as a full pass. Parallel groups remove that serial dependency and preserve independent complete replay and all service tests. They improve CI scheduling, not the runtime speed of the poker solver.
