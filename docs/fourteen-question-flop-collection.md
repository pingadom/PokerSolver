# Fourteen-question backend collection

The separate v5 collection joins the original eleven qualified questions to the three shared-reference call decisions. It exposes metadata, indexed public questions, cached grades and canonical reviews through a default-disabled HTTP API. The existing website, routes v1–v4, source reports and saved sessions remain unchanged.

## Ownership and source identity

`SixMaxCallFlopCollection` retains two typed cache entries: the complete original v4 collection and the owned three-question batch. Global indices 0–10 map to the original eleven questions unchanged. Indices 11–13 map to batch-local indices 0–2: AhKh, AsQs and QhTh facing BB's half-pot bet on 8d Kc Kd. Each decision/grade has exactly one matching typed source; its full nested source question and report pin are retained.

There are six underlying qualified sources but only two supplied input/belief sets. The batch deliberately shares the weak-fold source's physical input. Its two freshly initialized reference policies are assessed at all three selected questions; they are not six different policies or three new ranges. The API metadata states this sharing and the two input sets explicitly.

The v5 pin is `62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619`. Its identity binds the legacy collection hash and exact manifest bytes, batch report hash, batch request and stored-report bytes, shared input hash, schema, selection and beliefs. The loader verifies that the batch input equals the declared legacy weak-fold input. It preserves the older v4 distinction between its original and weak-range games.

## Complete startup, cached requests

The manifest reserves **six source replays, twelve fresh reference runs and 972,000 iterations**: the unchanged original 732,000 plus the batch's 240,000. Existing source loaders run unchanged. No saved reference policy is silently reused in place of a declared fresh run.

One outer snapshot covers all fifteen files: v5/v4/v3 manifests and every source request/report. Files must be bounded regular files under the manifest root. File identities, modification times and SHA-256 values are checked before and after the complete startup. Aliases and directory traversal are rejected. The loader re-derives the dependency list after taking snapshots, preventing a nested manifest change from substituting files outside that captured inventory. Only both complete owned caches can construct the collection; partial startup cannot publish a trainer.

HTTP calls use that immutable cache. Client bodies contain collection/source pins, question indices and legal choices; they cannot provide EVs, questions, success flags or grades. Parsers reject duplicate/unknown fields, trailing JSON, scalar coercion and fractional indices. Bodies are bounded to 32 KiB and reviews to 100 attempts. Successful responses are `no-store`.

Review counts distinguish distinct questions from repeated attempt classifications. The committed complete example has 28 attempts across all fourteen questions: fourteen within-limit and fourteen outside-limit moves. Its robust moves are two folds, one check, seven calls and four bets. Source-specific boards, acting players, legal actions, supplied beliefs, reach units and terminal-profit EV scope remain attached to every question.

## Run and inspect

The overlay loads the real source files read-only and enables only the separate v5 API:

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-call-flop-collection.yml up --build -d
```

Explicit settings are `TRAINER_CALL_FLOP_COLLECTION_ENABLED`, `TRAINER_CALL_FLOP_COLLECTION_MANIFEST_PATH` and `TRAINER_CALL_FLOP_COLLECTION_HASH`. Defaults create no collection bean or v5 routes. Full offline qualification runs before readiness and may take several minutes; it never runs per grade/review request.

The route prefix is `/api/v1/trainer/research/flop-interval-collection-v5`:

- `GET /` returns provenance, sharing, startup budget, question kinds and limits.
- `GET /questions/{index}?collectionHash=<pin>` returns the exact public question.
- `POST /grade` takes `collectionHash`, `questionIndex`, `reportHash`, `action`.
- `POST /review` and `/review/export` take `collectionHash` and an ordered `choices` list, each with `questionIndex`, `reportHash`, `action`. Export returns canonical downloadable JSON.

`SixMaxCallFlopCollectionMain create <manifest> <pin> <new-review> [index:action ...]` and `replay <manifest> <pin> <review>` offer offline use with compiled solver dependencies. Each invocation completely loads the six sources once; applications should retain one loaded cache for repeated interaction.

## Evidence and limits

The committed generation budget predates the single generation invocation. The manifest, 28-attempt review and new independent receipt are separately versioned artifacts; all 295 earlier data files remain unchanged. The Python checker composes the complete original physical/source proofs with the shared batch's 24 endpoint certificates and nine policy assessments, then reconstructs every typed question, grade, source mapping and coverage count. It performs zero fresh LP/CFR solves. Full Java replay establishes owned qualification and fresh production.

Tests cover real six-source solver and Spring startup, every question and legal move, default absence, request validation, pinned files, hardlink aliases, nested dependency changes, forged values/indices/counts, empty/subset/repeated reviews and exact export bytes. A dedicated Docker CI smoke checks actual default404 behavior, real enabled startup, all 28 grades, OpenAPI request schemas and canonical exports. Existing CI checks, work limits and numeric gates remain in place.

These remain bounded two-active-player research games at six-seat tables with supplied priors, no rake and limited betting. The separate [fourteen-decision table UI](fourteen-question-flop-ui.md) connects this API to action EVs, feedback and saved reviews. Broader ranges, boards, later streets and six active players remain separate solver work.
