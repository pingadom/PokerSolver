# Qualified raise feedback table practice

The new `#bank-raise-feedback` page connects the owned qualified 77/AJs cache to
the existing six-seat table. It loads original source IDs 0 and 2, displays each
move's current-decision EV on its button, grades choices through the native API,
and keeps next-hand controls and feedback on the main table. The hero stays blue,
opponents red, with move colours and captions on the player panels.

The backend cache/API was separately reviewed as `cc53f13`. This UI revision
was cleared as `77accde` on 2026-11-14 (Europe/London). Both raises remain unresolved and general trainer
admission stays off. This is practice for a supplied two-active-player model at six
seats with a fixed preflop prefix and selected-flop continuation.

## Original question IDs

The shared bank client derives its supported IDs from its exact frozen public
inventory. Question and review lookup use the original ID, not an array offset.
The new inventory is `[0,2]`, so suited ace-jack remains source 2 rather than being
renumbered to 1. ID 1 is rejected on this source. The original three-hand bank
retains `[0,1,2]`, its source pin, menus, route, session key and grades.

Both question responses must pass before the table appears. Deep links, manual
hand selection, mixed practice, next-hand wrapping and saved reviews use only the
declared IDs. Balanced mixed rounds alternate the two qualified hands and avoid
an immediate repeat across round boundaries. The original three-hand queue keeps
its earlier behaviour. Only 77 and AJs appear in the new source's hand picker.

## Server feedback and sessions

The separate API path is
`/api/v1/trainer/research/bank-raise-feedback-drill`. The route carries study hash
`60f8eb975a963ebdf3964cf4edb72354b8e0841e01bc5eb3a3a333e5bc9b0343`.
Metadata, both questions, each server grade and every review must exactly match
the pinned public protocol. The embedded fixture is a validation reference; it
never substitutes for a missing or failed server response.

EV values already include the server's posted-blind adjustment. The UI applies
no second shift. Loss ranges and classifications stay unchanged. The new source
explains that loss bounds conservatively compare all moves and are not jointly
attained endpoints. The original bank retains its earlier joint-bound explanation.
Raise loss upper bounds remain about 1.1078041 BB for 77 and 3.5953334 BB for AJs.

The two sources use separate browser-storage keys and export filenames. A review
can contain mixed hands and repeated choices in their original order; only those
choices are posted to the API. Stored grades, ordinals, counts and pins are checked
before restoration. Invalid or cross-source sessions are discarded with a notice.
The 128-attempt cap, empty reviews and canonical UTF-8 downloads remain supported.
Stale pins, unsupported IDs and missing opt-ins stop practice explicitly. Aborted
requests cannot restore a late grade after navigation changes the question.

## Validation

Lint, TypeScript, the production build and all 1,465 tests across 44 files passed.
All 1,384 prior cases remain, with 81 added. New coverage includes all six grades,
source-ID gaps, manual/mixed navigation, altered menus and metadata, loss/EV/scope
forgery, cross-source sessions, exports, attempt caps, keyboard guards and stale
request cancellation. An existing interval-collection shortcut test now waits for
its controls to be enabled before dispatching a key; the previous CI failure
occurred in that unchanged test while it waited only for a button to appear.
No product behaviour or assertion was removed from that earlier test.

One declared native startup replay used the reviewed backend, original physical
game, four parent LPs, two endpoint LPs and fresh 80k/160k references. No source
report was regenerated and no numerical rejection retried. Native HTTP and OpenAPI
checks passed for both questions, all six grades, strict inputs and review/export
controls. Production and Vite proxies both graded through that real local server.

Browser checks at 1365 by 900 verified all six players, move buttons, next-hand
controls and inline feedback within the viewport. The 390 by 844 mobile check
kept all six players and both hand choices, with no horizontal overflow; vertical
scrolling remains available on a phone. An actual browser download for four mixed
attempts matched all 2,573 native export bytes. Temporary viewport overrides were
reset after testing.

The old source's local preview server was initially unavailable; its route showed
HTTP 503 rather than fabricated fixture answers. A separately declared, one-time
legacy compatibility startup restored that original source with its unchanged
one-game, up-to-eight-parent/15-envelope-LP and fresh 80k/160k reference budget.
Its native smoke passed for all three questions and nine grades, and both proxies
returned the exact original nines question. That compatibility run is separate
from the single qualified-source startup above; neither source was regenerated
or retried. The earlier browser observation of 503 remains in the audit trail.
The new source's native preview is live at
`http://127.0.0.1:18152/#bank-raise-feedback`. It is a local preview, not a cloud
deployment. AWS deployment remains paused.

CI keeps all existing jobs and checks, adding only a byte comparison between the
reviewed backend fixture and its new frontend copy. All 436 numerical evidence
files, existing UI fixtures, solver/API code, dependencies and frozen earlier
previews are preserved. Full CI for this later revision follows owner clearance.

## Next boundary

The new UI exposes tighter, qualified decision bounds for the two original hands.
It does not establish unrestricted six-player GTO, full-hand action frequencies,
attained full-action maxima or coherent complete-hand play. Further solver/range
expansion and postflop progression remain separate work.

## Review status

The owner cleared UI code revision `77accde` on 2026-11-14 (Europe/London).
The confirmation below applies to that exact UI revision. Any later material
code changes require their own review before PR creation.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`df4101ea7b0e3b11467bd1d28c2f59c2768e3725` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared df4101e”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
