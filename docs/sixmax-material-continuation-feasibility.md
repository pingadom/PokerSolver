# Fixed-policy material continuation feasibility

The re-raise sources failed sampled continuation searches. `SixMaxMaterialContinuationFeasibility` now separates an unlucky sampled flop from a source policy that cannot meet the content requirements on any physical flop. This is an offline preflight for a frozen policy, not a solver, convergence certificate, continuation menu or trainer admission decision.

The default requirements are unchanged: each selected history must have at least 0.0001 reach; selected histories must cover 25% of heads-up continuation probability; both active seats need at least two exact combos with conditional mass at least 5%. The audit accepts exact private range support with at most twelve root deals, a complete legal preflop policy and no applied rake. Empirical chance support, missing/foreign/illegal policy rows and unsupported widths fail validation.

## Calculation

1. Walk the complete fixed-policy preflop tree, accumulating each public heads-up non-all-in terminal's probability across the original six-hand private distribution. This requires no flop equity enumeration. Validate the complete policy against every legal information set first, including unreachable rows.
2. Rank histories by reach, using the public-history string to break ties deterministically. Examine at most twenty histories.
3. For each examined history enumerate all **22,100 = 52 choose 3** unordered flops. A board is physical only in worlds where it conflicts with none of the twelve dealt cards. Folded players' cards remain blockers. Condition the full reached joint distribution on the board, then measure each active seat's exact-combo marginal.
4. Count optimistically material boards and record the minimum number of compatible **root counterfactual worlds**, plus a deterministic example board at that minimum. A board may naturally exclude worlds; the audit never deletes worlds from the source or substitutes independent active-player marginals.
5. Sum the largest eligible history masses up to the configured maximum of one to four histories. Credit **all unexamined history mass**, even if it would need more slots, miss the history floor or fail material hand mix. Ignore board solve cost, distinct-pair requirements and physical board probability. These deliberate relaxations can only make the upper bound more generous.

If `H` is total heads-up history mass, `T` the unexamined mass, and `E` the examined histories with at least one material board and sufficient history reach, the optimistic fraction is:

```text
min(H, T + sum(largest maximumHistories probabilities in E)) / H
```

The bound concerns selected **public history** coverage, not the probability of physically dealing the example boards. Adding more flops within a history cannot add that history's mass again. Board count and minimum compatible worlds are evidence, not a postflop state-cost estimate or budget approval.

`NUMERIC_MARGIN = 1e-12` relaxes combo/history cutoffs and the final fraction comparison in the optimistic direction. Any zero or subnormal root probability, positive-path multiplication entering the subnormal range, or subnormal posterior world probability prevents an infeasibility verdict. Reports distinguish `NUMERIC_REACH_UNRESOLVED`, `NO_HEADS_UP_REACH`, `INFEASIBLE_UNDER_FIXED_POLICY` and `NOT_RULED_OUT`. This is a guarded double-precision diagnostic, not an interval-arithmetic proof. A boundary result remains not ruled out.

## Saved evidence

All seven reports bind the full canonical source-pack hash and spot hash. They use twenty candidate histories, a maximum of four selected histories and the unchanged default content settings. Each source retains twelve private worlds. These are synthetic, mandatory-checkdown preflop policies trained for 500 CFR+ iterations; changing that continuation model or retraining changes the question being audited.

| Source | Optimistic heads-up coverage | Result |
| --- | ---: | --- |
| [Global 3bb open only](data/sixmax-three-open-material-feasibility.json) | 66.65808039% | Not ruled out |
| [Global 3bb/9bb targets](data/sixmax-three-nine-material-feasibility.json) | 0.00095616% | Infeasible under fixed policy |
| [Staged 3bb open, 9bb re-raise](data/sixmax-staged-three-nine-material-feasibility.json) | 20.03511340% | Infeasible under fixed policy |
| [Staged, half BTN `Js Ts` weight](data/sixmax-staged-button-weak-half-material-feasibility.json) | 0.03314839% | Infeasible under fixed policy |
| [Staged, double BTN `Js Ts` weight](data/sixmax-staged-button-weak-double-material-feasibility.json) | 0.00085196% | Infeasible under fixed policy |
| [Staged, half BB `7d 7h` weight](data/sixmax-staged-bb-pair-half-material-feasibility.json) | 0.00085774% | Infeasible under fixed policy |
| [Staged, double BB `7d 7h` weight](data/sixmax-staged-bb-pair-double-material-feasibility.json) | 0.03635232% | Infeasible under fixed policy |

