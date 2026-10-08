# Public-history-specific physical flop observations

PokerLab now has a separate backend model that reveals explicitly listed physical flops at explicitly listed public preflop histories. Every other board uses the original rank/texture signal, with the exposed boards removed from that signal's payoff aggregate. This expands actual-card coverage without increasing the existing one-million-state or 2,000-observation limits. All artifacts remain `VALIDATION_ONLY`; study reports set `trainerAdmission = false`.

## Model and information boundaries

The source remains the staged 100bb six-seat, no-rake 3bb-open/9bb-re-raise game, with twelve legal joint private worlds. The model expands six selected non-all-in heads-up histories into one flop betting round. Both active players can check/bet or fold/call against one declared half-pot bet; turn and river are integrated into exact showdown shares. Unselected histories and multiway pots retain checkdown. This is a restricted research game, not a complete cash-poker strategy.

The fixed menu contains pairs of public-history index and sorted three-card flop. Classification receives only that menu, the public history and public cards. It cannot receive an opponent hand or choose a different observation according to the hidden deal. Consequently, a board can be literal at one history and aggregate at another, but every private world at the same public history uses the same classification rule. Information sets contain the new model namespace, the public history, the actor's own cards, observation and public flop actions.

The table, checkpoint and report have separate schemas:

- `six-max-history-physical-payoffs/v1`
- `six-max-history-physical-checkpoint/v1`
- `six-max-history-physical-study/v1`

The model is `PUBLIC_HISTORY_FLOP_WHITELIST_OTHERWISE_RANK_TEXTURE_ONE_BET/v1`. Checkpoint bindings include source pack, source spot, parent rank table, exact payoff table, menu-derived game identity and complete tree cardinality. Previous rank-only or whole-group suit policies cannot be loaded as fresh policies in this model. Their data and identities remain intact.

## Exact payoffs and counterfactual support

All six players' twelve private cards are removed before board enumeration, including folded players' cards. Each world has 9,880 possible flops; a supported literal flop has 666 turn/river combinations from the remaining 37 cards. The payoff kernel evaluates only the two active hands for a history, while still removing all folded blockers. Repeated identical world/pair/board work can be reused across histories.

For each history, private world and active pair:

1. Start with the original exact rank/texture board counts, first-player wins and ties.
2. Give every legal whitelisted physical board its own count of one and exact 666-runout payoff.
3. Subtract that board's count, wins and ties from its original aggregate signal.
4. Verify that regrouping physical and aggregate observations recovers every parent field exactly, and that wins/ties fit the remaining runout count.

Illegal boards have zero count in the blocked world. Off-policy worlds remain supported wherever the physical cards permit them. An aggregate observation disappears in a particular history/world only when every physical board it represented has been exposed. Probability sums and checkdown utilities remain unchanged. Structural regrouping alone cannot detect a compensated payoff edit; exact replay regenerates all literal payoffs and requires complete artifact equality. The runtime accepts only the opaque table returned by exact generation/replay.

Before evaluating hands, sizing counts all counterfactual world/observation supports. Limits are six histories, twelve worlds, 600 history/board revelations, 2,000 shared observations, one million complete states, 8MiB uncompressed table, and 128KiB menu. Policy and report limits are 64MiB and 32MiB, with at most 1,000 fresh iterations. JSON/gzip bounds apply to decompressed bytes. Unsupported requests fail rather than relaxing the limits.

## Menu selection

The selection tool freezes the prior whole-group suit model's 500-iteration preflop policy. It independently enumerates every world/flop and controls all original rank counts before selecting boards. Each candidate needs history reach at least 0.0001 and at least two combos with probability at least 5% for each active player, conditioned on that history and physical board.

Candidates are ordered by frozen joint history/board reach divided by their raw counterfactual state cost, with deterministic history/card tie ordering. This is a density heuristic; it proves neither optimality nor strategy quality. Its bound evidence records source/table/policy/menu hashes, requested and achieved material reach, the all-heads-up denominator and exact final sizing. Replay reconstructs every candidate and the selected menu from the frozen policy. Changing the final trained policy does not silently reselect the observation model.

The saved 2.5% request selects 530 physical boards, all in menu history 5: UTG/HJ fold, CO opens to 3bb, BTN/SB fold and BB calls. BB acts first against CO on the flop. The index is local to this ordered six-history menu. The heuristic's concentration is an explicit limitation: this milestone expands physical boards in one selected history, rather than establishing coverage across all seat pairs.

The frozen projection is 2.503790% of all heads-up reach. It produces 1,712 shared observations and 915,457 complete states, compared with 888,205 states in the underlying rank-only model. Eighteen aggregate history/world supports are exhausted. The old whole-group model's roughly 0.88% physical heads-up coverage uses different observation rules and policy; this projection is not a direct strategy-performance comparison.

## Fresh training and saved evidence

Both saved policies begin with zero regret rows and run exhaustive alternating six-player CFR+, with literal fixed-utility pruning for inactive players. A prior policy is used only to choose the fixed observation menu. Its strategy rows or completed iterations are not transferred into either fresh solve.

| Measurement | 100 fresh iterations | 500 fresh iterations |
| --- | ---: | ---: |
| Complete states | 915,457 | 915,457 |
| Preflop information sets | 9,161 | 9,161 |
| Postflop information sets | 60,826 | 60,826 |
| Own-model parent NashConv | 0.02966613bb | 0.00136993bb |
| Material physical fraction of all heads-up reach | 2.415094% | 2.495162% |
| Reached / material physical cases | 530 / 530 | 530 / 530 |
| Largest conditional gap, all observations | 2.69388609bb | 2.69278728bb |
| Training node visits | 206,236,200 | 1,031,181,000 |
| Training terminal evaluations | 110,309,200 | 551,546,000 |
| Literal inactive suffix roots pruned | 2,880,000 | 14,400,000 |
| Sampled chance nodes / baseline corrections | 0 / 0 | 0 / 0 |

