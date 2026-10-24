# Unpaired physical-flop source

PokerLab now has four qualified **backend** decisions on 6♦ T♦ Q♣ (canonical card order `6d Qc Td`). BTN faces a half-pot BB bet, with 9.75bb in the pot and 3.25bb to call. This adds a different board texture to the existing paired-board evidence. The website's fourteen-question collection and its source pins remain unchanged.

This is a restricted two-active-player game at a six-seat table. It uses the same supplied 24-world joint private prior as the weak-hand source, retains all twelve dealt cards as blockers, has no rake, and permits one flop bet followed by exact checkdown. It is not a general six-player cash-game solution.

## Discover first, qualify second

The separately declared discovery budget reserves two physical game builds, at most 25 parent solves and sixteen action computations, with no fresh CFR references or automatic retries. The original parent has 1,321 states and 73 information sets. Its independently evaluated best-response gap is below 1e-8bb.

All eight declared hand/history combinations remain in the discovery report. Four after-check cases have certified witnesses of zero reach within the original security face. Their denominator cannot support a conditional decision; they stay rejected with their witnesses intact. More CFR iterations would not repair that reach condition. The four facing-bet cases separate fold from call, so only those proceed to a separately budgeted whole-batch qualification.

| BTN hand | Robust move within 0.01bb of best | Approximate chosen-action EV, bb |
| --- | --- | --- |
| 2♣ 3♣ | Fold | −3 |
| A♥ K♥ | Call | 1.0253–1.0291 |
| A♠ Q♠ | Call | 4.1665–4.1674 |
| Q♥ T♥ | Call | 4.2313–4.2325 |

The displayed intervals are rounded outward for readability; the saved report contains authoritative endpoints. These are absolute profit EVs under the declared game and prior, not equities or prescribed action frequencies.

The complete four-hand batch passes the existing qualification gates. Two fresh CFR+ policies run for 80,000 and 160,000 iterations, shared across all four questions: 240,000 iterations, twelve selected-policy assessments and 32 endpoint certificates. Full-parent quality, reach, inclusion, posterior, EV/loss and materiality checks retain their original thresholds. No hand is dropped to make the batch pass.

The new report pin is `c5845cabc3a93208520549d5893ea311ad3c5409f792b96729b0d325fab077c9`. The paired review exercises both legal actions for all four questions: eight attempts, four distinct questions, four within-limit and four outside-limit results. Public trainer admission remains false; offline qualification is a separate private capability.

## Generate a report and review in one solve

`SixMaxSharedFlopFeedbackMain` now accepts:

```text
solve-review <request.json> <new-report.json.gz> <new-review.json> [index:fold|call ...]
```

The command validates the request, choices, distinct real parent paths and new output paths before optimizer/reference work. It qualifies once, then builds the review from that same owned result. Review export therefore requires no second reference run. The original 100-attempt, 512KiB review and 8MiB source limits remain intact; existing `solve` and `replay` commands remain available.

A failed batch retains its diagnostic report, creates no review and returns an error. The two successful outputs are not a filesystem transaction: an I/O failure can leave a valid report without a review. Loading a saved source still performs complete pinned fresh replay; this command does not create a shortcut around qualification.

## Independent verification

`scripts/check-unpaired-flop-discovery.py` independently regenerates all 15,792,192 preflop boards and 15,984 selected-flop runouts across the 24 worlds. It checks folded blockers, the literal betting tree and utilities, both original-parent LP certificates, all 32 positive endpoint certificates and four zero-reach rejection witnesses. Certificate arithmetic uses exact rationals of the stored binary64 values, original flow equations and independent full-game best responses.

`scripts/check-unpaired-shared-flop.py` verifies the complete four-question source, all twelve policy assessments, exact traversal/pruning work and every saved review grade. It reuses the unchanged qualification-check function. Neither independent checker runs an LP optimizer or CFR. Corruption tests deliberately alter physical cards, certificates, endpoints, policies, reference work, question identity and review coverage; semantic rejection is tested separately from final file pins.

Java integration tests independently regenerate the source/review pair, load the pinned saved source, replay the parent and all eight discovery cases, and verify empty/repeated reviews and malformed-input rejection. Their physical builds, parent solves and fresh-reference work are separately reserved before execution.

The twelve new artifacts have distinct filenames under `docs/data`; all 299 older data files retain their original bytes and Git blobs. API/UI collection integration is a later, separately versioned step. AWS deployment remains paused.
