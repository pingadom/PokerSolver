# Three-hand preflop table practice

The `#root-hand-bank` page connects the owned three-hand cache to a six-seat poker
table. Choose pocket sevens, pocket nines or suited ace-jack, or use mixed practice.
Hero is the blue big blind; the red button has raised to 3 BB. Four seats are folded.
The pot is 4.5 BB and calling costs 2 BB. EV ranges appear on fold, call and raise
buttons, with immediate feedback and **Next hand** directly under the table.

## Practice and reviews

Mixed practice shuffles one of each hand per round and avoids an immediate repeat
at round boundaries. Choosing a hand switches to manual practice; **Next hand**
then cycles sevens, nines and suited ace-jack. Switching preserves attempts while
clearing the previous hand's feedback. All three server questions are loaded and
validated first; switching them needs no additional request.

The URL records the current source, hand and mode. Reload restores validated
attempts and the last choice only when it belongs to the active hand. The remaining
mixed-round order is not persisted: reloading or choosing a mode starts a new
round containing the active hand. Each attempt counts separately, including retries.
Review rows show their hand and verdict; distinct-hand count is separate. Sessions
hold at most 128 attempts. **Start over** clears only this source's local session.

Review downloads use the server's canonical UTF-8 LF JSON without recomputing
grades. A browser test downloaded a three-hand call/raise/fold review and compared
all 1,983 bytes with a separate request to the cached native API. Its SHA-256 is
`35553b88cb88b2515cba690e2d3dde7215ae6f71903e58130bd0eca7bddb0765`.

## Local enablement

Enable the [three-hand cached API](root-hand-bank-cached-api.md) first. The source
is disabled by default and physically replays once at startup. In frontend Vite,
`POKERLAB_ROOT_HAND_BANK_API_TARGET` selects its backend; the default is
`http://localhost:18080`. Other studies keep separate opt-ins, routes and session
keys. Production uses the existing same-origin API proxy. AWS deployment is paused.

The public fixture is an immutable protocol pin, never an offline fallback. Live
metadata, all questions, grades and reviews must match it exactly, including the
source index for each hand. Duplicate JSON keys, extra or changed fields, stale
pins, wrong-hand results and malformed reviews are rejected. Network failures
show an error; they do not synthesize a grade. Pending requests prevent double
submissions and hand switches, and navigation aborts outstanding work.

## Validation and scope

The frontend suite passes 1,384 cases across 42 files: all 1,268 prior cases plus
116 new cases. Lint, TypeScript and the production build pass. The browser check
covered mixed and manual switching, all verdicts, saved-session recovery, reset,
stale-link recovery and a real download. At 1280 by 900, all six seats, EV actions
and **Next hand** fit without scrolling. A 390 by 844 layout has no horizontal
overflow; feedback and long reviews can scroll vertically. No browser console
warnings or errors were observed. The temporary viewport override was reset.

This UI reused the already running cache: zero additional native starts, no Java
replay suites and no source report regeneration. All 427 numerical source files,
backend code and solver limits remain unchanged. CI adds a byte-identity check
for the new frontend fixture while retaining the previous jobs and checks. Full
PR CI for this frontend revision follows owner clearance and PR creation.

This remains a supplied-range research exercise with two active players at six
seats, a fixed preflop prefix and selected-flop continuation model. It does not
establish unrestricted six-player GTO, whole-hand frequencies or a played-out
hand. All three supplied hands classify call as **Within limit**, fold as
**Outside limit**, and raise-to-9 as **Unresolved**. `trainerAdmission=false`.
Loss bounds come from joint decision comparisons; subtracting displayed EV
endpoints does not reproduce them. Posted blinds are accounted for by the server.

## Review status

The project owner cleared code revision `838fd5ac57e6524998181efedf99a6a1d6d7f20d`
on 2026-11-08 (Europe/London), with “cleared 838fd5a”. This confirmation applies
only to that revision. Full PR CI follows PR creation; the central history retains
this and earlier approvals.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`cc53f13ce75c053c298fe32c5a0d3fb67663cd2d` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared cc53f13”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
