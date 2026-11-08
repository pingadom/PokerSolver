# Cached minimum-informed preflop API

## Declaration before startup replays

Use only the unchanged qualified PR214 request/report, original envelope and PR213
minimum. Pin all four dependency byte hashes and the entire qualified report's
canonical identity `baa6420476438abfca0c508d19ca313d8f9dc78f643293df9b2866418f15bf6d`.
Capture bounded regular files before replay, reject aliases/escapes and check them
again afterwards. Reproduce the entire source through an owned solve; only its
private qualification object may construct the immutable question/grade cache.
Do not trust parsed flags or caller-refreshed hashes. No numerical source artifact,
world, range, board, action, threshold, policy, representation or work cap changes.

Reserve one enabled real HTTP startup for public-fixture/review capture, two local
validation replays (one shared solver cache suite and one shared API context),
and three new replays per complete CI run (the two suites and a dedicated Docker
startup). Each uses one physical game, two parent calls/four parent LPs, one
minimum LP and two fresh references/240k iterations. Keep 8m compiler, 10k pivot,
4b arithmetic, 64 reduced-plan and 8 MiB stored/expanded source caps. No numerical
retry, fallback, search or additional generation is reserved. Malformed-source
controls must reject before physical work. Existing replay classes remain enabled.
The disabled API test context performs no replay. Initial native HTTP capture is
the only new native API process; the API test uses its existing MockMvc pattern.

Publish a new manifest, public HTTP fixture, canonical review and independent
receipt. Public context has six seats, two active players, BB99, an empty board,
pot 4.5 bb and 2 bb to call. Hide opponent cards, future board, policies, internal
information-set keys and certificates. Add the committed 1 bb to terminal-profit
EVs exactly once; retain all loss bounds and classifications, including unresolved
raise feedback. Bind every request and review to the complete report identity.

Use separate default-off configuration and routes. Accept only a source pin, case
zero and legal choices; reject unknown, duplicate, missing, null, coerced, nonfinite,
oversized and stale inputs. Bound ordered/repeated reviews to 128 attempts and
export deterministic UTF-8 JSON with LF/final newline. All successful responses
use no-store. After startup, requests use cached data only, even if source files
become unavailable. Verify explicit OpenAPI schemas and default absence.

Require a dedicated production-container CI smoke with default absence, exactly
one enabled replay, all three cached grades and review/input controls. Local
Docker unavailability is not passing evidence. Preserve every existing job/test/
timeout and all 388 old data files, the cc20 API/fixtures/deep links, previews and
interview document. No frontend route is included. This remains a bounded supplied-
range two-active-player research model, with public trainer admission false.
AWS deployment remains paused.


## Backend contract

Routes use `/api/v1/trainer/research/root-minimum-preflop-drill`:

| Method/path | Behaviour |
| --- | --- |
| GET base | Metadata, complete source pin and bounded startup inventory |
| GET `/questions/0?studyHash=...` | Public preflop context and current-decision bounds |
| POST `/grade` | Exact pin, integer case zero and one legal action |
| POST `/review` | Pin and up to 128 ordered/repeated choices |
| POST `/review/export` | The same cached review as canonical downloadable JSON |

Actions are `fold`, `call` and `raise:9.0`. Their classifications remain outside
limit, within limit and strategy dependent. Approximate current-decision EVs are
0 bb, +1.786 bb and +1.211 to +1.786 bb respectively. Raise retains loss bounds
0 to 0.574637464148319 bb. Its EV upper bound comes from the original full-action
envelope, not the upper bracket on the minimum. The single commitment shift never
changes loss bounds or classifications.

Enable with `TRAINER_ROOT_MINIMUM_PREFLOP_ENABLED=true`,
`TRAINER_ROOT_MINIMUM_PREFLOP_MANIFEST_PATH` and
`TRAINER_ROOT_MINIMUM_PREFLOP_STUDY_HASH`. Defaults expose no routes or solver
bean. The separate Compose overlay mounts the manifest and four approved source
files read-only. The old root-preflop configuration and source identity remain
independent; both configuration classes have distinct bean names.

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-root-minimum-preflop.yml up --build -d
python scripts/smoke-minimum-preflop.py
```

The loader verifies approved byte pins as well as the canonical report identity,
rejecting attempts to self-authorize changed files by refreshing a manifest SHA.
The feedback solver's captured-DTO overload retains the original physical solve,
request validation and complete report comparison; it avoids reopening source
paths during the loader's replay. After replay, all captured identities, sizes,
modification times and hashes must remain unchanged before cache publication.
The cache accepts only an owned qualified result and retains public question and
grade objects, without retaining source file paths for request-time access.

## Verification and review status

The public fixture and canonical review were captured through actual HTTP using
the production controller/configuration classes. The temporary capture server was
stopped after the enabled smoke checked all grades, ordered/repeated/max reviews,
exports, strict inputs and OpenAPI. The independent checker revalidates the
underlying qualified evidence and reconstructs every public field and export byte
without LP/CFR optimization.

Local validation covers one shared solver cache replay, loader rejection controls,
one shared enabled API context, and a disabled context with zero source replays.
The dedicated container job is a required CI check; local HTTP capture does not
substitute for it. It has not run before the PR is created.

```text
mvn -pl solver -Dtest=MinimumPreflopDrillTest,MinimumPreflopDrillLoaderGuardsTest test
mvn -pl api -am -Dtest=MinimumPreflopDrillControllerTest,MinimumPreflopDrillDisabledTest -Dsurefire.failIfNoSpecifiedTests=false test
python -m unittest discover -s scripts -p test_check_minimum_preflop_drill.py
python -m unittest discover -s scripts -p test_smoke_minimum_preflop.py
```

The project owner cleared code revision `b7db059` for PR creation on 2026-11-03.
The approval is recorded across the project's human-readable documentation;
material later code changes require renewed review.


## Validation-only budget amendment

The first API validation completed the owned replay and passed eight of nine
API tests. One export-header assertion still contained the legacy cc20 source
prefix copied from the older test. The production response used the correct baa6
prefix, already checked by the live HTTP smoke. Derive the expected filename from
the test's complete source pin instead of a copied literal.

Reserve exactly one additional enabled API test-context replay to validate that
assertion correction. Retain the original declaration, failed test log and XML.
This changes no numerical inputs, policies, work caps or saved source; complete
source equality remains mandatory. There is no new numerical generation or search.
The local validation total becomes one solver replay and two API replays; the
initial HTTP capture and three CI replay reservations remain unchanged.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`028914bdd8d970241bdd315db79ffbb094659622` and cleared PR creation on
2026-11-08 (Europe/London), with “cleared 028914b”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
