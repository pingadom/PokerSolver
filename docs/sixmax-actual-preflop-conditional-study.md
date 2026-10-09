# Actual-card conditional preflop solver and offline trainer

PokerLab now solves a bounded **CO versus BB preflop decision at a six-seat table** using its own affine sequence-form solver. Two smaller raise menus pass complete independent feedback checks and supply an offline drill with a public table, action EVs and ten-question reviews. A larger menu correctly exports diagnostics without a usable policy.

This is a separately identified conditional research game: exact physical cards, fixed action-conditioned source beliefs, 100bb stacks, no rake, and mandatory checkdown after a non-all-in call. It is **not a six-player cash equilibrium or general poker lesson**. Every artifact and trainer response remains `VALIDATION_ONLY`, `trainerAdmission=false`; no public API or website pack is registered. AWS deployment remains paused.

## What is solved

The complete, validated source is `docs/data/sixmax-staged-three-nine-source-pack.json`. The frozen public prefix is UTG fold, HJ fold, CO raise to 3bb, BTN fold, SB fold. BB faces 2bb to call into a 4.5bb pot. CO and BB are the only remaining decision makers; all six players' original cards still affect the available board and showdown shares.

The source's saved strategy supplies the likelihood of **every prefix action in every joint deal**. Multiply that likelihood by the original joint chance probability, then normalize. The resulting twelve-world posterior retains hidden folded cards and correlations; it is not a product of independently reconstructed ranges. Prefix reach is `0.049379766642009104`. CO's two hands are AhKh/QhTh, BB's are 7d7h/AcJc. These are deliberately small synthetic supports, not credible full cash ranges.

Only exact, no-rake source payoffs are accepted. Showdown estimates are reused by literal **all-six-card hands plus active-seat mask**; each new terminal recomputes commitments, uncalled returns and side-pot settlement. Changing raise sizes never relabels the source policy as a new solution. The new specification, source pack/policy/payoffs, posterior and immutable solver snapshot all have separate bound identities.

The `NEXT_TARGET` rule offers the next staged raise size at each node. The three tested ladders are 3/9, 3/9/22 and 3/9/22/50/100bb. Calls that leave chips behind terminate in mandatory checkdown. This makes the future tree finite and explainable, but excludes strategic postflop betting.

## Solving and checking feedback

`SixMaxHeadsUpPreflopGame` constructs the complete conditional tree and exact terminal utilities. `FiniteTwoPlayerAffineSequenceForm` compiles the original perfect-recall flows, removes dependent variables with exact integer projections, solves both owned LPs, reconstructs every original sequence and independently checks full-game best responses. Original depth, state, sequence and arithmetic-work caps are unchanged. The strategy's `CfrSolution(1, ...)` is a container convention, not one iteration of training.

`SixMaxHeadsUpPreflopDecisionValues` then visits **every own-card/public-history information set**, including zero-policy-reach rows. A reached question conditions the joint world distribution on that player's own cards and the complete on-policy action history. For each legal action, it holds that same question posterior fixed and finds the player's best subsequent continuation against the fixed opponent policy. This values alternative raises as well as the played action.

EV is measured from the current decision: terminal net profit plus the player's existing commitment. Thus folding is 0bb; chips already committed are sunk. The report separates full policy regret, immediate action-mixture regret and later continuation regret. They are not interchangeable.

Two independent, fresh CFR+ references run for 500 and 1,000 iterations, with exact fixed-utility pruning of folded players. Each reference must pass the unchanged 0.001bb full-game deviation gate. At every material primary decision, both references must also pass:

- Every alternative action EV changes by at most 0.01bb under the fixed primary question posterior.
- Either policy's action mixture loses at most 0.01bb under the other's action values.
- The reference's own reached private posterior is nonzero and within 0.01 total variation.

Material rows require public-prefix probability at least 0.01 and own-hand conditional probability at least 0.05. Accepted study content requires all material rows to pass, at least two material private combos per active player, and source-history reach at least 0.0001. Zero-reach and nonmaterial rows receive no stable credit. Acceptance permits the **offline research drill only**, never general trainer admission.

| Raise ladder | Complete states | All / material information sets | Stable at both budgets | Largest action-EV drift | Export |
| --- | ---: | ---: | ---: | ---: | --- |
| 3/9 | 73 | 4 / 4 | 4 / 4 | 0.00002623bb | Policy + report |
| 3/9/22 | 109 | 6 / 5 | 5 / 5 | 0.00838220bb | Policy + report |
| 3/9/22/50/100 | 181 | 10 / 5 | 0 / 5 | 0.81250137bb | Report only |

The largest menu's primary solver gap is below `4e-16bb`. Its 1,000-iteration reference gap is `0.00082523bb`, yet only three of five material questions pass that reference and the largest action-EV disagreement remains `0.77884517bb`. Small exploitability alone therefore does not certify reliable feedback at every alternative action. The screen rejects this content without weakening thresholds or assuming more iterations solve policy selection.

