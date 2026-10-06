# History-dependent preflop raise schedules

`SixMaxPreflopBetting.Rules` now distinguishes `GLOBAL_TARGETS` from `NEXT_TARGET`. A global `[3,9]` menu permits opening directly to either 3bb or 9bb. A next-target `[3,9]` menu permits a 3bb open and then a 9bb re-raise. Calls and limps do not advance the schedule; after the last target, only the remaining legal calls, checks and folds are available. Every seat can act, including an opener or prior caller facing a later re-raise.

The rules engine still uses integer micro-big-blind accounting, validates acting seats and state ownership, reopens action after a full raise, retains folded contributions and distinguishes uncontested wins, matched all-in showdowns and non-all-in continuation boundaries. Each scheduled increment must satisfy the previous full-raise increment. Invalid schedules fail instead of silently skipping a stage. Equal effective stacks remain a model assumption; short all-ins and unequal-stack side-pot betting are outside this rules engine.

The original three-argument `Rules` constructor retains global semantics. Its canonical JSON still contains exactly `raiseToBb`, `smallBlindBb` and `stackBb` in alphabetical order. New scheduled rules additionally contain `"raiseSchedule":"NEXT_TARGET"`. A dedicated strict decoder defaults only the absent schedule; it rejects missing chip fields, nulls, unknown or duplicate fields, string-to-number coercion, unsupported schedules and invalid chip menus. The rest of the artifact mapper retains all its existing strict settings.

Legacy global packs remain `six-max-preflop-checkdown-pack/v1` and keep their original spot and pack hashes. Scheduled packs use `six-max-preflop-checkdown-pack/v2`, with a distinct spot-hash domain and an explicit schedule in the hash input. The builder selects the matching version; reload rejects either version paired with the wrong schedule. Checkpoints bind both source hashes, so a legacy policy cannot resume against a scheduled source even when the two particular menus happen to have identical actions. This is a model change requiring a fresh solve.

## Measured twelve-world control

The [saved spot](data/sixmax-staged-three-nine-spot.json), [source pack](data/sixmax-staged-three-nine-source-pack.json) and [payoff/menu report](data/sixmax-staged-three-nine-payoff-reuse.json) declare 100bb stacks, 0.5bb small blind, no rake and scheduled `[3,9]` targets. They retain the original twelve correlated physical worlds across four uncertain seats. All 684 exact deal/subset shares are reused from the original source; every betting utility, strategy row and quality score is recomputed. The source uses 500 exhaustive CFR+ iterations and mandatory postflop checkdown.

The complete preflop tree has 11,566 public states: 5,466 decisions, 665 uncontested wins, no all-in leaves and 5,435 checkdown leaves. Including twelve private deals and the chance root gives 138,793 states. It fits the existing 20,000-public / 160,000-deal-public bounds. Adding a scheduled 27bb four-bet exceeds the public cap before any payoff call; neither cap has been increased.

The control's own-game NashConv is **0.0018644721bb** and heads-up continuation probability is **0.4786164618**. These are results for a different game from the earlier global and open-only sources. They do not demonstrate improvement of those policies, multiplayer convergence or full cash-poker quality.

The unchanged one-history menu search tries seeds 711–726. Fourteen attempts fail content and two find no diverse menu. At seed 711 it selects BB versus CO after UTG/HJ fold, CO limps, BTN/SB fold and BB checks. Both players retain two material hands on `3c 6s 9s`; all twelve private worlds remain board-compatible. Its cost is 12 compatible pairs / 1,445,605 states, but its history reaches only 0.0952275460, or **19.8964%** of heads-up mass, below the existing 25% cutoff. The two more frequent CO/BTN histories fail active-hand diversity. A bounded greedy search failing does not prove that no other menu exists.

The source is therefore not admitted as trainer content. The earlier open-only passing control remains separate. Further connected solving requires a declared candidate with useful source and retained content as well as independent quality checks.

## Controlled range-weight sensitivity

Two additional declared sources change only BTN `Js Ts` from weight 1 to 0.5 or 2. BTN `88` stays at weight 1; all other exact combos and weights remain unchanged. The filenames call these the button-weak half/double variants; the changed hand is specifically `Js Ts`, not a general weak-hand range. These are synthetic sensitivity probes, not recommended cash ranges. All three sources use the same betting schedule, exact physical payoff table, 500 CFR+ iterations and content/search settings. Every source retains all twelve private worlds.

