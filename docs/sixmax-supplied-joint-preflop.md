# Supplied joint-range preflop research

This backend adds a separately identified way to solve two active players at a six-seat table. The caller supplies a **joint distribution of six physical hands conditional on a public preflop history**. PokerLab solves the remaining bounded betting game using its own affine sequence-form LP solver, enumerates exact checkdown payoffs, independently screens every material decision, and offers an offline practice session only when that entire screen passes.

This is a supplied-range research tool. It does not derive opening ranges or the probability of reaching the supplied history. It does not solve a simultaneous six-player equilibrium. Synthetic ranges, no rake and mandatory checkdown after a non-all-in call remain explicit limits. No website/API registration or AWS deployment is added.

## Why this input exists

The existing complete six-seat source has 11,566 public states. Twelve physical joint worlds require 138,792 deal/public states within its 160,000-state cap. Eighteen require 208,188 and fail before payoff enumeration. Adding a hand by silently raising that cap, dropping blockers or pretending a new prior came from the original policy would invalidate the existing evidence.

The new input declares its beliefs directly at the selected history. Only the complete future two-player tree is traversed. Existing source-derived inputs, reports, policies, source-history reach gates and saved replay remain unchanged. Shared local feedback checks run for both models; the new model records source history reach as **`UNAVAILABLE_NO_SOURCE_POLICY`**, rather than inventing a numeric reach or claiming to satisfy the source-history gate.

## Input and budgets

`SixMaxSuppliedRangePreflopGame.Input` contains a version, the public history/rules, and 1–64 weighted joint worlds. Each world lists exactly six literal two-card hands in UTG/HJ/CO/BTN/SB/BB order. All twelve cards must be distinct within a world. Worlds need not form a Cartesian product: correlations and omitted combinations remain intact. Duplicate physical worlds are rejected. Cards and world order are canonicalized; positive finite weights are normalized with overflow protection, and underflowing probabilities are rejected.

The selected history must be legal and leave exactly two live players with future decisions for both. Rules use 1–5 staged raise targets. Preflight builds the entire future public tree and checks all physical inputs before evaluating any boards.

| Budget | Bound |
| --- | ---: |
| Complete future states, including the chance root | 20,000 |
| Depth | 32 |
| Information sets per actor | 64 |
| Sequences per actor | 129 |
| Exact enumerated boards across all worlds | 20,000,000 |
| Hand evaluations across all worlds | 40,000,000 |

Six dealt hands leave 40 cards, hence exactly C(40,5) = 658,008 boards per world. The board budget currently permits at most 30 worlds even though the input schema permits 64. The tighter applicable bound wins. Existing compiler/LP bounds and numerical tolerances are unchanged and remain additional solve-time gates.

`ExactDeadCardHeadsUpShowdown` scores only the two active players on each board. All four folded hands still remove their cards from the deck. It records integer wins, losses and ties, exactly twice as many hand evaluations as boards, and exact shares with zero sampling error. The audit binds every count to its complete physical deal. Settlement preserves folded chips and returns uncalled raises; no independent marginal ranges or sampled payoffs replace these inputs.

## Solve, screening and replay

The primary policy comes from `FiniteTwoPlayerAffineSequenceForm`, with both owned LP certificates, original realization-flow audits and complete information-set best responses. Separate fresh CFR+ runs use the existing 500/1,000 iteration budgets.

The shared screen keeps the existing thresholds: reference whole-game gap at most 0.001bb; every material alternative-action EV and cross-policy mixture regret within 0.01bb; posterior total variation at most 0.01; public-prefix probability at least 0.01 and own-hand mass at least 0.05 for material rows; at least two material private hands per active player; every material row must pass both references. Alternative actions use the **same primary question posterior** and optimal hero continuation against a fixed opponent.

The report contains the complete declared input, normalized joint prior, measured budget, exact physical counts, owned solve audit and every decision/reference comparison. Binding hashes distinguish input, prior, payoffs, model and belief provenance. `qualifiedForOfflinePractice` is separate from global trainer admission, which remains false. An unqualified study exports its diagnostic report and **no policy**. Public JSON constructors cannot manufacture a solved result handle.

Replay requires a separate matching input file. It checks identities, reenumerates every board, reruns the owned LP and both fresh references, and compares every report/policy field. Rehashing fabricated payoff counts cannot pass. Strict JSON, expanded 8MiB limits, optional gzip, distinct input/output paths and exclusive new output files are enforced.

## Saved button-defense example

[Input](data/supplied-range-button-defense-input.json), [policy](data/supplied-range-button-defense-policy.json), [complete report](data/supplied-range-button-defense-report.json).

