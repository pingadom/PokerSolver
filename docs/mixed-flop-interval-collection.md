# Six-question BB/BTN flop collection

The v2 collection combines six independently graded decisions from three owned solver sources: BB's initial check/bet question, BB facing a BTN bet, and all four BTN hands after BB checks. It preserves the original request/report pins, question and feedback protocols, and their different reference budgets. The existing two-question collection remains v1 with its original identity and artifacts.

This is a research drill with two active players at a six-seat table, supplied joint hand beliefs, no rake and one flop betting round followed by exact turn/river checkdown. Mixing these questions does not establish a connected full-hand strategy, broader hand coverage or a six-player equilibrium. Public trainer admission stays off; AWS deployment stays paused.

## Identity and grading authority

`SixMaxMixedFlopIntervalCollection` accepts only the three owned result types. Each underlying trainer requires its private qualification capability. Deserializing a report, setting a public qualified flag or recomputing a hash cannot construct those capabilities.

The new review schema is `pokerlab-flop-interval-collection-review/v2`, with selection `ORDERED_TWO_BB_AND_FOUR_BTN_FLOP_QUESTIONS/v2`. The collection hash binds both version strings and six ordered entries containing source kind, original report pin and source-local question index.

| Collection index | Source kind | Source-local index | Decision |
| --- | --- | --- | --- |
| 0 | `INITIAL_CHECK_BET` | 0 | BB checks or bets |
| 1 | `BB_FACING_BET` | 0 | BB folds or calls |
| 2 | `BTN_AFTER_CHECK` | 0 | BTN with `9s Js` |
| 3 | `BTN_AFTER_CHECK` | 1 | BTN with `Ah Kh` |
| 4 | `BTN_AFTER_CHECK` | 2 | BTN with `As Qs` |
| 5 | `BTN_AFTER_CHECK` | 3 | BTN with `Qh Th` |

Questions and feedback carry both index spaces. Each decision is an explicit typed union containing exactly one unchanged original question or grade. A grade requires the complete cached question, including its source-local index. Review choices submit only collection index, original source pin and action. Every grade is rebuilt from the corresponding qualified cache. No request accepts imported EVs, feedback, solver configuration or file paths.

## Startup and offline use

The v2 manifest has exactly three ordered sources, each with a source kind, sibling request/report filenames and exact report hash. Before expensive replay, the loader bounds and parses the manifest, validates the collection identity, rejects input aliases, checks all three request/report matches and pins, and checks that the underlying connected physical game is identical. It snapshots real paths, file identities, sizes, modification times and stored SHA-256 values for all seven inputs.

It then performs each source's complete original physical qualification. The old BB reference tier and BTN's shared 80,000/160,000 runs retain their original scopes and limits. The constructor rechecks compatibility of the owned public contexts; the loader verifies all snapshots and the final owned collection hash before returning an immutable cache. Grades and reviews subsequently perform no file access, LP solve or CFR training.

After building the solver and its runtime dependencies, run `com.pokerlab.solver.SixMaxMixedFlopIntervalCollectionMain`:

```text
create <manifest> <collection-hash> <new-review.json[.gz]> [0:check 1:fold 2:bet ...]
replay <manifest> <collection-hash> <review.json[.gz]>
```

The CLI rejects invalid indices/actions and protects the manifest and all six source files against output aliases before replay. Files use exclusive creation. Reviews accept zero to 100 attempts across all six questions, including retries, and enforce 1 MiB stored/expanded caps for plain or gzip JSON. The canonical serializer also supports cached review export. Counts distinguish distinct contexts from repeated attempts, without reporting cumulative full-hand EV or general training accuracy.

The saved [manifest](data/supplied-six-question-flop-collection-manifest.json) binds collection `1694d51922de3619a3523b9ed07dbe6375a742c882632e18eafc3f7fe7c6cb2b`. The [review](data/supplied-six-question-flop-collection-review.json) contains both legal moves for all six contexts: twelve attempts, six within-limit moves and six outside-limit moves.

## Verification

```sh
python scripts/check-mixed-flop-interval-collection.py \
  docs/data/supplied-six-question-flop-collection-manifest.json \
  docs/data/supplied-six-question-flop-collection-review.json
```

The independent checker verifies all three original source proofs and source drills before comparing the complete mixed inventory and every attempt. Its receipt binds all three source receipts, 67 original endpoint certificates, 18 policy/context checks, exact source-local indices, the checker bytes, manifest bytes and stored/expanded review bytes. It runs no optimizer and does not claim fresh CFR execution; the owned Java replay establishes that separately.

