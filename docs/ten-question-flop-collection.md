# Ten-question flop collection

This collection joins four independently qualified sources into one practice
session. It covers two active players at a six-seat table, two declared boards
and ten independent decisions. It does not provide a connected full-hand policy
or a general six-player GTO solution.

| Collection index | Source index | Hero and decision | Board | Actions |
| --- | --- | --- | --- | --- |
| 0 | 0 | BB, first flop move | 2s 8s Ts | check / bet |
| 1 | 0 | BB facing BTN bet | 2s 8s Ts | fold / call |
| 2–5 | 0–3 | BTN after BB check | 2s 8s Ts | check / bet |
| 6–9 | 0–3 | BTN facing BB bet | 8d Kc Kd | fold / call |

Both four-hand sources use `9s Js`, `Ah Kh`, `As Qs` and `Qh Th` in that order.
The game uses supplied joint hand beliefs, no rake, one flop bet and exact
turn/river checkdown. The new source is the complete four-hand paired curriculum,
not the older single-AsQs drill counted again.

## Backend process

The separate v3 protocol preserves all original source formats and the older v2
collection's same-board compatibility rule. The new collection verifies that
all four requests share the same supplied physical input, preflop path, bet
fraction and teaching loss threshold. It explicitly permits only the two boards
above. Its hash binds the ordered source kinds, report pins, source-local indices,
declared boards and decision contexts.

Construction requires four owned result objects. Parsed reports, client grades
and qualification flags cannot grant grading authority. Startup snapshots the
manifest and all eight distinct source files, checks pins and compatibility,
then physically replays every source. It compares all snapshots again before
returning the immutable cache. The manifest reserves four source replays, eight
fresh reference runs and 492,000 reference iterations. Each original source keeps
its existing compiler, LP, reference and qualification limits. These are work
bounds, not a wall-clock guarantee.

After startup, grading and review use only cached source questions and moves.
There is one global cap of 100 attempts. Coverage counts distinct questions;
retries remain separate attempts. Replay requires exact inventory, feedback,
attempt order and counts. Export uses the same canonical bytes as the CLI.

Collection pin:
`11db67eb188fe4fadf8b370f45ecfc0f29ed5412ae78d69eff514dcecc962122`.

## Running it

The API is disabled by default. Local opt-in:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-expanded-flop-interval-collection.yml up --build -d
python scripts/smoke-expanded-flop-interval-collection.py
```

Allow up to 20 minutes for startup on slower machines. The separate production
smoke tests default absence, full qualification, all twenty legal grades,
source-local indices, canonical export, OpenAPI and malformed requests.

Open `#interval-expanded-flop` in the frontend. Direct links accept the pinned
`collection` and a `question` from 0 to 9. Hero is blue, villain red; the table
shows the board, acting player, prior action, pot and call amount. Action buttons
reveal EV/loss ranges after grading. Navigation retains previous feedback.

The default-disabled API prefix is
`/api/v1/trainer/research/flop-interval-collection-v3`:

| Method and path | Purpose |
| --- | --- |
| GET `/` | Metadata and ordered context inventory |
| GET `/questions/{index}?collectionHash=...` | Public table state |
| POST `/grade` | Explicit collection pin, index, report pin and action |
| POST `/review` | Recompute a session from explicit choices |
| POST `/review/export` | Canonical replayable JSON download |

Unknown fields, duplicate keys, trailing JSON, scalar coercion, stale pins,
incorrect indices, illegal menus and oversized requests are rejected. No HTTP
request selects solver inputs or filesystem paths.

Offline replay:

```sh
java -cp "$SOLVER_CLASSPATH" com.pokerlab.solver.SixMaxExpandedFlopIntervalCollectionMain replay docs/data/supplied-ten-question-flop-collection-manifest.json 11db67eb188fe4fadf8b370f45ecfc0f29ed5412ae78d69eff514dcecc962122 docs/data/supplied-ten-question-flop-collection-review.json
python scripts/check-expanded-flop-interval-collection.py docs/data/supplied-ten-question-flop-collection-manifest.json docs/data/supplied-ten-question-flop-collection-review.json
```

The independent checker verifies all four fixed source proofs and 99 original
endpoint certificates without running an optimizer. Its receipt distinguishes
that verification from the fresh physical work performed by Java.

## Remaining solver work

Ten contexts do not establish balanced strategy coverage: all four paired hands
prefer call, and all four original BTN-after-check hands prefer bet. The next
solver milestone is independently qualified robust fold, robust check and mixed
examples over broader credible ranges. A rejected weak-hand research LP and a
separately tested numerical candidate remain outside production; integrating that
candidate requires a new algorithm version, regression evidence and fresh physical
qualification. Full multiplayer solving, rake and connected streets remain later
milestones. AWS deployment remains paused.

Backend CI now partitions solver tests into eight and non-solver tests into two disjoint hash groups under
the existing per-job time limit. The aggregate evidence verifier still requires
all assigned classes, all five modules, exact commit identity and complete JUnit
accounting; a missing shard or class fails verification.
