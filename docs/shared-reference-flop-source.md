# Shared-reference flop source

This backend source qualifies three additional BTN facing-bet decisions in one physical game. It builds the game once and trains **two fresh CFR+ reference policies**, then checks both policies and the primary candidate at **every question**. The original eleven-question website remains unchanged; this source is available through the owned Java API and offline CLI, with no HTTP route or deployment added.

## Model and results

The separately supplied weak-range game has 24 joint six-hand worlds, four folded players and two active players. BB calls preflop; the board is **8d Kc Kd**, the flop pot is 6.5 bb and BB bets 3.25 bb. BTN chooses fold or call. The betting model allows one bet followed by exact checkdown, with 100 bb starting stacks and no rake. These are three selected contexts in the same supplied belief set, not three new ranges or connected full hands.

| BTN cards | Within the original 0.01 bb loss limit | Robust lower bound on fold loss |
| --- | --- | --- |
| Ah Kh | Call | 8.516862588 bb |
| As Qs | Call | 3.162948975 bb |
| Qh Th | Call | 1.109022630 bb |

All three primary candidates have conditional counterfactual question reach 0.25 and selected-decision regret zero. Every original v8 conditional report equals its earlier discovery control. The original single-question qualification code is unchanged and supplies the actual Java assessments, including full-parent and reached-flop gaps, original security face, actual and counterfactual reach, forced-action utilities, losses, selected-policy regret and interval inclusion.

The new report pin is `55902ac40959215482cbf3d0b623da7d64313847f7c58c14a81f867d76f22e90`. Stored gzip size is 235,833 bytes; expanded JSON is 2,992,347 bytes, within the unchanged 8 MiB cap.

## Work, sharing and ownership

The new request fixes the parent game, explicit v8 positive-reach endpoint mode, ordered distinct BTN hands, preflop history, original 0.01 bb threshold and original 80,000/160,000 reference tier. It bounds the number of questions to one through four and checks every selection against the owned physical game before optimization.

The committed generation reservation was written before generation: one physical game, three conditional studies, at most six action computations and nine parent solves, two fresh reference runs totalling 240,000 iterations, nine selected-policy assessments and zero retries. Parent solves and endpoint certificates are **not** shared or reported as saved work. Replaying the source repeats that same reservation; tests and other validation replays are additional work, not included in generation's 240,000 iterations.

Both fresh solvers start from empty regrets through the existing immutable traversal and validated inactive-seat utility proof. The source stores each reference policy/work audit once. Each question contains its own primary assessment and two checks whose hashes bind those exact shared policies. Literal traversal checks account for 635,040,000 reference node visits. Three standalone qualifications would reserve 720,000 iterations; this batch reserves 240,000. That is an iteration reduction, not a claimed threefold end-to-end speedup.

`SixMaxSharedFlopFeedback.Result` and `QualifiedBatch` have private constructors. Only a completed owned solve or full replay constructs them. The whole batch qualifies only if every declared question passes; no subset can create a trainer. Conditional/optimizer errors abort without a capability, retry, replacement hand or algorithm fallback. Parsed reports, passed flags, caller hashes and imported policies cannot create the private capability.

`SixMaxSharedFlopTrainerLoader` bounds and snapshots both files, requires the exact report pin, rejects path/file aliases, performs full physical regeneration with two newly trained references and compares the complete report. It checks file identities and bytes again before publishing the immutable trainer. JSON deserialization or a matching hash alone is insufficient.

Cached questions bind the batch hash, ordered question index, total question count, exact hand, physical table context, model, beliefs, reach units and EV scope. Grades use owned moves and reject caller-edited questions. Reviews support up to 100 explicit choices across the batch and recompute all feedback and counts on replay. Distinct questions and repeated-attempt classifications have separate counters. Review writes are exclusive and bounded; overwriting an existing artifact is rejected.

## Reproduce

From the repository root, build with Java 21 and Maven:

```text
mvn -B -pl solver -am verify -Dtest=SharedFlopFeedbackTest -Dsurefire.failIfNoSpecifiedTests=false
python -m unittest discover -s scripts -p test_check_shared_call_flop_feedback.py
python scripts/check-shared-call-flop-feedback.py docs/data/supplied-shared-call-flop-request.json docs/data/supplied-shared-call-flop-report.json.gz --output <new-receipt.json>
```

With compiled solver dependencies on the classpath, these main classes run offline:

```text
SixMaxSharedFlopFeedbackMain replay docs/data/supplied-shared-call-flop-request.json docs/data/supplied-shared-call-flop-report.json.gz
SixMaxSharedFlopTrainerMain review <request> <report> <pin> <new-review.json> 0:call 1:fold 2:call
SixMaxSharedFlopTrainerMain replay-review <request> <report> <pin> <saved-review.json>
```

`review` and `replay-review` each perform one full pinned startup replay before using cached decisions. An application should call the loader once at startup and retain its trainer for repeated grades/reviews; calling the CLI for each choice would repeat training unnecessarily. No optimizer is invoked by `grade`, `review` or cached `replay(Review)`.

The committed review contains seven attempts covering all three questions: four within-limit calls and three outside-limit folds, with one call repeated. It occupies 31,473 bytes. Its separate independent receipt checks the complete source again and derives every question, grade and coverage count. One local complete pinned loader/review invocation took 67.688 seconds; this is a single end-to-end observation, not a benchmark or a warm cached-grading latency measurement.

```text
python scripts/check-shared-call-flop-feedback.py docs/data/supplied-shared-call-flop-request.json docs/data/supplied-shared-call-flop-report.json.gz --review docs/data/supplied-shared-call-flop-review.json --output <new-source-and-review-receipt.json>
```

The independent Python checker rebuilds all 15,792,192 preflop boards and the 1,321-state physical tree once, verifies parent certificates, checks all 24 question endpoint certificates and all nine policy assessments, and derives literal inactive-seat constants/work. Its hand key is an explicit parameter; the policy checker otherwise matches the original independent gates exactly. It performs zero LP or CFR solves. Original immutable discovery/reference reports supply equality controls, while full Java replay proves new source production.

Adversarial tests cover missing/duplicated/reordered hands, altered game/key/action, incorrect policy sharing/work, missing assessments, reach/gap/regret/value/certificate changes, forged qualification/admission, stale pins, oversized files and edited reviews. A report edited and rehashed with the caller's own pin must still fail complete replay.

## Interview explanation and next milestone

“I separated expensive strategy qualification from interactive training. A batch owns one physical game and two independently initialized reference runs, checks every selected decision against both, and creates an opaque capability only when the complete batch passes. A pinned startup replay then builds an immutable cache. The trainer cannot trust client EVs or deserialized success flags; it uses that cache to grade decisions and replay saved sessions. Sharing the pair cuts the reference iteration budget across these three questions by two thirds without removing any per-question validation.”

The next step is a default-disabled HTTP adapter and a separately versioned collection joining these three contexts to the existing eleven. That would make fourteen questions, with seven robust calls, four bets, two folds and one check. It is not the current website count. Check/bet variety in this weak-range model remains blocked by certified zero reach after BB checks; changing that requires a separately declared game or objective. Broader ranges, boards, later betting, rake and six active players remain separate solver work.
