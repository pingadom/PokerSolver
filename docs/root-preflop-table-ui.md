# Root preflop table practice

Open `#root-preflop` from the solver section's “Pocket nines: compare fold, call
and raise EVs” link. This UI consumes the existing
[cached preflop API](root-preflop-cached-api.md). The API must be enabled explicitly;
a disabled or unavailable server displays an error rather than a fixture demo.

## Playing the exercise

BB holds 9d 9h, facing BTN's raise to 3 bb. The six-seat table shows four folded
players, the red button, blue hero, stacks and prior contributions. It has no
board or visible opponent hand. The pot is 4.5 bb and calling costs 2 bb.

Fold, call and raise-to-9 stay together below the hero. Each button shows its
current-decision EV range and additional commitment (0/2/8 bb). Keys 1–3 select
the corresponding move; modified/repeated keys and interactive-field shortcuts
are ignored. The selected action appears under the hero, followed by the returned
grade. Try another move clears the selected action while retaining EVs. It repeats
this same question; it does not deal a new hand or advance the action tree.

Fold is outside the source's decision-loss limit, call within it, and raise remains
unresolved. The UI never labels the raise a proven mistake or safe move. The exact
original bounds are available below the table. EV uses optimal future hero
continuation against each supported opponent strategy, with the already-posted
1 bb accounted for once by the backend. Joint loss bounds cannot be obtained by
subtracting independent EV endpoints. This remains a fixed-prefix supplied-range
research exercise, not a six-player equilibrium or whole-hand frequency estimate.

## Session and protocol

Up to 128 ordered/repeated choices are saved locally under the exact study pin.
After reloading metadata and the question from the API, the UI validates every
stored grade and count before restoring a session. Corrupt storage is discarded;
unavailable storage leaves practice usable for the current visit. Start over
clears this session without a new solve or question request.

Review sends only the study pin and ordered choices to the backend, then checks
every returned grade, ordinal and count. Unresolved decisions have a separate
count. Download requests the backend's canonical JSON, validates it against the
current choices, requires LF with a final newline, and saves the original bytes
under a source-pinned filename. It does not generate a replacement review client-side.

The bundled public HTTP fixture is a protocol pin. CI requires it to remain byte
identical to the published capture. Metadata, question and grade responses must
match every field, including finite numbers, source hash and EV scope. Unknown
fields, hidden cards, a future board, stale links, duplicate JSON keys (including
escaped aliases), reordered grades and changed raise classifications fail closed.
Requests use no-store; overlapping submissions are blocked and old requests are
aborted on navigation. No local fixture replaces a failed network request.

## Local development

The existing opt-in Compose overlay enables the backend:

```powershell
docker compose -f docker-compose.yml -f docker-compose.trainer-root-preflop.yml up --build -d
cd frontend
pnpm dev
```

By default Vite proxies this API to port 18080. For a separate local API, set
`POKERLAB_ROOT_PREFLOP_API_TARGET` before starting Vite. This setting affects only
the root preflop route. Existing trainer proxies and backend settings are preserved.

## Verification

The UI/protocol tests cover all three moves, honest EV/raise semantics, retries,
concurrent clicks, stale replies, strict pins, review/export corruption, persistence,
storage failures, a full 128-attempt session and keyboard controls. Full frontend
lint, type checking, tests and production build are required. Real browser testing
uses the owned API once at startup; requests do not solve or train. Backend tests,
numerical gates, original data, source work limits and paused AWS deployment are
unchanged by this UI milestone.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`bc35089a63f16b4f6afb0aa4ef34323c7b282abd` and cleared PR creation on
2026-11-07 (Europe/London), with “cleared bc35089”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
