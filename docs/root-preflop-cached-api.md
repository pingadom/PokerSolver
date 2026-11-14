# Cached root preflop trainer backend

## Declaration before replay

Use only the qualified PR209 BB99 root and its unchanged saved request/report.
Build one immutable question and three cached grades from an owned qualified
Result, preserving fold/call/raise-to-9 and raise's unresolved classification.
No hand, range, board, action, policy, tolerance or work limit is changed or
searched. No new numerical source report is generated or overwritten.

The public preflop context includes six seats, BB99, pot 4.5 bb, 2 bb to call,
the known public history and costs 0/2/8 bb. Its board is empty; villain cards,
future selected flop, internal information-set keys and full source policies are
not question fields. Public EVs use current-decision profit: add BB's committed
1 bb to source terminal-profit bounds once. Loss bounds/classifications stay
unchanged. This remains a two-active-player supplied-range research game at a
fixed public-history prefix, not a six-player equilibrium or whole-hand reach.

Pin stored request/report bytes and the canonical qualified report hash in a
new manifest. Capture bounded regular files, reject aliases/path escapes/changed
bytes before replay, then perform one complete owned feedback solve and compare
the entire saved report. Check the source files again after replay. A parsed
report, snapshot or caller-selected pin cannot construct the owned cache.

Reserve one source replay for initial public-fixture capture, two local validation
replays (one solver cache/loader suite and one real API startup suite), and three
new replays per full CI run (the two suites and a dedicated Docker startup).
Each replay uses one physical game, two parent calls/four parent LPs, five shared
envelope LPs and two fresh 80k/160k references (240k iterations). Original per-replay
limits remain 10,000 pivots, four billion arithmetic units, eight million compiler
units and 8 MiB stored/expanded source bytes. No numerical retry/fallback or larger
cap is allowed. Invalid-manifest controls reject before physical work. Existing
source tests and their separate replay reservations remain unchanged.

After startup, metadata, question, grade and ordered review/export requests use
the immutable cache only, with no file reads, optimization, hand evaluation or
training. Require the source pin and case index zero, retain all three choices,
bound reviews to 128 ordered/repeated attempts and count within/outside/unresolved
separately. Exports are deterministic UTF-8 JSON with LF and a final newline.

Add separate default-off configuration and routes. Disabled routes return 404
without constructing a solver bean. Inputs accept choices only; reject missing,
extra, null, duplicate, coerced, nonfinite, oversized or unknown choices/pins.
All successful responses use no-store. Verify explicit OpenAPI request schemas
and download headers. Require a dedicated production Docker CI smoke for default
absence, owned startup, all three grades, bounded reviews and strict controls.
Local Docker absence is not a passing container test. Preserve every existing
test/job/timeout, source artifact, API, frontend asset and paused AWS deployment.
No frontend/UI route is added in this backend milestone.

## Backend contract

`SixMaxRootPreflopDrill` accepts only an owned qualified feedback Result, binds its
canonical report hash and creates one immutable question and three grade objects.
The public EV scope is `CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1`.
Fold has EV near zero, call near +1.786 bb, and raise retains a wide interval;
none of the source loss bounds or classifications are changed. Repeated choices
count independently and unresolved choices have their own review count.

`SixMaxRootPreflopDrillLoader` captures and pins all three startup files before
performing the full replay, then checks their identity and contents again before
returning the cache. The manifest is
`docs/data/supplied-root-preflop-drill-manifest.json`; its qualified report pin is
`cc20afe1dee2584956fba058a4cbac5e421886b6bc98e91ff2c602666173a658`.
Stored byte pins are separate from that canonical report pin. Startup consumes
the declared source budget once; request handlers receive only the cache.

Routes under `/api/v1/trainer/research/root-preflop-drill`:

| Method and path | Behaviour |
| --- | --- |
| `GET /` | Metadata, source pin, scope and startup budget |
| `GET /questions/0?studyHash=...` | One public preflop table and current-decision bounds |
| `POST /grade` | Pin, integer `sourceCaseIndex: 0`, and a legal action only |
| `POST /review` | Pin and up to 128 ordered choices |
| `POST /review/export` | The same review as canonical downloadable JSON |

Legal actions are `fold`, `call`, and `raise:9.0`. The raise is returned normally
as `STRATEGY_DEPENDENT`, meaning unresolved by the conservative bounds. It is not
counted as a proven mistake or a proven safe move. All successful responses use
`Cache-Control: no-store`; exports include a source-pinned filename. Explicit
request schemas describe the choices; bodies cannot provide EVs, grades or
solver inputs.

Enable this source explicitly with `TRAINER_ROOT_PREFLOP_ENABLED=true`,
`TRAINER_ROOT_PREFLOP_MANIFEST_PATH` and `TRAINER_ROOT_PREFLOP_STUDY_HASH`.
The default is disabled, returning 404 without constructing the source cache.
The local Compose opt-in mounts the three dependencies read-only:

```powershell
docker compose -f docker-compose.yml -f docker-compose.trainer-root-preflop.yml up --build -d
python scripts/smoke-root-preflop.py
```

## Validation

The published fixture and canonical review were captured through real HTTP from
the owned startup, then independently reconstructed from the unchanged qualified
source. The checker verifies every public field, the one-time EV shift, all three
grades, review order/counts, exact canonical LF bytes and preserved ambiguity.
It rechecks the underlying source evidence without fresh optimization/training.
Raw review bytes are read without newline normalization.

Eight loader guard tests reject changed/aliased/escaping sources, malformed,
oversized or inflated input and attempts to authorize a modified report by
refreshing only its stored SHA. Eight cache tests and eight API tests use one
complete source replay each. They cover strict inputs, default absence, ownership,
immutability, all moves, ordered/repeated/max reviews and operation after startup
files are made unavailable. A separate production-container smoke is required
in CI; the local native HTTP check does not substitute for it.

```powershell
mvn -pl solver -am test "-Dtest=RootPreflopDrillTest,RootPreflopDrillLoaderGuardsTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl api -am test "-Dtest=RootPreflopDrillControllerTest,RootPreflopDrillDisabledTest" "-Dsurefire.failIfNoSpecifiedTests=false"
python scripts/check-root-preflop-drill.py
python -m unittest discover -s scripts -p test_check_root_preflop_drill.py
```

The separate [table UI](root-preflop-table-ui.md) consumes these cached endpoints
at `#root-preflop`. The backend remains explicitly opt-in.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`df4101ea7b0e3b11467bd1d28c2f59c2768e3725` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared df4101e”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
