# Eighteen-decision table practice

Open `#interval-unpaired-flop` from the Solver section to practise the eighteen-question v6 collection. It includes the original fourteen contexts plus all four qualified BTN hands on 6♦ T♦ Q♣. The API must be separately enabled as described in [the backend collection guide](unpaired-flop-collection.md).

The six-seat table shows hero in blue, the active opponent in red, folded seats, the prior bet, board, pot and amount to call. Action buttons show supported EV/loss ranges after a choice; feedback, retry and next/previous controls stay at the table. The decision picker is collapsed initially. New-board picker labels include the physical board so identical private cards on different boards are distinguishable.

**Inspect action EVs** reveals both legal moves' verified ranges before choosing. It requests one cached grade containing all alternatives, validates the complete question/source identity, and uses only the alternatives as study information. It records no attempt and saves no session. A subsequent real decision is graded separately. Inspections are cached in memory per collection pin, global question index and report pin; navigation cannot carry another hand's or board's EVs into the current question. Link changes abort outstanding work and clear those inspection caches.

Practice persists under a separate v6 device-storage key. Reload restores validated attempts. Importing a bounded review verifies the complete eighteen-question document, then sends only its pinned choices to the backend for regrading before replacing the session. Review/export counts distinct questions separately from retries and preserves the 100-attempt and 1MiB document limits. A blocked storage API leaves play and downloadable review available. Reset affects only the current v6 key.

The decoder binds every nested question, hand, board, public history, action menu and grade to the frozen qualified inventory. It rejects incompatible schemas, stale pins, substituted same-pin hands, foreign-board values, extra fields, duplicate JSON keys (including escaped aliases), changed counts and invalid indices. The UI fixture is byte-identical to the saved backend review and CI enforces that comparison.

The new page loads on demand and calls only `/api/v1/trainer/research/flop-interval-collection-v6`. A disabled or inconsistent server produces an explicit error; the page never falls back to another collection. Existing routes, review schemas, saved sessions and all 315 older evidence files are retained.

For an independently configured development API, `POKERLAB_UNPAIRED_FLOP_API_TARGET` sets the Vite proxy target for v6. The old proxy settings stay separate. Browser verification can use a separate port and tab without reloading an existing practice session.

These remain supplied-prior research games with two active players at a six-seat table, no rake and one flop bet followed by exact checkdown. An EV range is final chip profit under the declared game; a loss interval is a conservative bound, not a strategy frequency or sampling error. Four after-check cases with certified zero reach remain excluded from practice.
