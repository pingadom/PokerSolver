# Four qualified preflop questions

The backend now has four distinct interval questions: three BB responses to a BTN open, followed by the original BB99 re-raise decision. The collection has its own ordered identity, offline review format and opt-in HTTP API. The existing single-question API and browser drill remain compatible; browser navigation through this collection is the next frontend step.

This remains a research trainer with **two active players at a six-seat table**, supplied conditional joint priors, no rake and mandatory postflop checkdown. Source-policy history reach is unavailable, and `trainerAdmission=false` throughout. It does not solve a six-player cash equilibrium.

## Decisions and evidence

All four questions use the same 27-world physical game, the `[3,9,22,50,100]` raise schedule and 17,766,216 enumerated boards. All twelve dealt cards remain blockers. Requests explicitly declare 0.0001 bb security slack, a 0.01 minimum chance/opponent reach and a 0.01 bb feedback loss limit. Numerical certificate tolerance remains 1e-8.

| Index | BB hand and decision | Within limit | Other moves |
| --- | --- | --- | --- |
| 0 | 7♦7♥ facing the 3 bb open | Call | Fold loses about 1.0077 bb; raise to 9 loses 0.8366–0.8421 bb |
| 1 | 9♦9♥ facing the 3 bb open | Raise to 9 bb | Call loses 0.0968–0.0971 bb; fold loses 1.4283–1.4286 bb |
| 2 | A♣J♣ facing the 3 bb open | Call | Fold loses about 1.2739 bb; raise to 9 loses 0.4985–1.0897 bb |
| 3 | 9♦9♥ after BB raises to 9 and BTN raises to 22 | Call | Fold is outside the limit; raise to 50 remains strategy-dependent |

The new [77](data/supplied-root-five-target-77-l1-interval-feedback-request.json), [99](data/supplied-root-five-target-99-l1-interval-feedback-request.json) and [AJ](data/supplied-root-five-target-aj-l1-interval-feedback-request.json) requests each have a corresponding `-report.json.gz`. Each passed an owned solve, physical replay and freshly regenerated CFR+ references at 500 and 1,000 iterations. Both references lie inside the declared face before comparing their EVs and joint losses with the bounds. The original [later BB99 report](data/supplied-conditional-five-target-99-interval-feedback-report.json) is retained unchanged.

## Explicit row ordering and original certificates

`solveAscendingL1JointRows` retains the existing objective-conditioned action/reach controls. It orders only joint-loss LP constraints by ascending `sum(abs(A[i][j]))`, breaking ties by original row index. It does not sort by RHS, alter coefficients, enlarge caps, retry failed representations or replace old v1/v2 paths.

The owned LP solver copies the original problem and uses an immutable per-call permutation. It reserves copying, validation, metric and original-certificate work before ordering, charges every sort comparison, and passes only the remaining budget to the ordered solve. All action/reach and joint controls share the existing aggregate compiler, pivot and arithmetic limits.

After solving, dual multipliers are mapped back to original row indices. Primal feasibility, dual feasibility, both objectives and their gap are recomputed against the original unpermuted inequalities. Only that check creates the immutable result. Its certificate binds the original problem hash and the separate `OWNED_STABLE_ASCENDING_L1_ROWS_ORIGINAL_CERTIFICATE/v2` algorithm. An ordered certificate is never relabelled as an original certificate.

The joint audit identifies `CONDITIONED_STABLE_L1_JOINT_CONDITIONAL_DECISION_LOSS_OWNED_LP/v3`; its wrapper identifies `UPPER_OBJECTIVE_SHIFT_STABLE_ASCENDING_L1_JOINT_ROWS_ORIGINAL_CERTIFICATES/v2`. Feedback request/report schemas `/v2` explicitly select this representation. Old `/v1` requests select the original conditioned representation and exactly replay their old reports.

The 1e-8 root faces still exclude fresh references and cannot qualify. The narrow later-BB99 case still rejects its invalid behavioral witness. Neither failure triggers a fallback or tolerance change. This improves PokerLab's owned numerical implementation; no upstream library defect has been identified.

## Collection identity, ownership and reviews

The [manifest](data/supplied-four-question-interval-collection-manifest.json) declares four ordered request/report pairs and exact report pins. Its collection hash is:

```text
227fd33b33ce8ed8be46af3d0d4515e4c7b017c3468ec6f9a025bf96d67070b2
```