Conditional CO values are 0.76046701bb for the smallest ladder and 0.78905506bb for each larger ladder. Those values belong to different fixed games; they do not establish improvement in the original six-player strategy or expand the physical-flop retained-coverage result of 0.131820%.

## Replay and practice

Saved files use `docs/data/heads-up-preflop-<three-nine|three-nine-twentytwo|five-target>-`. Each has `specification.json` and `report.json`; only the first two have `policy.json`.

Exact replay validates the complete source, rebuilds the posterior/tree, reruns the owned solver and both fresh references, and compares every report, policy, lineage, action EV and audit field. Recomputing a forged report's hash cannot manufacture an opaque solved result. Rejected studies cannot supply a stand-in policy. Strict bounded JSON and expanded gzip limits apply; CLI aliases, hardlinks and existing output paths are rejected before inputs are loaded. Output files use exclusive creation.

After compiling/installing the modules, run from the repository root:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHeadsUpPreflopStudyMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-three-nine-twentytwo-policy.json docs/data/heads-up-preflop-three-nine-twentytwo-report.json'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHeadsUpPreflopTrainerMain' '-Dexec.args=question docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-three-nine-twentytwo-policy.json docs/data/heads-up-preflop-three-nine-twentytwo-report.json 711'
```

Use `grade` with the same paths/seed followed by a displayed legal action. Use `session` with those paths and a session seed to show ten ordered questions, then `review` with the same session seed followed by exactly ten legal session actions. The Java trainer's `sessionQuestion(seed, index)` reproduces indices 0–9. Questions show only hero's cards, public history, all six public seat statuses/stacks/last actions and legal choices. Feedback supplies frequencies, per-action EVs and EV loss. Question selection is uniform over stable material information sets, explicitly not real deal frequency. The backend rejects altered questions, wrong study identities, illegal actions and incomplete reviews.

To solve a new specification, use `SixMaxHeadsUpPreflopStudyMain solve <source> <specification> <new-policy> <new-report>`. Rejected runs write only their report. `.json.gz` outputs and replay are supported. Each offline trainer CLI command replays the whole study before returning content; this is not a request-time API design.

## Verification and next scope

JUnit independently enumerates literal pure hero plans to check every primary and reference action EV, including off-policy actions. It compares all 48 original-menu terminal worlds against the original six-seat game's payouts, checks posterior likelihoods/dead cards, replays accepted/rejected artifacts, and tests self-consistently rehashed changes to LP audits, policy, posteriors and reference evidence. Additional controls cover strict JSON, gzip expansion, path aliases/hardlinks, public-card isolation, deterministic grading and complete session totals.

```powershell
mvn -q -pl solver -am test '-Dtest=SixMaxHeadsUpPreflop*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The next useful expansion is a separately identified continuation model that keeps action feedback stable on a wider raise ladder, followed by defensible range provenance and explicit rake. General six-player solving, broader retained physical-board coverage and connected postflop practice remain separate gates. This milestone establishes a reproducible actual-card conditional solver-to-trainer path, not a completion percentage for those larger goals.

## Additional offline drill: limp–re-raise

A separately screened public prefix is now saved as `heads-up-preflop-limp-reraise-{specification,policy,report}.json`. UTG and HJ fold; CO, BTN and SB call; BB raises to 3bb; CO re-raises to 9bb; BTN and SB fold. BB faces 6bb to call into a 14bb pot. The table preserves each folded caller's 1bb contribution. If BB raises to 22bb, CO faces 13bb to call into 33bb.

The new prefix has source-policy reach `0.0006611382850751814`. It is rare but passes the unchanged `0.0001` history-reach requirement. It retains all twelve correlated physical worlds, with a new posterior/snapshot identity. Its remaining 73-state unrestricted game uses the 3/9/22bb ladder and the same mandatory-checkdown boundary. All four material decisions (two own hands for each of CO and BB) pass every existing gate at both fresh reference budgets; maximum action-EV drift is `0.00005502bb`. This supplies a third deterministic **offline validation-only** trainer scenario, not a recommendation to limp–re-raise in cash games or general trainer admission.

Use the existing replay and practice commands with the new prefix:

```text
java -cp <solver-runtime-classpath> com.pokerlab.solver.SixMaxHeadsUpPreflopStudyMain replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-limp-reraise-policy.json docs/data/heads-up-preflop-limp-reraise-report.json
java -cp <solver-runtime-classpath> com.pokerlab.solver.SixMaxHeadsUpPreflopTrainerMain session docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-limp-reraise-policy.json docs/data/heads-up-preflop-limp-reraise-report.json 711
```

JUnit replays the complete study, independently enumerates every candidate/reference action continuation, checks the actual pot/contribution/action display for both active players, and verifies deterministic ten-question grading and cross-study rejection. No API or website pack is registered.
