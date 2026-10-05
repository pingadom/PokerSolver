# Correlated private ranges in a six-seat connected game

This offline validation expands the connected private-deal cap from eight to twelve. HJ, CO, BTN and BB each have two possible hands. UTG and SB retain singleton hands. The public betting menu remains 3bb / 100bb, with no rake and mandatory checkdown outside the explicitly selected heads-up flop continuations.

## Physical range product

All input combo weights are one:

| Seat | Input hands |
| --- | --- |
| UTG | 4c 4d |
| HJ | Kd 9d, Ah Jh |
| CO | Qh Th, Ah Kh |
| BTN | Js Ts, 8s 8h |
| SB | 6c 5c |
| BB | Ac Jc, 7d 7h |

There are sixteen nominal products, but HJ Ah Jh and CO Ah Kh share Ah. Removing those four impossible products leaves twelve equiprobable physical deals. HJ's physical marginal is 2/3 Kd 9d and 1/3 Ah Jh; CO's is 1/3 Ah Kh and 2/3 Qh Th. BTN and BB stay 50/50. Input range weights are not the resulting physical marginals.

The HJ/CO joint prior contains three possible hand pairs, each with probability 1/3. Multiplying its physical marginals would assign 1/9 probability to the impossible Ah Jh / Ah Kh pair. Removing the impossible pair from that independent product and renormalizing would give 1/4, 1/2 and 1/4 for the three legal pairs, instead of the original 1/3 each. Reapplying blockers to physical marginals therefore cannot reconstruct the original joint prior. Its mutual information is log2(27/16)/3, about 0.25163 bits, and its total variation distance from that independent product is 2/9. Other seat pairs in this fixture are independent in the prior. Reached decisions and public boards can introduce further dependence.

`SixMaxPrivateRangeCorrelationAudit` records all fifteen seat pairs, their joint combo probabilities (including zero entries within the surviving marginal product), supported pair counts, unsupported independent mass, mutual information in bits and total variation distance. It measures the physical prior, independent of the policy. A pair absent from empirical sampled support is not proven physically impossible. Pairwise independence does not prove full joint independence. Entirely absent marginal combos cannot be recovered from the prepared game; this audit is not a reconstruction of the original input weights.

These concealed joint probabilities belong only in offline research reports. They must not enter trainer question payloads. Existing private-support diagnostics report reached beliefs separately, and players' information sets still depend only on their own cards and public history.

## Bounded execution

The unchanged preflop cap permits 160,000 deal/public-state combinations. This twelve-root source uses 153,984 plus its initial chance root. Connected studies still allow at most sixteen compatible deal/flop pairs and two million complete states. Expanding the private cap does not expand these study budgets. Planning fails before training when a selected menu is too large; it never drops worlds or silently changes a board to fit.

A board containing Ah leaves only HJ Kd 9d / CO Qh Th, with four physical worlds and prior mass 1/3. Folded hands remain blockers. An unblocked selected flop supports all twelve worlds, including four seats with uncertainty; adding a second unblocked flop exceeds the study's pair budget and is rejected.

New alternating-study reports use `six-max-alternating-continuation-study/v4`, adding `sourcePrivateCorrelation`. Existing v1-v3 reports remain readable with that field null. The checkpoint schema and policy/menu binding are unchanged.

Generate the exact source from the repository root:

```powershell
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.GenerateSixMaxPreflopPack' '-Dexec.args=exact docs/data/sixmax-correlated-spot.json docs/data/sixmax-correlated-source-pack.json 500 2026-10-05T21:30:00Z'
```

The study commands use a 5 GiB maximum JVM heap per process; concurrent trials need enough total memory for both JVMs. This is a configured limit, not a measured peak or a promise of runtime. Generation and plan-only inspection do not execute the alternating training stages.

Each private deal retains all six physical hands. Exact payoff construction evaluates 658,008 five-card boards from the forty undealt cards for each of the 57 active-seat masks with at least two players. Shared-board reuse avoids reevaluating the entire deck separately for each mask. The source remains validation-only.

## Reproducible menu preflight

The first four declared flop seeds were rejected for exceeding the unchanged pair budget: 711 required 18 pairs, 712 and 713 required 24, and 714 required 18. No training or output checkpoint was created for them. Seed 715 was the first fitting menu in this consecutive scan; it was selected by cost, before comparing any solver-quality outcome.

The diverse selector requires at least two exact combos with at least 5% source-policy mass for both active players on each selected board. For seed 715 it selects original reach ranks 2 and 6:

| Active pair | History | Flop | Compatible worlds |
| --- | --- | --- | ---: |
| BB / BTN | BTN limps, BB checks; others fold | 3c As Qh | 4 |
| CO / BTN | CO opens to 3bb, BTN calls; others fold | 3c 5h Kc | 12 |

Qh blocks CO Qh Th on the first board, leaving CO Ah Kh and therefore forcing folded HJ Kd 9d. Both active players still have two hands. The second board blocks no source hand and retains uncertainty in folded HJ and BB. Material source mixes do not constrain the retained strategy.

