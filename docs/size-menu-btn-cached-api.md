# BTN sizing startup and cached research API

The four [joint-sizing BTN responses](size-menu-btn-quarter-study.md) now have
their own default-disabled API. They remain research drills for two active
players at a six-seat table, within one selected physical flop and a restricted
bet-size menu. This does not expand the game tree or enable public GTO admission.
The existing twenty-question v7 collection, its hashes and its preview are unchanged.

## Startup contract

`SixMaxSizeMenuBtnDrillLoader.load(manifest, studyHash)` accepts the published
`supplied-size-menu-btn-quarter-drill-manifest.json`. The exact study pin is:

```text
c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc
```

The loader captures bounded regular files in the manifest's real directory.
Dependency names must be simple basenames; escaping paths, same-file aliases,
oversized stored/expanded inputs, changed files and incorrect byte pins fail
startup. It parses the captured bytes with duplicate-key detection, no unknown
fields, no scalar coercion and no trailing JSON values. It checks the complete
canonical report identity and original request before numerical work.

It then reconstructs the physical game once, replays all eight fold/call
endpoints and both shared fresh references, and requires full report equality.
Only this owned result can create the immutable four-question/eight-grade cache.
The source paths, identities, timestamps, sizes and byte hashes are checked again
before returning the bean. A parsed report alone cannot supply capabilities.
Any failure prevents the enabled application from starting; no partial cache or
fallback is published.

The fixed startup reservation is one physical game, at most nine parent solve
calls/eighteen parent LPs, thirty-two conditional LPs, and two shared reference
runs of 80,000 and 160,000 iterations (240,000 total). These are the original
study budgets. Startup is synchronous and can take a few minutes; allow it to
finish before readiness. Requests never rerun it.

## Enable locally

Set the following Spring properties when launching the existing API:

```properties
pokerlab.trainer.size-menu-btn-enabled=true
pokerlab.trainer.size-menu-btn-manifest-path=/absolute/path/to/docs/data/supplied-size-menu-btn-quarter-drill-manifest.json
pokerlab.trainer.size-menu-btn-study-hash=c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc
```

Copy the manifest and its two original source files together if using another
directory. Enabling requires both settings. Leaving the enable property absent
creates no drill bean and all routes below return 404. There are no default
properties or deployment changes enabling this work.

## HTTP contract

Base: `/api/v1/trainer/research/size-menu-btn-drill`.

| Method and suffix | Result |
| --- | --- |
| `GET` | Study hash, model/EV scope, research status, six seats/two active players, four questions/eight grades, 128-attempt cap and startup budget |
| `GET /questions/{sourceCaseIndex}?studyHash=…` | Original source index, hero cards, public table/actions and both move EV/loss intervals |
| `POST /grade` | Cached move and classification under the exact study pin |
| `POST /review` | Canonical attempts, cached grades and honest counts |
| `POST /review/export` | The same review as deterministic UTF-8 JSON with an attachment filename |

Every successful response uses `Cache-Control: no-store`. Questions contain
six public player states and the current BTN decision: pot 8.125 bb, call price
1.625 bb, board `6d Qc Td`, and BB's quarter-pot bet in the public history.
They omit dealt opponent cards, joint worlds, strategy profiles and LP witnesses.
EV is absolute terminal profit in bb from the hand's start, conditional on the
selected decision and forced move with optimal future continuation; it is not
the extra amount won from clicking a button. Loss compares against the best
legal move within the certified strategy set.

Grade body (indices 0–3 retain their original study meaning):

```json
{
  "studyHash": "c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc",
  "sourceCaseIndex": 0,
  "action": "fold"
}
```

Review/export body:

```json
{
  "studyHash": "c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc",
  "choices": [
    { "sourceCaseIndex": 0, "action": "fold" },
    { "sourceCaseIndex": 3, "action": "call" }
  ]
}
```

Only `fold` and `call` are legal. Reviews allow zero to 128 choices, preserve
order and repeated attempts, and count distinct questions separately from
attempt outcomes. All eight legal choices yield four within-limit and four
outside-limit results. Clients submit choices, never EVs, grades, contexts,
solver inputs or previous review results. Stale/foreign pins, missing/null
fields, invalid indices, unknown fields, duplicates, scalar coercion, trailing
JSON, more than 128 choices and bodies over 32 KiB return 400. This matches the
existing research API's input-byte cap; it is an application parser limit, not
a transport-level streaming limit.

## Verification and next integration

Local validation reserves one full replay in the real Spring HTTP test context;
loader corruption tests fail before replay. The HTTP suite exercises every
question and cached legal grade, ordered reviews/exports, empty/repeated/maximum
sessions, malformed requests and default-disabled behavior. It temporarily
removes only copied startup files after startup and checks every route still
works from the same owned cache. Existing published source files are untouched.
CI runs the same real startup test once within its complete backend inventory.
No new numerical source generation, relaxed gates or alternate solver mode is used.

Next, adapt the poker-table UI to this separate protocol and validate its real
HTTP path before extending the combined collection. `trainerAdmission=false`
remains explicit throughout this milestone.