| BTN `Js Ts` weight | BTN physical prior after blockers | Own-game NashConv (bb) | Heads-up mass | One-history search, seeds 711–726 |
| --- | ---: | ---: | ---: | --- |
| 1, control | 1/2 | 0.0018644721 | 0.4786164618 | 14 content failures, 2 no diverse menu |
| 0.5 | 1/3 | 0.0006591231 | 0.4446346632 | 10 content failures, 6 no diverse menu |
| 2 | 2/3 | 0.0003224348 | 0.8210197736 | 16 content failures |

The smaller different-game scores do not clear content or demonstrate improved convergence. None of the variants passes the unchanged screen. Increasing heads-up mass to 82.10% does not ensure material active-hand mixes on frequent histories. At seed 711, the doubled-weight selected history has only 0.0000053073 reach and fails the history floor as well as heads-up coverage. Both successful and unsuccessful search attempts remain saved; no private worlds were pruned and no thresholds were lowered.

`SixMaxPreflopRangeWeightSensitivity` validates both packs and requires matching rules, rake, continuation, solver version, iteration budget, physical combo support and every exact payoff value. It permits only declared positive range-weight changes and differing descriptive spot IDs. Its [saved comparison](data/sixmax-staged-range-weight-sensitivity.json) separates total variation in the blocker-conditioned physical chance distribution from total variation in matching private-information-set action rows. Policy weighting uses the symmetric mean of both games' own-policy decision encounter masses. That mass is an expected count, not the probability of a hand. Uniform means and maxima keep rare or unreachable rows visible, while numerical underflow is treated as zero reach.

Both probes move joint-deal probabilities by **1/6 total variation**. Their policy differences are materially different:

| Variation from control | Uniform mean action TV | Largest row action TV | Reach-weighted action TV |
| --- | ---: | ---: | ---: |
| Half `Js Ts` weight | 0.0178308606 | 0.9999760479 | 0.0364707100 |
| Double `Js Ts` weight | 0.0152980999 | 0.9999760479 | 0.2052716210 |

Each comparison visits all 138,793 deal/public states and compares 9,161 policy rows. The doubled-weight probe changes frequent decisions much more than its small uniform mean suggests. This is finite-budget sensitivity to changed game assumptions, not independent-solve stability, a certificate of equilibrium sensitivity, a convergence bound or trainer admission. Review plausible input ranges and consider a richer material-history selector before spending on another connected solve. Broader postflop modeling remains necessary; the mandatory-checkdown source can itself distort preflop incentives.

The read-only sensitivity CLI accepts one baseline, one to eight variants and an output report. It strictly reloads each pack, bounds each input to 16 MiB, rejects report/input aliases including hard links, completes all comparisons before writing, and atomically replaces the output. Failed validation preserves the inputs and any previous report. Analytic tests distinguish a 0.25 chance-TV change from a root-action flip with weighted TV `1/5.5`; self-comparison, reversed comparison, mismatched games/budgets/payoffs/support and file guards are checked separately. Saved-artifact tests reload all three sources and reproduce source hashes, chance weights, exact payoffs, seed-711 menu costs/coverage and sensitivity metrics without retraining.

## Verification and reproduction

Regression tests exercise limps, big-blind opens, re-raises, prior callers acting again, calls/folds after the final raise, full all-in closure, chip conservation, immutable states, minimum-raise rejection and the unchanged tree cap. Pack tests bind canonical historical hashes, strict parsing, version/schedule mismatches, exact payoff reuse and checkpoint rejection. A complete uniform test policy separately exercises reached trainer questions, six-seat public-table replay and finite action EVs under the same scheduled rules. This does not publish a new trainer lesson.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopPayoffReuseMain' '-Dexec.args=docs/data/sixmax-correlated-source-pack.json docs/data/sixmax-staged-three-nine-spot.json .local/staged-source.json .local/staged-report.json 500 2026-10-06T13:18:54Z --search 711 16 1 1'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopRangeWeightSensitivityMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-button-weak-half-source-pack.json docs/data/sixmax-staged-button-weak-double-source-pack.json .local/staged-sensitivity.json'
```

Use the saved pack's `generatedAt` value when reproducing its exact content hash; a changed timestamp changes that hash. The command above illustrates the workflow. New output paths preserve recorded evidence. All artifacts remain `VALIDATION_ONLY`; full ranges, wider physical boards, multiway postflop betting, postflop raises, realistic rake and trainer admission remain separate gates.
