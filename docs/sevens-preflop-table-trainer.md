# Pocket sevens table trainer

The page at `#root-sevens-preflop` connects the owned pocket-sevens cache to the
existing six-seat table. Open the solver section and choose **Pocket sevens:
defend against the button**. The heading and blue BB show pocket sevens, while
the red BTN shows its raise to 3 BB. The four other seats are folded, the pot is
4.5 BB and the call costs 2 BB. Opponent private cards and future board cards are
not exposed.

## Decisions at the table

Every fold, call and raise button shows its current-decision EV bounds and extra
chip commitment. Keyboard keys 1–3 select the same actions. A server grade appears
just beneath the controls, alongside **Try another move**, **Review attempts**
and **Start over**. The table records the selected action beneath the hero.
Call is within the source's loss limit, fold is outside and raise stays unresolved.
Unresolved choices have their own review count and are not labelled correct or
incorrect.

The source is one BB77 decision with two active players at a six-seat table,
supplied joint ranges and a selected-flop continuation model. It does not establish
unrestricted six-player GTO or whole-hand frequencies. Trainer admission remains
false. The server accounts for the posted 1 BB exactly once; the browser preserves
its EV/loss values and rounds displayed interval endpoints outwards. Exact bounds
and public history are available below the table.

The shared page now takes its hand name from the source client. All earlier
nines headings, URLs and session namespaces remain unchanged. Singular wording
for one saved attempt and one review attempt is corrected across those pages.

## Source identity and local setup

The study pin is
`5f52b053c7610adbfb4abd2cfb210674b4a325d8b870cd350edc22d192708da6`.
The frontend fixture is copied byte-for-byte from the owned HTTP capture in
`docs/data/supplied-sevens-preflop-drill-fixture.json`, and CI checks that identity.
It pins the protocol; it is never substituted for a failed API request.

The backend remains default-off. Use the existing stack with its separate overlay:

```powershell
docker compose -f docker-compose.yml -f docker-compose.trainer-root-sevens-preflop.yml up --build
```

The four source files are mounted read-only. The [cached API guide](sevens-preflop-cached-api.md)
explains the bounded owned startup and its exact opt-in settings. Requests use
`/api/v1/trainer/research/root-sevens-preflop-drill`. Production requests use the
same origin. Vite defaults to `http://localhost:18080`; its source-specific override
is `POKERLAB_ROOT_SEVENS_PREFLOP_API_TARGET`.

## Saved attempts and review downloads

The storage namespace is `pokerlab:root-sevens-preflop:v1:<study hash>` and the
export prefix is `pokerlab-root-sevens-preflop-review-<first 12 hash characters>.json`.
Up to 128 choices are retained in order, including repetitions. A reload restores
only source-valid grades. A foreign or malformed save is discarded only under
this source's key, and reset leaves other sources untouched. Stale links show
**Load current study** rather than silently accepting another source.

Metadata, question, grade, review and export responses must match their pin and
public context. Unknown fields, altered EVs/losses, leaked private cards, wrong
source grades and dishonest counts are rejected. Network failure does not create
a grade or attempt. Route changes abort pending work, and late responses cannot
modify a different source. A downloaded review must match the submitted ordered
choices and preserve the server's UTF-8 LF bytes.

## Validation and review status

The full frontend suite passes 1,268 tests across 40 files, including 93 added cases.
The common protocol/table matrices cover all five sources, and all directed
cross-source pairs check pins and session isolation. Navigation tests cover late
responses; corrupted, maximum and repeated sessions preserve honest counts.
Lint, type checking, production build and the captured fixture identity also pass.

Real native HTTP/OpenAPI smoke, a dedicated Vite proxy, production same-origin
requests, desktop/mobile browser checks, restored sessions, stale-link recovery,
reset and an actual ordered download are checked locally. The first preview
startup completed before a pause. Its processes ended, so one additional bounded
startup was declared for final wording verification. Both use the original
one-game/four-parent-LP/five-envelope-LP/240000-reference-iteration limits. No new
numerical source report, backend change, Java replay suite or dependency upgrade
is introduced. All 420 prior data files and earlier previews are preserved.

This frontend revision is awaiting explicit owner review. The prior `bc35089`
clearance applies only to its cached backend. Full PR CI follows fresh clearance;
no new frontend PR or owner confirmation is claimed here. AWS deployment stays
paused.
