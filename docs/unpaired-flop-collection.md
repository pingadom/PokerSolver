# Eighteen-question backend trainer collection

The v6 collection joins the existing fourteen decisions with all four qualified BTN fold/call decisions on **6♦ T♦ Q♣**. It exposes eighteen ordered questions, cached EV/loss feedback and an exact review/export protocol at `/api/v1/trainer/research/flop-interval-collection-v6`.

This remains a restricted, supplied-prior research trainer with two active players at a six-seat table, folded-card blockers, no rake and bounded betting. The four new hands use one declared physical game; they are not four independent ranges. The four zero-reach after-check cases from the discovery study remain rejected and are not included. See [the new source and its qualification](unpaired-flop-source.md).

## Startup and source identity

The manifest pins the old v5 collection, its exact manifest bytes, the unpaired source request/report bytes and its qualified report hash. The collection pin is:

```text
b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2
```

The loader captures all eighteen dependency files under the manifest's real directory. It rejects path escapes, aliases, changed files, inconsistent source inventories and incorrect aggregate budgets. It checks the four expected private hands and the new physical board before optimizer work. After full replay, every dependency must still match its original snapshot.

One complete startup reserves **seven source replays, fourteen fresh reference runs and 1,212,000 reference iterations**. Every original source gate, numerical tolerance and work cap is preserved. Source qualification runs once during startup; metadata, question, grade, review and export requests use the owned caches. Startup can take several minutes and is deliberately opt-in.

Questions 0–13 contain the exact original v5 questions. Questions 14–17 map to local unpaired-source indices 0–3: 2♣3♣, A♥K♥, A♠Q♠, Q♥T♥. The same private cards on another board have a different report pin and global question identity. A grade cannot borrow another question's cards, board, EV or source identity.

## API

The API is off by default. Enable the separate configuration with:

```text
TRAINER_UNPAIRED_FLOP_COLLECTION_ENABLED=true
TRAINER_UNPAIRED_FLOP_COLLECTION_MANIFEST_PATH=<absolute path to supplied-eighteen-question-flop-collection-manifest.json>
TRAINER_UNPAIRED_FLOP_COLLECTION_HASH=b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2
```

| Request | Result |
| --- | --- |
| `GET /flop-interval-collection-v6` | Metadata, exact pin, limits, research status and startup budget |
| `GET /flop-interval-collection-v6/questions/{index}?collectionHash=...` | One public question without private ranges or solver witnesses |
| `POST /flop-interval-collection-v6/grade` | Grade one explicit collection pin, index, report pin and legal action |
| `POST /flop-interval-collection-v6/review` | Regrade at most 100 explicit choices and count distinct questions separately from attempts |
| `POST /flop-interval-collection-v6/review/export` | The same canonical review as a downloadable JSON attachment |

All paths are relative to `/api/v1/trainer/research`. Responses use `Cache-Control: no-store`. Choice bodies are bounded to 32KiB and reject unknown fields, duplicate JSON keys, trailing documents, null entries, non-integer indices and scalar coercion. Clients cannot submit their own questions, EVs, grades or counts. Reviews retain the 1MiB expanded cap and 100-attempt limit.

The offline `SixMaxUnpairedFlopCollectionMain` supports `create <manifest> <pin> <new-review.json> [index:action ...]` and `replay <manifest> <pin> <review.json>`. Its generation budget is declared before the first complete physical startup. The saved review covers both legal moves for every question: 36 attempts, eighteen distinct questions, eighteen within-limit and eighteen outside-limit grades.

## Run and verify

`docker-compose.trainer-unpaired-flop-collection.yml` supplies the new configuration and eighteen read-only dependency mounts. Compose it with the base `docker-compose.yml`. The dedicated CI smoke first proves that all five routes are absent without opt-in, then enables the real production service, waits for all seven qualifications, checks every public question and all 36 grades, and compares canonical export bytes. It also checks OpenAPI request schemas, malformed requests and empty/repeated reviews.

Java tests exercise complete physical startup, all question mappings, byte-identical export, same-card/different-board rejection, stale pins, alias/path/snapshot protection and real Spring controller validation. The independent Python checker verifies all seven source proofs and exact review composition without LP or CFR optimization; corruption controls alter source kinds, private cards, indices, EVs, grades and coverage.

The v5 and earlier protocols, all 311 older data files and the current browser session remain unchanged. This release adds backend capability; website controls for v6 are a separate step. AWS deployment remains paused.