The full tree has 16 compatible deal/flop pairs and 1,896,409 states, including residual-checkdown children for every original deal. Every root remains in preflop play. The source has 10,172 strategy rows and NashConv 0.0453440070545501bb after 500 exhaustive CFR+ iterations. Its content hash is `7d077588d06a4100336cc989892cf953c94f82220f73008c8962a6f974b9cbc8`; its spot hash is `75b0e61e77ed83dcc91762e6faffa9b9966f664f201adee4e597bd1a6d17ccbf`.

```powershell
$env:MAVEN_OPTS = '-Xmx5g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain' '-Dexec.args=docs/data/sixmax-correlated-source-pack.json .local/correlated-plan.json .local/unused-policy.json 711 500 300 500 300 1 2 715 1 0.05 0.000001 --plan-only --diverse-pairs 0.05'
```

## Standalone source audit

Audit any validated saved six-seat preflop source without constructing a connected menu or launching training:

```powershell
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPrivateRangeCorrelationAuditMain' '-Dexec.args=docs/data/sixmax-correlated-source-pack.json .local/correlated-private-range-audit.json'
```

The `six-max-private-range-correlation/v1` report binds the source's content hash and spot hash. It is validation-only and describes the saved prior, not convergence or a postflop strategy. The source size limit is 16 MiB. Malformed inputs, excess arguments and report paths aliasing the source are rejected before output is written; an existing report is preserved on input validation failure. No connected-deal cap is needed for this diagnostic; the source game's own validated support and public-tree caps still apply.

## Explaining the implementation in an interview

The input ranges are independent weights, but a physical deal cannot use the same card twice. PokerLab enumerates the six-seat range product, rejects collisions and normalizes the surviving weights. In this example, two players can hold Ah in their input ranges, but never simultaneously. This changes both their individual hand probabilities and their joint distribution. The audit quantifies the difference from multiplying the resulting marginals and lists the absent hand pairs, making a subtle card-removal assumption inspectable.

The solver then carries every original physical deal through preflop, including folded hands. A selected board removes only deals that physically conflict with it. Public actions change the reached posterior, while counterfactual support remains available for deviation evaluation. Information sets group only a player's own hand and public observations, preventing a folded opponent's concealed cards from leaking into decisions. Turn/river privacy and six-seat settlement are checked on the twelve-world fixture.

Exact showdown enumeration removes payoff sampling error in the source, but does not solve unrestricted poker. Only declared heads-up continuations allow postflop betting; other branches check down. Alternating stages re-solve preflop against fixed continuation values, then re-solve postflop at the changed ranges. A quality gate assesses the complete candidate before retaining it. The private-range audit explains support and dependence; the separate best-response audits measure finite-game strategy quality. Neither constitutes a convergence theorem for six-player cash poker.

## Whole-round rejection evidence

The first two trials use 500 joint iterations, 300 initial exact postflop iterations, then one 500-iteration CFR+ preflop / 300-iteration postflop round. Both initial policies pass the 0.05bb conditional target. Both complete candidates improve conditional accuracy but worsen the full six-seat parent score, so both are rejected:

| Joint seed | Initial parent NashConv (bb) | Candidate parent NashConv (bb) | Initial worst conditional gap (bb) | Candidate worst conditional gap (bb) | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| 711 | 0.0083853140 | 0.0455992414 | 0.0195831153 | 0.0118326607 | PARENT_QUALITY_REGRESSION |
| 712 | 0.0093710957 | 0.0438667535 | 0.0198352164 | 0.0104620567 | PARENT_QUALITY_REGRESSION |

The candidate's CO deviation gain dominates the regression: approximately 0.03212bb / 0.03172bb, versus approximately 0.00222bb / 0.00261bb in the inputs. These are measurements of this finite game, not a diagnosis of a solver implementation defect or an upstream library problem. Multiplayer CFR+ has no general Nash-convergence guarantee.

The retained checkpoints keep the original average policies, hashes `4da23ffe39cc4a6c8eddc9df00660d59aa4416f05df1c32ae127ee916a5ba9a5` / `3d755fc944ff7d8a795a95a1556531e9295c243b17ddc16605dbd57179b2a89a`. The rejected rounds still freeze all 24 history/private-deal utility vectors, preserve all 10,172 preflop information sets and traverse 461,955,000 preflop states exhaustively. The completed policy has 243,444 rows; uniformly completed rows are explicitly counted (9,990 / 9,270). Full checkpoints remain ignored local artifacts of approximately 63.54 MiB each.

The preflop stage now accepts `--preflop-algorithm CFR_PLUS|LINEAR_VANILLA`. CFR+ remains the default, including for historical reports lacking this optional setting. `LINEAR_VANILLA` uses exhaustive chance with unclipped regrets and linear weighting of both regret updates and strategy averages. Both algorithms start fresh regret tables, preserve the original postflop rows during preflop feedback and use the same full-game/conditional quality gate afterward. The feedback report records the actual algorithm and traversal; it does not infer a convergence guarantee from its label.

The next trials resume each preserved input using 1,000 preflop iterations with `LINEAR_VANILLA` and 300 exact postflop iterations. Both algorithm and iteration budget change relative to the rejected controls. This is a higher-budget practical follow-up, not a same-budget causal comparison. The original checkpoints are preserved as separate inputs.
