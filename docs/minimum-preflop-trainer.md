# Minimum-informed preflop trainer

## Scope declared before validation

Connect the unchanged minimum-informed cached API to a separate browser route,
`#root-minimum-preflop`, pinned to complete study identity
`baa6420476438abfca0c508d19ca313d8f9dc78f643293df9b2866418f15bf6d`.
Keep the original cc20 API, public fixture, route and browser storage namespace.
Use the existing poker table, server-supplied current-decision EV ranges on action
buttons, feedback below the controls, and ordered/repeated session reviews.
The raise remains strategy dependent. Six seats represent two active players,
supplied ranges and a selected-flop continuation research model; admission to the
general trainer remains false. This does not establish six-player GTO.

Extract the shared strict protocol into one source-bound client factory rather
than maintaining two copies. Each client validates its entire captured public
bank and server grades/reviews. A captured fixture is a protocol pin, never an
offline answer or fallback. No raw solver report, private cards, future board,
browser optimization or browser-generated grade is included.

Reserve exactly one new native minimum API startup for end-to-end browser checks
and the unchanged HTTP smoke. Use the production cached configuration/controller
in the existing database-free preview harness. Its one startup replay has the
existing one physical game, two parent calls/four parent LPs, one minimum LP and
two fresh references/240k iterations. All original compiler, pivot, arithmetic,
plan, source-size, qualification and equality gates remain unchanged. No numerical
source generation, retry, parameter search or saved numerical artifact changes
are authorized by this milestone. Frontend tests use captured HTTP fixtures and
perform zero solver replays. Do not rerun Java replay suites for frontend-only
changes; existing required CI jobs remain unchanged for the eventual PR.

Use a separate local frontend port and dedicated API proxy target, leaving old
preview builds and the user's current browser tab intact. Verify both study
protocols, stale/cross-study data rejection, grade/review/export, ordered/repeated
attempts, restore/reset, maximum session size and cancellation across navigation.
Run the entire frontend suite, lint, typecheck and production build. Inspect the
new route at desktop and narrow viewport sizes. AWS deployment stays paused.

## Running locally

Enable the API with the separate
`docker-compose.trainer-root-minimum-preflop.yml` overlay described in
[the cached API guide](minimum-preflop-cached-api.md).
Point `POKERLAB_ROOT_MINIMUM_PREFLOP_API_TARGET` at its server when running Vite
against a different port; otherwise the target is `http://localhost:18080`.
Open `#root-minimum-preflop`; after validation the URL includes the complete
`study` pin. A disabled server displays an unavailable message and no choices.

The new study displays approximately +1.211 to +1.786 bb for raising to 9 bb,
preserving the full-action upper bound. Fold and call retain their previous EVs.
Loss bounds and classifications come from the server. The exact unrounded bounds,
public history and EV scope remain available below the table.

Browser sessions use `pokerlab:root-minimum-preflop:v1:<study hash>`.
The original route retains `pokerlab:root-preflop:v1:<original hash>`.
Resetting one cannot clear the other. Reviews are validated against the current
source and ordered choices; downloads retain the server's validated JSON bytes.

## Local verification

Both study variants run the same protocol and component test matrix. Additional
integration cases check the tighter lower bound with the original full-action
ceiling, reject data across study identities, exercise both routes, isolate and
reset saved sessions, discard a legacy review stored under the new key, and abort
a pending grade during cross-study navigation. The CI frontend job compares the
new fixture byte for byte with the existing owned HTTP capture, alongside all
previous comparisons. No server file-access permissions are widened for tests.

The initial full local test run found an out-of-root fixture import rejected by
Vite and one existing long UI test timing out under default parallel execution.
Replace the fixture import with the existing CI `cmp` pattern and run the full
frontend suite with two workers, retaining the five-second test timeout. No test,
assertion or numerical gate is removed to obtain a pass. Initial logs are retained
with the local review evidence. An initial smoke invocation incorrectly targeted
Vite for OpenAPI, which is served by the API directly; rerun it against the same
already-started native server, without any extra solver startup or replay.

The real browser exercise uses the Vite API proxy, submits raise/call/fold, checks
all three feedback classes, reviews their original order and downloads the server
review. Check the downloaded bytes against the native export for those choices.
At 390 CSS pixels the table and all action controls fit without horizontal page
overflow; long bounds wrap. The normal desktop viewport keeps the decision
buttons and immediate feedback together. Full session history can extend below
the viewport. Reset the temporary responsive viewport after inspection.

Required local commands are `pnpm --dir frontend lint`,
`pnpm --dir frontend typecheck`, `pnpm --dir frontend test --maxWorkers=2` and
`pnpm --dir frontend build`. Backend packaging uses `mvn -pl api -am -DskipTests
package`; it performs no test replay. The unchanged native API HTTP smoke covers
all grades, ordered/repeated/max reviews, exports, strict inputs and OpenAPI.
Docker and the full PR CI run remain pending until a reviewed PR exists.

The completed local run passed 1,006 tests across 39 files, including 70 additional
cases for the second study and cross-study integration. Lint, typecheck, production
build, backend packaging and the native HTTP smoke passed. All 392 existing data
files, previous preview assets and the reviewed interview document were preserved.
The review preview serves a frozen copy of the production build against the same
already-qualified cached API; freezing the frontend performs no solver replay.

## Review status

The project owner cleared code revision `7c8a70e` for PR creation on 2026-11-04
with “cleared 7c8a70e”. The factual confirmation is recorded across the project's
human-readable documentation and central review history. Later material code
changes require renewed review. PR creation and full CI follow this clearance.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`fc6b6a7301f7da64aecbf2c60c579d18562f8d1c` and cleared PR creation on
2026-11-05 (Europe/London), with “cleared fc6b6a7”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
