# One qualified interval drill

The offline backend can now turn an owned [interval-feedback qualification](sixmax-interval-feedback-qualification.md) into a playable question, grade each legal move and save/replay explicit attempts. The [saved drill](data/supplied-conditional-five-target-99-interval-drill.json) contains the actual six-seat table plus examples for fold, call and raise-to-50.

The format is `pokerlab-supplied-interval-drill/v1`, with publication status `SELECTED_QUESTION_INTERVAL_DRILL_RESEARCH_ONLY`, selection `SINGLE_QUALIFIED_SELECTED_QUESTION/v1` and `trainerAdmission=false`. It has **one independently qualified question**, with two active players. Repeating it does not increase scenario coverage. This is an offline integration, not a public HTTP pack or a six-player cash equilibrium.

## The question

BB holds 9♦9♥. UTG, HJ and CO folded, BTN opened to 3 bb, SB folded, BB raised to 9 bb, and BTN raised to 22 bb. BB acts with 91 bb remaining; BTN has 78 bb. The pot is 31.5 bb including the folded SB's 0.5 bb, and calling costs 13 bb. Legal actions are fold, call and raise to 50 bb total.

`SixMaxSuppliedIntervalFeedback.solve` derives this context from the selected state of the already enumerated physical game. It checks actor, own cards, information-set identity, commitment and legal-action order against the owned evidence. It replays the complete public history into the shared six-seat snapshot. The existing saved qualification report schema and bytes remain unchanged; full replay derives the context again.

The public question binds the exact qualification report hash, information set, declared security slack, reach floor and educational loss limit. It carries supplied-belief/model provenance and unavailable source-policy history reach. Only hero's private cards are exposed. It excludes the joint prior, opponent/folded cards, payoffs, posterior, policies and raw LP diagnostics.

## Grading and review

`new SixMaxSuppliedIntervalTrainer(ownedResult)` requires an opaque qualified result from a complete solve or physical replay. Deserializing a report with `qualifiedForIntervalFeedback=true`, or supplying a matching-looking report hash, cannot construct that result. Both excluded-reference and nonmaterial results are rejected. Existing scalar trainer admission remains unchanged.

`question()`, `grade(question, action)`, `review(question, actions)` and drill replay use immutable cached question/move evidence. They neither enumerate boards nor call a solver. A submitted question must equal the canonical question in every field; legal actions are checked against the owned moves.

Each grade contains the selected action's incremental EV interval and joint decision-loss interval, the declared 0.01 bb loss limit, and all alternatives for a future action-button UI:

| Action | EV interval (bb) | Loss interval (bb) | Result |
| --- | ---: | ---: | --- |
| Fold | approximately 0 | 9.111777–9.112981 | `OUTSIDE_LIMIT` |
| Call | 9.111777–9.112981 | 0–0.000450 | `WITHIN_LIMIT` |
| Raise to 50 | 8.627219–9.112981 | 0–0.484558 | `STRATEGY_DEPENDENT` |

Within-limit means the entire loss interval meets the limit. Outside-limit means the entire interval exceeds it. Strategy-dependent means the available evidence straddles the limit; it is an uncertainty result, not a binary wrong answer. There is no midpoint EV, inferred action frequency, scalar accuracy or sum advertised as realized hand loss.

Reviews accept zero to 100 explicit attempts at this same question. Zero attempts saves a question ready for practice. Each attempt stores its index, action and complete feedback. Counts distinguish all three classifications; repeated attempts remain repetitions of one question. Replay reconstructs every grade and count from the owned qualification and compares the entire review. Altered question fields, grades, alternative ranges, action/index associations or counts fail replay.

## Run the backend demo

Use Java 21 and the existing solver/engine/Jackson classpath. The main class is `com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain`:

```text
create <request.json[.gz]> <report.json[.gz]> <new-drill.json[.gz]> [action ...]
replay <request.json[.gz]> <report.json[.gz]> <drill.json[.gz]>
```

For the checked-in scenario:

```text
java -Xmx2g -cp <classpath> com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain create \
  docs/data/supplied-conditional-five-target-99-interval-feedback-request.json \
  docs/data/supplied-conditional-five-target-99-interval-feedback-report.json \
  <new-drill.json> fold call raise:50.0

java -Xmx2g -cp <classpath> com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain replay \
  docs/data/supplied-conditional-five-target-99-interval-feedback-request.json \
  docs/data/supplied-conditional-five-target-99-interval-feedback-report.json \
  docs/data/supplied-conditional-five-target-99-interval-drill.json
```

Each command first fully replays qualification **once**: all 17,766,216 physical boards, the owned LPs and fresh reference strategies. Startup therefore takes time. Subsequent question/grade/review operations in the constructed adapter use only cached evidence. Create requires a new output path; all input/output paths must be distinct. Strict JSON/gzip loading rejects unknown/duplicate fields, trailing values, null primitive values and scalar coercions. Saved drills have a 256 KiB compressed and expanded cap. Writing validates the review against the owned trainer and never overwrites an existing file.

## Next boundary

The next integration needs an explicit trusted, opt-in runtime registration/loading boundary for these cached owned results, then matching HTTP question/grade/review contracts and the table/action-button UI. No request should solve or enumerate boards, and loading caller-provided JSON must never substitute for physical qualification. A broader collection needs independently qualified questions and an identity binding its ordered reports and selection settings.

Supplied synthetic priors, exactly two active players, mandatory postflop checkdown, unavailable source-policy history reach and no rake remain limitations. Strategic postflop, broader preflop coverage and multiway equilibrium remain separate work. AWS deployment remains paused.