Each study rechecks six-player checkdown recovery, complete strategy support, every selected history/observation posterior, conditional information-set best responses, independent full-parent unilateral responses and embedded local-response gains. Zero-support observations receive explicit labels. It also recomputes all-heads-up physical feasibility under the fresh preflop policy. That feasibility result is not a passed quality/content gate.

The 500-iteration report audits 7,619 reached history/observation cases and labels 2,653 palette entries without reached private support, including physical keys at histories that do not reveal them. All six histories are reached. Of the 530 physical cases, 510 have conditional NashConv at most 0.001bb; the largest physical gap is 0.00209649bb. Aggregate fallback observations account for the much larger 2.6928bb worst case. The reach-weighted local gain is 0.00022861bb, but 2,633 reached cases across the whole palette still exceed 0.01bb. Neither aggregate weighting nor the physical-case accuracy tally replaces decision-budget/posterior stability.

Training counters are saved separately and bound to the checkpoint. Replay runs one fresh exhaustive iteration and checks the scaled structural counts. This verifies traversal accounting; it does not reproduce all 100 or 500 regret updates. A complete deterministic policy regeneration requires another full fresh solve. Diagnostic traversal costs are excluded from the saved training counters.

`materialAllHeadsUpFraction` means that the board/history is reached and both players retain material hand diversity. It does **not** mean the decisions meet local EV, convergence or budget-stability thresholds. None of these cases is admitted trainer content. The prior unchanged 25% retained-coverage target, per-decision EV and posterior checks still apply before publication. Small own-model parent NashConv is not a certificate for general multiway poker or for the hidden-suit remainder of this abstraction.

Saved files use the prefix `docs/data/sixmax-staged-history-physical`:

- `-menu.json` and `-menu-selection.json`: fixed public observations and reproducible frozen-policy selection evidence.
- `-payoffs.json.gz`: exact physical payoffs plus per-history rank complements.
- `-policy-100.json.gz` / `-policy-500.json.gz`: fresh bound policies.
- `-study-100.json.gz` / `-study-500.json.gz`: complete parent/conditional diagnostics and fresh-policy coverage.
- `-traversal-100.json` / `-traversal-500.json`: fresh work counters.

Payoff table SHA-256: `8a32a8159abbe92f8df53d49f702fe22caf0c6d058105a0c636dd02e197e9e13`.

Game SHA-256: `0b7e6a7093c2780997f21b961ac0f73677b8828a17a3cbdc009616c0c105bdb6`.

500-iteration solution SHA-256: `3227533eb3011640af7155dda87cfcd80911fea39ecb535878548bf4ce871298`.

## Run and replay

From the repository root, using Java 21 and Maven:

```powershell
mvn -q -pl solver -am install -DskipTests
$env:MAVEN_OPTS = '-Xmx2g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalMenuSelectionMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-history-physical-menu.json docs/data/sixmax-staged-history-physical-menu-selection.json'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalStudyMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-traversal-500.json'
mvn -q -pl solver -am test '-Dtest=SixMaxHistoryPhysicalPayoffTableTest,SixMaxHistoryPhysicalArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

For new generation, the menu command uses `select` with the same four input paths, two **new** menu/evidence output paths, then a coverage fraction. The payoff command uses `generate <source> <rank-table> <menu> <new-table>`. The study command uses `solve <source> <rank-table> <table> <new-checkpoint> <new-report> <new-traversal> <iterations> <NONE|FIXED_UTILITY>`. Every CLI rejects normalized and hard-linked aliases among its paths and requires new generation outputs. Study replay first replays every literal payoff, then the bound complete policy's diagnostics and traversal counters.

Tests separately check public-only classification, history-dependent fallbacks, folded blockers, off-policy support, chip accounting, kernel agreement for all fifteen active pair masks, strict input limits, namespace rejection and tampering. Saved evidence tests independently classify the full physical deck in every history/world, replay selection and all physical payoffs, compare physical samples against the object hand evaluator, reproduce both complete study reports and check every reached own-hand physical decision against closed-form pot/share EV calculations. The compensated-payoff test demonstrates why count/marginal checks alone are insufficient.

## Interview explanation and next step

“I reduced solver memory by defining the public observation model per betting history. I preserve every legal hidden world, split exact board outcomes out of an aggregate, and subtract their payoffs so the original marginal totals recover exactly. I bind model and policy identities, retrain from zero regrets, then evaluate both full-game deviations and rare local decisions. I report reached board coverage separately from decision accuracy and trainer admission.”

The [conditional refinement and decision-stability follow-up](sixmax-history-physical-refinement.md) now implements frozen-preflop local optimization, complete parent re-audits, exact fresh-budget replay and retained coverage measurement in this separately bound model. The broad candidate fails on fourteen selected cases and exports no policy; a separately declared accurate-case derivative retains 28 of 32 screened cases as research evidence, covering only 0.132% of all heads-up reach. Next address the weak/unstable decisions and a broader measured screen. A broader menu must address the heuristic's concentration and demonstrate acceptable runtime and local quality. Realistic ranges, rake, multiway postflop and richer turn/river betting remain separate milestones. Public AWS deployment remains paused.