The staged control's only eligible examined histories are ranks 3 and 7, both BB/CO. Each has 8,296 optimistic material flops. Their combined mass is 0.0958855304; all unexamined histories add only 0.0000058205. The minimum-compatible example `2c 8h Jh` keeps four root worlds: its cards block the folded BTN `88` and HJ `Ah Jh` alternatives. It does **not** retain uncertainty at every folded seat on that board. Even granting these boards zero solve cost and allowing repeated active pairs, the bound misses the 25% target.

The open-only result agrees with its previously passing one-history search and two independent connected trials. It does not imply that all of its potentially material histories fit the connected budget or that it is ready for general cash training. The failed results only rule out content menus under the **unchanged source policies**. Connected feedback can alter preflop incentives and reach; a new policy needs a fresh bound and final-policy coverage/quality checks.

## Fresh BB prior controls

After establishing the bound, two new 500-iteration solves changed only BB `7d 7h` from weight 1 to 0.5 or 2. BB `Ac Jc` and every other combo stayed at weight 1. These declared bidirectional probes reuse all 684 exact physical showdown-share entries from the staged control, recompute priors and every betting utility, and train fresh policies. No source strategy or connected checkpoint is transferred. The input spots, fresh packs, payoff-reuse provenance and all-board reports are saved under `sixmax-staged-bb-pair-{half,double}-*`; generated time is `2026-10-06T19:50:00Z`.

| BB pair weight | BB pair prior | Own-game NashConv (bb) | Heads-up mass | Reach-weighted policy TV from control |
| --- | ---: | ---: | ---: | ---: |
| 0.5 | 1/3 | 0.0017065463 | 0.6665092597 | 0.1408032995 |
| 2 | 2/3 | 0.0004780871 | 0.3344897997 | 0.0738012851 |

Both priors move joint private probabilities by 1/6 total variation. The [matched-budget comparison](data/sixmax-staged-bb-pair-sensitivity.json) checks identical rules, continuation, physical support and every payoff value; it compares all 9,161 information sets and visits 138,793 deal/public states. These scores describe different prior-weight games and do not show improved convergence or equilibrium sensitivity.

The half-weight source has three examined histories with material flops (including a CO/BTN history), but all lie below the absolute history floor. Its upper bound therefore consists entirely of the generously credited tail. The double-weight source has one eligible BB/CO history at 0.0001154515 reach, still far short of useful coverage. Neither proceeds to another sampled-seed search or connected solve. Raising heads-up mass, changing a single prior, or finding a second player pair is insufficient on its own. These are synthetic diagnostics, not recommended cash ranges; the next source study needs a declared range/continuation rationale rather than more blind weight tuning.

## Use and validation

Run this preflight before spending on a larger frozen-source seed search or connected solve. A failure supports reviewing the declared ranges and preflop continuation assumptions; simply increasing the number of board seeds cannot fix it. A not-ruled-out result still needs actual menu construction, full counterfactual state costing, connected solving and separate final quality/content gates. Historical search schemas, source packs, thresholds and checkpoints are unchanged.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxMaterialContinuationFeasibilityMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json .local/staged-material-bound.json'
# Optional limits: examine 10 histories and credit at most 2 examined histories.
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxMaterialContinuationFeasibilityMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json .local/staged-material-bound-10x2.json 10 2'
```

The CLI strictly reloads one source of at most 16 MiB, rejects normalized-path and hard-link output aliases, finishes validation before writing, and atomically replaces its report. It never modifies or trains the source. The schema is `six-max-material-continuation-feasibility/v1`, with publication status `VALIDATION_ONLY`.

Analytic tests count exactly 7,140 material flops in a four-world fixture, check inclusive thresholds, demonstrate dominant-hand removal through board conditioning, prove the history-slot/floor accounting, credit the entire tail, and distinguish zero reach from underflow. Invalid policies, empirical chance, applied rake, excessive private support, immutable reports, repeatability and file guards are tested. Saved-artifact tests recompute all seven all-flop bounds and source hashes without retraining. Additional BB-control tests bind the declared input spots, changed weights, exact payoffs, fresh policy budgets and both sensitivity reports. The next solver work should improve useful source hand coverage and continuation assumptions before broader re-raise connected studies; full ranges, multiway postflop betting, realistic rake and production admission remain separate research gates.
