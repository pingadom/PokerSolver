# Eleven-question flop collection

The v4 research collection adds one qualified weak-hand decision to the ten existing BB/BTN decisions. The hero holds **2c 3c** on **8d Kc Kd**, facing a **3.25 bb** BB bet. Folding is within the 0.01 bb loss limit; calling has a certified loss lower bound of approximately **2.2495 bb**. These are absolute terminal-profit values, including money already committed, rather than incremental returns from this decision.

This is a bounded model with two active players at a six-seat table, no rake, one flop bet and exact checkdown. It is not a six-active-player equilibrium, an unrestricted postflop solver, a full-hand policy or general trainer admission. The eleven questions are independent decisions. The new weak-hand source uses different supplied conditional joint hand beliefs from the first ten questions; it must not be treated as another hand sampled from their common game.

## Identity and startup

The new immutable manifest is `docs/data/supplied-eleven-question-flop-collection-manifest.json`. Its collection pin is:

```
57abbb5f5bbc10b2227739299c1eec0f0092a7a136dc80e63c0830865cb7fd87
```

The original v3 manifest, source reports, review schema, API and UI route remain separately versioned. The wrapper composes two owned caches:

| Global questions | Owned entry | Local index |
|---|---|---|
| 0–9 | Fully replayed v3 ten-question collection | 0–9; the nested question retains its original source-local index |
| 10 | Pinned qualified weak-hand trainer | 0 |

The outer hash binds the review schema, ordering, explicit independent-source belief scope, original collection hash, weak report hash and both canonical private input hashes. The source report pins transitively bind each board, public context, policy and all interval evidence. Private hand ranges and server paths are not sent to trainees.

Before replay, the loader validates typed manifest fields, exact pins, different input identities, declared work and input/output aliases. It snapshots the outer manifest, nested manifest, eight original request/report files and two weak-hand files. All twelve snapshots must still match after both entries are completely loaded. A source file cannot change during the other entry's replay unnoticed.

Startup reserves **five source replays, ten fresh reference runs and 732,000 reference iterations**. Every original physical, compiler, LP, certificate and fresh-reference gate remains in force. No rejection or numerical failure triggers another algorithm, relaxed tolerance or automatic numerical retry. This full work is performed before readiness. Question, grade, review and export requests use the resulting caches and perform no fresh solving.

The committed generation budget authorizes one fixture-generation invocation and 22 explicit choices. It does not describe test runs as additional scenarios or claim reference training occurred inside the independent checker.

## Interaction

The separate default-off route is `/api/v1/trainer/research/flop-interval-collection-v4`:

| Request | Purpose |
|---|---|
| `GET /` | Metadata, collection pin, original collection pin, source kinds and scope |
| `GET /questions/{index}?collectionHash=...` | Canonical cached question |
| `POST /grade` | Explicit collection pin, global question index, source report pin and action |
| `POST /review` | Up to 100 explicit choices; server reconstructs every grade |
| `POST /review/export` | Exact replayable JSON bytes and attachment headers |

All successful responses disable caching. The choices endpoints reject bodies over 32 KiB, duplicate/unknown fields, trailing JSON, scalar coercion, null indices, stale pins, illegal actions and caller-supplied EVs or contexts. The public OpenAPI contract describes the choice DTOs rather than a raw byte array.

The review includes all eleven canonical questions and ordered attempts. Distinct questions attempted and repeated attempt classifications are counted separately. Replaying a review recomputes every grade against the owned cache and requires exact equality, including question inventories, source pins, move alternatives, attempt order and counts. Matching JSON flags cannot create grading authority.

The table UI is `#interval-qualified-flop`. It retains the six-seat display, blue hero/red villain, visible prior actions, move-button EV/loss intervals after grading, verdict beneath the actions, direct question links, keyboard actions, retry, navigation and exact JSON download. The weak-hand scenario has an explicit different-ranges label. Its client validator accepts the exact frozen qualified weak question and grades; a changed source requires an explicit client update. Original questions retain their four existing validators. The old `#interval-expanded-flop` route remains usable.

## Running locally

Opt in with:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-qualified-flop-collection.yml up --build -d
python3 scripts/smoke-qualified-flop-collection.py
```

The overlay mounts all twelve inputs read-only and sets the exact collection pin. It does not enable the older collection routes. Allow startup to finish its complete qualification before requesting metadata.

The Vite development server normally proxies this API to port 18080 alongside the other routes. For a separate local qualification process, set `POKERLAB_QUALIFIED_FLOP_API_TARGET` in the ignored `frontend/.env.local` file. Only the new v4 route uses this override; it is not a production hosting setting.

For an offline review, run `SixMaxQualifiedFlopCollectionMain` with the solver dependencies:

```
create <manifest> <collection-pin> <new-review.json> 10:fold 10:call
replay <manifest> <collection-pin> <review.json>
```

The CLI validates output aliases and choices before expensive startup and refuses to overwrite an existing review.

## Independent verification

```sh
python3 scripts/check-qualified-flop-collection.py \
  docs/data/supplied-eleven-question-flop-collection-manifest.json \
  docs/data/supplied-eleven-question-flop-collection-review.json \
  --output new-independent-receipt.json
```

The standard-library checker composes the original v3 physical proofs and the weak-hand physical/LP/reference-policy checker. It independently reconstructs the new drill from the checked report, then checks all eleven questions, both moves in every question, the typed wrapper, review order and counts. It runs **zero fresh LP solves and zero fresh CFR training runs**. Its receipt includes nested evidence and exact source/manifest/review hashes.

The eleven within-limit choices consist of **two folds, one check, four calls and four bets**. This inventory is not balanced game coverage or a measured accuracy score. Both boards, supplied beliefs and the two-active-player scope remain explicit. Broader ranges, more bet sizes, turn/river continuation and genuinely multiplayer equilibrium solving remain separate work.
