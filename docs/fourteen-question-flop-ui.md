# Fourteen-decision flop practice

Open `#interval-call-flop` from the solver section. This page uses the v5 fourteen-question API; the eleven-question and all older routes retain their own protocols, links and reviews.

The same six-seat table shows the acting player in blue and the opponent in red. Choose a move to reveal hand EV and loss intervals on both action buttons. The result appears below the controls; previous, next and retry remain beside the actions. The decision picker is collapsed initially to keep the feedback near the table. Number keys choose the corresponding move when focus is outside an input or control.

## Saved practice and reviews

Each accepted attempt is saved on the current device under a separate v5 collection-pin key. Reloading the same collection restores validated attempts; question selection stays in the URL. Repeated attempts count separately from distinct questions practised. There is a 100-attempt limit; navigation, review and export remain available at the limit.

Use **Review attempts**, then **Download replayable review**, to obtain the canonical UTF-8 JSON returned by the backend. **Import saved review** validates the complete document and asks the backend to regrade its pinned choices before replacing the current session. Files are capped at 1 MiB; wrong schemas, changed questions, duplicate fields, invalid counts and stale grades are rejected. A rejected import leaves the current session intact. If browser storage is unavailable, practice still works and a downloaded review can retain it. **Start over** clears only this collection's v5 device session.

The source of truth remains the backend: submissions contain only collection pin, global question index, report pin and action. The client never submits supplied EVs or grades for acceptance. A saved review can also be replayed through the existing backend CLI described in [the backend document](fourteen-question-flop-collection.md).

## Identity and compatibility

The page checks the exact frozen, physically qualified fourteen-question inventory. The fixture is copied byte-for-byte from `docs/data/supplied-fourteen-question-flop-collection-review.json`; CI checks that equality. The first eleven questions pass through the existing v4 parser and table adapter. The last three use the existing facing-bet table with a separate typed shared-source branch. A shared question wraps a flat decision; shared feedback is already flat.

The three additional hands are Ah Kh, As Qs and Qh Th on 8d Kc Kd, with BTN facing BB's 3.25 bb bet. They share a report hash but have different cards and EVs. UI identity includes the v5 collection hash, **global** question index and report hash. Saved attempts and feedback are indexed by the global question, never by report hash alone. Legacy question 10 (zero-based) retains its nested weak-source index 0; shared questions 11–13 retain their local indices 0–2.

## Local API configuration

The backend must explicitly enable `TRAINER_CALL_FLOP_COLLECTION_ENABLED`, with the manifest path and collection pin documented in the backend guide. Default-off servers produce a clear unavailable message; there is no fallback to another collection.

Vite normally forwards all API requests to port 18080. To use an independently started v5 API, set `POKERLAB_CALL_FLOP_API_TARGET=http://127.0.0.1:<port>` in the local frontend environment. This affects only `/api/v1/trainer/research/flop-interval-collection-v5`; the older qualified-source override and all other proxies remain separate. Production uses the existing same-origin `/api` deployment configuration.

## Research scope

These are independent flop decisions with two active players at a six-seat table, two source-specific supplied range sets, no rake and one flop bet followed by exact checkdown. The three shared hands are not three new range sets. Source-history reach remains unavailable and public trainer admission is off. This is not a general six-active-player GTO solution. Broader reachable check/bet contexts, additional boards/ranges, later betting and rake require separate solver work.

Validation covers all fourteen questions and twenty-eight moves, board/menu changes, all three same-pin hand transitions, stale grade rejection, persistence, imports, canonical downloads, unavailable servers and the attempt cap. Existing routes and tests remain in place.