UTG/HJ/CO fold, BTN opens to 3bb, SB folds. BB may fold, call or raise to 9bb; BTN then folds or calls. Stacks are 100bb and the small blind is 0.5bb. BTN has AhKh, QhTh and Js9s; BB has 7d7h and AcJc. Three possible folded HJ hands create 18 declared worlds. This is an illustrative supplied distribution, not a recommended opening/defending range.

The future game has six public states and 109 complete states, 11,844,144 enumerated boards and 23,688,288 hand evaluations. Its five material decisions all pass both reference budgets. Maximum action-EV drift is about 0.00001573bb. The button's conditional game value is 0.3370787285260969bb; the active-player constant sum is 0.5bb from the folded small blind. These values belong to this exact input, not a claim that the original source policy improved.

A second [input](data/supplied-range-cutoff-threebet-input.json), [policy](data/supplied-range-cutoff-threebet-policy.json) and [report](data/supplied-range-cutoff-threebet-report.json) cover CO facing a BTN 3-bet: UTG/HJ fold, CO raises to 3bb, BTN raises to 9bb, SB/BB fold. CO may fold, call or raise to 22bb, followed by BTN fold/call. The same three button hands face two cutoff hands in 18 newly enumerated physical worlds. All five material decisions pass, with maximum action-EV drift about 0.00004614bb. Both folded blinds contribute 1.5bb to the active-player constant sum; the cutoff value is 0.44996751137101987bb. The future tree again contains 109 states. Each study individually fits the exact-payoff budget.

A third [input](data/supplied-range-expanded-three-target-input.json), [policy](data/supplied-range-expanded-three-target-policy.json) and [report](data/supplied-range-expanded-three-target-report.json) expand BTN/BB to three private hands each and three possible folded HJ hands: 27 complete worlds. BB gains 9d9h and the staged menu includes 22bb. Its 244-state future game enumerates 17,766,216 boards and 35,532,432 hand evaluations within unchanged budgets. All seven material rows pass both references; maximum action-EV drift is about 0.00036383bb. Practice includes BB facing BTN's 22bb re-raise. The button value is 0.2633447521970912bb for this separate input.

For this deeper example an independent 27-by-216 literal complete-plan matrix checks all 5,832 plan pairs and all pure deviations. The EV control maximizes the hero's **aggregate conditional continuation** after an opponent re-raise, choosing one action across the information set rather than a different action in each hidden world.

A separate [stronger-hand input](data/supplied-range-strong-button-rejected-input.json) and [rejected report](data/supplied-range-strong-button-rejected-report.json) demonstrate why whole-game quality alone is insufficient. Both references have gap below 0.000006bb, but material alternative-action EVs differ by up to about 1.49bb. The report remains unqualified and there is no saved policy or playable trainer handle.

## Offline commands

Build with Java 21/Maven and use the solver, engine and Jackson runtime classpath. Commands are class entry points:

```text
com.pokerlab.solver.SixMaxSuppliedRangePreflopStudyMain
  preflight <input.json>
  solve <input.json> <new-policy.json[.gz]> <new-report.json[.gz]>
  replay <input.json> <policy.json[.gz]> <report.json[.gz]>

com.pokerlab.solver.SixMaxSuppliedRangePreflopTrainerMain
  question|session <input.json> <policy.json[.gz]> <report.json[.gz]> <seed>
  grade <input.json> <policy.json[.gz]> <report.json[.gz]> <seed> <action>
  review <input.json> <policy.json[.gz]> <report.json[.gz]> <seed> <ten actions>
```

Every trainer command first completes replay. Questions display all six public seats, contributions, actions, hero cards and legal actions, with explicit supplied-prior provenance. They omit villain/folded cards and strategy/EV answers. Grading recreates the question from its study hash and seed, rejects altered or foreign questions, and returns all move EVs, frequencies and EV loss. Sessions contain ten deterministic questions with aggregate reviews, sampled uniformly over stable material information sets rather than according to poker-hand frequency.

## Validation and next work

Tests compare exact counts against the separate all-subsets enumerator, verify folded-card effects, preserve sparse correlated support, reject work/card/history/input failures before enumeration, and independently calculate every leaf payout and candidate/reference action EV. Independent literal 8-by-9 complete-plan matrices check every pure deviation of both saved equilibria without calling the game, strategy evaluator or best-response implementation. Saved accepted/rejected studies replay, tampered rehashed physical evidence fails, and existing source-derived studies retain exact replay.

Next expand useful supplied scenario/seat-pair support while retaining complete independent EV qualification, then design explicit content registration with provenance visible in the product. Full-range cash realism, rake, broader physical postflop coverage and multiplayer convergence remain separate goals. No upstream library defect was identified in this work.