Regression tests cover genuine full startup, all twelve moves, both index spaces, source pins, wrong unions/menus, 0/100/101 attempts, exact replay and canonical bytes, bounded gzip/plain persistence, strict JSON, source/output aliases and rehashed source edits. Python corruption controls independently reject altered inventories, cross-hand grades, counts and malformed manifests. The new Java class is assigned normally to solver shard 0; all existing class assignments, test limits and five-group v3 evidence accounting are preserved.

## Cached API and local opt-in

Base route: `/api/v1/trainer/research/flop-interval-collection-v2`. It is absent by default. Operator settings are `TRAINER_MIXED_FLOP_INTERVAL_COLLECTION_ENABLED`, `TRAINER_MIXED_FLOP_INTERVAL_COLLECTION_MANIFEST_PATH` and `TRAINER_MIXED_FLOP_INTERVAL_COLLECTION_HASH`. These settings are never accepted from HTTP clients.

| Request | Result |
| --- | --- |
| `GET /` | Collection pin, six ordered kinds, research provenance and attempt limit |
| `GET /questions/{0..5}?collectionHash=...` | Exact cached typed public question |
| `POST /grade` | Grade from collection pin, question index, original report pin and action |
| `POST /review` | Exact mixed review from collection pin and ordered explicit choices |
| `POST /review/export` | Canonical UTF-8 review attachment from the same choices |

Bodies have a 32 KiB cap and reject unknown/duplicate fields, trailing JSON, scalar coercion, missing pins, invalid indices and more than 100 choices. Nested choices accept only `questionIndex`, `reportHash` and `action`; clients cannot supply source-local indices or grades. Success responses use `Cache-Control: no-store`. Explicit uniquely named request schemas document the strict byte-body parser in OpenAPI.

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-mixed-flop-interval-collection.yml up --build -d
python scripts/smoke-mixed-flop-interval-collection.py
```

All seven manifest/source mounts are read-only. The smoke allows 900 seconds for the combined startup replay. A dedicated 25-minute CI job checks default route absence, performs full production startup and exercises all six questions/twelve grades, exact export, request schemas and negative controls. The existing Compose job and every old wait/work limit remain unchanged. API transport tests use a mocked immutable cache to isolate parsing/delegation; genuine owned construction is tested in the solver and the production startup smoke, without manufacturing qualification capabilities.

## Six-question table UI

Open `#interval-mixed-flop` after enabling the overlay. A shareable link binds the collection pin and collection question index, for example `#interval-mixed-flop?collection=1694d51922de3619a3523b9ed07dbe6375a742c882632e18eafc3f7fe7c6cb2b&question=2` for BTN with 9s Js. All six questions load and pass the original source validators and shared public-context checks before any table is playable. An unavailable or stale source produces an explicit error rather than demo content.

The table switches the blue hero, red opponent, private cards and action menu for each context. Six direct tabs, previous/next controls, keyboard action shortcuts and feedback sit beside the table controls. Action EV/loss ranges appear after answering and remain visible when retrying that same decision. Grades stay attached to their own question; navigation preserves them in the current in-memory session. Reloading or changing the URL starts a new session. These are independently graded decisions, not a connected full-hand policy.

The session permits 100 total attempts across all six contexts. Pending clicks and keyboard repeats cannot duplicate a request; navigation waits for the request, and route changes abort loading, grading, review or export. The review counts question coverage separately from retries. Its download posts only collection-index choices and original report pins, validates all returned questions, grades and counts against the captured session, then saves the server's original UTF-8 JSON. The twelve-choice reference session downloads as exactly 40,731 bytes, SHA-256 `0914137826345fb96293a87ba252801d85ee8386197e32f34182a146d7e6b40f`.

Frontend tests use a byte copy of the physically replayed saved review; CI compares the fixture with the owned artifact. Parser and page tests cover all twelve legal moves, both index spaces, actor/card/menu changes, retained feedback, the global attempt cap, stale/disabled/inconsistent data, forged review/export responses and aborted late responses. Live browser checks cover desktop 1366×768 and mobile 390/320 widths. The original BB, BTN and v1 collection pages retain their source protocols.

## Next model milestone

This completes table integration within the existing one-bet, two-active-player supplied-belief model. Before expanding lessons, measure broader hand/board coverage with explicit solver budgets and the same independent qualification gates. Richer betting, range provenance, rake and genuinely multiplayer postflop equilibrium remain separate work. AWS deployment remains paused.
