# Complete-menu preflop table trainer

This page connects the owned complete-menu batch cache to the existing six-seat
table at `#root-batch-preflop`. The new source pin is
`a7533a49a06fe2b7376109bce133c1b3c937dbef8915ed47af86290d36b777c8`.
The original, minimum and earlier sequence studies retain their own URLs, API
paths, public pins, saved sessions and exports.

The visible table and EV/loss bounds match the earlier sequence study. The new
source extends owned minimum qualification to the complete action menu; it does
not create another poker decision or improve these already published endpoints.

## Play and review

Open the solver section and choose **Pocket nines: complete-menu preflop practice**.
The blue BB is the hero, the red BTN has raised to 3 BB, and the other four seats
are folded. The pot is 4.5 BB and the call costs 2 BB. Each action button includes
its current-decision EV bounds and extra chip commitment; keys 1–3 also select
fold, call and raise. A server grade appears below the controls. Use **Try another
move** to compare the same decision again, **Review attempts** to inspect ordered
choices, and **Download session review** for the server's canonical JSON.

Call is within the study's loss limit, fold is outside and raise remains
unresolved. Unresolved choices have a separate count. This is one repeated BB99
root with supplied ranges and a selected-flop continuation model: six seats with
two active players. It is not a full hand, a new board/range bank, unrestricted
six-player GTO or a unique optimal frequency claim. Public trainer admission is
false. Displayed EV endpoints round outwards; exact bounds and the source pin
are in the expandable context below the table. The already posted 1 BB is
accounted for once by the server.

## Run locally

The API remains default-off. Start the existing stack with its separate override:

```powershell
docker compose -f docker-compose.yml -f docker-compose.trainer-root-batch-preflop.yml up --build
```

The seven source mounts are read-only. See [cached API](batch-preflop-cached-api.md)
for the startup work reservation, ownership checks and exact opt-in settings.
The page uses `/api/v1/trainer/research/root-batch-preflop-drill`. During Vite
development it proxies to `http://localhost:18080` by default. Override only this
source's target with `POKERLAB_ROOT_BATCH_PREFLOP_API_TARGET` if using a separate
API port. Production uses same-origin API requests.

The immutable frontend fixture is a byte-identical public HTTP capture, checked
in CI. It is a protocol pin, not an offline fallback. Metadata, questions, grades,
reviews and downloads must match the source exactly. An unavailable/disabled API,
stale study link, malformed response or wrong-source grade produces an explicit
error. **Load current study** recovers a stale pin without migrating saved data.

## Sessions and boundaries

Browser storage key: `pokerlab:root-batch-preflop:v1:<study hash>`.
Export prefix: `pokerlab-root-batch-preflop-review-<first 12 hash characters>.json`.
Up to 128 attempts are stored in order, including repeated actions. Reload
restores only a valid review for this pin. A malformed or foreign save is
discarded only under this study's key. Reset leaves the other studies untouched.
Network failures do not fabricate a grade or attempt. Navigation aborts pending
requests; late replies cannot alter a different study. Downloads are validated
against the exact ordered choices and preserve the server's UTF-8 LF bytes.

## Validation and review status

The change extends the existing protocol and table test matrices to this source,
and all directed cross-study pairs test pin/session isolation and cancellation.
All 1,175 frontend tests across 40 files passed, including 85 added cases. Lint,
typecheck, production build and fixture identity checks passed. A single declared
native API startup was reused for HTTP smoke, Vite proxy checks and production
browser QA. There are no additional Java replay
suites, numerical generations, backend source changes or dependency upgrades.

The built page was checked at 1280×720 and 390×844. All three feedback classes,
reload/restore, stale-link recovery, reset and the solver entry link worked.
An ordered raise/call/fold review downloaded through the browser matched the
server's export bytes exactly. There was no mobile horizontal overflow; mobile
feedback requires vertical scrolling. Desktop feedback fits after the choice
button scrolls into view. These checks and screenshots accompany the exact
revision review packet. All 413 numerical artifacts and earlier frozen previews
remain unchanged. Full PR CI and
production-container checks follow only after explicit owner clearance. AWS
deployment remains paused.

The project owner cleared code revision `b193ec567dd484ce829f6a456dba7aa347078bd7`
on 2026-11-07 (Europe/London), with “cleared b193ec5”. This confirmation applies
only to that revision. Code Review History retains this and all earlier approvals.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`bc35089a63f16b4f6afb0aa4ef34323c7b282abd` and cleared PR creation on
2026-11-07 (Europe/London), with “cleared bc35089”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