Identity binds selection/schema, ordered report hashes, inputs, heroes and information sets. Reports for the same input/hero/target remain one question even if thresholds or representations differ. Collections contain two to sixteen distinct questions; reviews contain zero to 100 explicit attempts.

The trusted loader first checks strict bounded JSON, sibling filenames, distinct paths, request lineage, every report pin, inventory and order. It then physically replays every source, regenerating boards, LP controls and reference policies. Parsed qualification booleans and matching hashes cannot authorize grading. Only owned qualified results construct a collection; the collection pin is checked again after replay. The original one-question loader remains independent.

Question envelopes bind collection hash, index, total count and selection to the canonical public table. Only hero cards, public history, stacks and legal actions are exposed. Grading requires collection and report pins and returns all three verdicts. Requests use immutable cached evidence and never initiate a solve.

Reviews preserve attempt order, question indices and canonical feedback. `distinctQuestionsAttempted` is separate from verdict counts: 100 retries of one question still cover one question. No midpoint EV or scalar accuracy is invented. Altered grades, counts, pins, indices, question order or actions fail replay.

The [saved five-attempt review](data/supplied-four-question-interval-collection-review.json) covers all four questions and all three verdicts. With Java 21 and the existing solver/engine/Jackson classpath:

```text
com.pokerlab.solver.SixMaxSuppliedIntervalCollectionMain
  create <manifest.json> <collection-hash> <new-review.json[.gz]>
    0:call 1:raise:9.0 2:call 3:raise:50.0 3:fold

com.pokerlab.solver.SixMaxSuppliedIntervalCollectionMain
  replay <manifest.json> <collection-hash> <saved-review.json[.gz]>
```

Create refuses existing output paths. Each command performs physical qualification once per source at startup, then uses cached decisions.

## Separate HTTP opt-in

Set `TRAINER_INTERVAL_COLLECTION_ENABLED=true`, `TRAINER_INTERVAL_COLLECTION_MANIFEST_PATH` and `TRAINER_INTERVAL_COLLECTION_HASH`, or use the local overlay:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-interval-collection.yml up --build
```

The manifest and sibling artifacts are mounted read-only. Startup must finish all four source replays before serving requests. The API is absent by default and uses `Cache-Control: no-store`:

| Endpoint under `/api/v1/trainer/research/interval-collection` | Input |
| --- | --- |
| `GET /` | Metadata with collection pin and four available questions |
| `GET /questions/{index}` | Required `collectionHash` query parameter |
| `POST /grade` | `collectionHash`, `questionIndex`, `reportHash`, `action` |
| `POST /review` | `collectionHash`, `choices: [{questionIndex, reportHash, action}, ...]` |

Missing indices/parameters, stale pins, illegal actions, unknown fields, caller-provided questions/EVs/grades, null choices and oversized reviews reject with HTTP 400. Review output matches the offline format. The existing `/interval` endpoint is unchanged. AWS deployment remains paused.

## Verification

All 292 independent LP controls check original-row primal/dual feasibility, objective agreement and original problem hashes. Analytic tests cover stable ties, signed bounds, degeneracy, deterministic immutable results, reduced work caps, both player orientations, correlated continuations and low reach. Physical golden replay covers all new reports and the unchanged old report. Collection tests cover stale/altered evidence, inventory/order, false coverage, canonical review and plain/gzip round trips. Real Spring MVC tests load all sources and compare HTTP reviews with the offline artifact. Compose checks the default-disabled boundary and actual opt-in startup, navigation, grading and review.

The optional [independent oracle](../scripts/check-supplied-l1-root-feedback.py) builds a literal 729-by-5,832 complete-plan game from saved integer showdown counts. The [recorded check](data/supplied-root-five-target-l1-interval-feedback-independent-check.json) contains **83 fresh SciPy LP solves**, no cached extrema and **72 fresh joint-witness checks** of original security, hidden-world posteriors, behavioral mixtures, continuations and joint losses. It checks recorded reference rows; Java replay regenerates physical boards and reference policies. NumPy/SciPy remain optional offline dependencies.

```sh
python scripts/check-supplied-l1-root-feedback.py \
  docs/data/supplied-root-five-target-77-l1-interval-feedback-report.json.gz \
  docs/data/supplied-root-five-target-99-l1-interval-feedback-report.json.gz \
  docs/data/supplied-root-five-target-aj-l1-interval-feedback-report.json.gz \
  --output <new-independent-check.json>
```

Next: connect the browser to this API with real question progress/navigation. More private hands, active players, postflop betting, rake and source-policy reach need further models and qualification evidence.
