# GTO trainer review and focused demo scope

**Decision, 24 September 2026.** The next deliverable is one complete, explainable preflop all-in trainer demo. It uses PokerLab's own solver for **two active players at a six-seat cash table**. The demo is a bounded study exercise, labelled `VALIDATION_ONLY`. It does not claim to solve ordinary 100bb opening strategy. The long-term goal remains broader 6-max cash preflop, followed by later-street and mixed practice.

## What exists today

| Area | Reviewed state | Evidence and limit |
| --- | --- | --- |
| Equity platform | Simulator, API, workers, persistence, CI and local infrastructure exist. | This produces showdown equity, which does not by itself grade betting decisions. |
| Solver foundation | Vanilla CFR and CFR+ style variants, Kuhn benchmark, weighted exact-card ranges, blockers, chip payoffs and best-response checks. | The scalar full-tree implementation is appropriate for small finite games. It is not designed for unrestricted 6-max no-limit Hold'em. |
| Two-player preflop | A six-seat table history leads to a two-player shove/fold, then call/fold decision. Exact board enumeration, versioned packs, screening and backend grading exist. | The wider synthetic fixture has eight hero combos, seven opponent combos and 47 unblocked matchups. Its exact pack has about 0.000149bb best-response gap, but the ranges have not been reviewed as realistic poker content. |
| Six-player research | A separate forced-shove game lets up to five responders call or fold. It has exact six-way payoffs, versioned packs, deviation measurement and a ten-question research API. | Its fixture contains only two synthetic combos per seat. Equal stacks, no rake, no side pots and no further betting make it a solver validation exercise. |
| Trainer product | Both research APIs are opt-in and grade on the server. The website shows an “in development” section. | There is no playable trainer page, saved attempt history or reviewed lesson yet. |
| Delivery | The current solver work is in draft PRs [#22](https://github.com/pingadom/PokerSolver/pull/22), [#23](https://github.com/pingadom/PokerSolver/pull/23), [#24](https://github.com/pingadom/PokerSolver/pull/24) and [#25](https://github.com/pingadom/PokerSolver/pull/25), stacked in that order. | PR #25 passed all eight CI checks, including Docker smoke. These changes need to land in dependency order before a release is cut. |

## Findings that affect the next release

1. **The two-player API binds a question to the spot hash, not the whole solution pack.** A new solve can keep the same spot inputs while changing strategy and action EVs. The server recomputes a question using the newly loaded pack, so an old client can be graded against a different solution without being told. The multiway research API already uses a full-pack hash. Add equivalent binding to the two-player API before building the demo UI.
2. **The exact payoff result answers a narrower question than the product ambition.** Exhaustive boards remove sampling error for the all-in call. A small best-response gap measures strategy quality inside that finite game. Neither validates the starting ranges, the omitted betting choices, rake or postflop play. Show the actual game and `VALIDATION_ONLY` label with every exercise.
3. **The existing two-player pack has useful decision variety but sparse range coverage.** An eight-combo fixture can demonstrate shoves, folds and mixed frequencies. A full 13×13 chart would imply coverage of hands the solver did not include. Use exact-combo views and a compact range list for this demo; reserve the full grid for a sufficiently broad pack.
4. **The website's solver section is a progress placeholder.** The current backend offers single-question two-player grading and a separate ten-question six-player research API. A complete two-player session contract and a focused frontend are the missing pieces for the chosen outcome.

## Next milestone: one playable, explainable drill

**Scenario.** Use the existing 100bb, no-rake six-seat validation history: blinds post, UTG and BTN raise through a five-bet, the other four seats fold, and UTG faces a 40bb raise after committing 22bb. UTG chooses `SHOVE` or `FOLD`; if UTG shoves, BTN calls or folds according to the solved strategy. The hero sees its own exact cards, positions, pot, commitments, action history and legal choices. BTN's private cards stay hidden. The wider exact pack is the starting fixture, with its synthetic-range limitation stated plainly.

**Backend work.** Give the two-player pack a deterministic full-content identity. Extend its research API with a reproducible ten-decision session, grade and complete review, using the pack identity to reject stale answers. The server recomputes EVs and session totals; the browser submits only seed, question index, pack hash and selected action. Keep solving offline, exact payoffs, pack validation and the current numerical gate. Document the session request and response contract.

**Frontend work.** Add one responsive trainer page reachable from the existing solver section. Show the scenario and assumptions first, then one decision at a time. After a choice, reveal exact-combo strategy frequencies, both action EVs and EV loss, with a short explanation of the committed chips, BTN's fold/call possibilities and the role of ranges. At the end, show all ten decisions and total/average EV loss. Use a compact list of hands actually present in the pack. Provide loading, invalid-pack and unavailable-API states. The existing Equity Lab can be linked as a separate comparison tool, with its different purpose explained.

**Content review.** Record why the chosen actions and ranges were used. Check card legality and history order, manually inspect several solved decisions, and call out any result sensitive to range weights. Keep `VALIDATION_ONLY` until a poker-content review supports stronger wording. Explain the model and its limitations in both the UI and the interview guide.

**Done when:** a local user can complete ten decisions on desktop and mobile; refresh reproduces a session from its seed and pack hash; old-pack submissions, illegal actions and client-supplied EVs are rejected; feedback comes from the saved exact pack; a reviewer can trace one decision from displayed cards and history to payoff, strategy and EV loss; solver, API, frontend and Docker smoke checks pass. Local progress may live only for the active session. No account is required.

## Deliberately later

- A full six-active-player lesson, broad 13×13 ranges, unopened opens, blind defence, three-bets and four-bets.
- Unequal stacks, side pots, realistic rake, new stack depths and other table formats.
- Flop, turn, river, partial-hand and coherent full-hand solving and mixed-street sessions.
- Login, cross-device progress, public AWS deployment and paid infrastructure changes. Deployment remains paused by the user's earlier instruction.

After the demo is working, review user feedback and the cost of expanding the solved range. The next solver step should then be chosen from measured bottlenecks and content quality, rather than adding several betting streets or table formats at once.
