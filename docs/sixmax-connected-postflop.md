# One connected postflop solve from a six-seat policy

`SixMaxHeadsUpPostflopGame` now solves flop, turn and river betting in a **single CFR tree**. It starts from the [policy-derived six-hand flop posterior](sixmax-policy-flop-handoff.md). Earlier flop decisions are updated using the actual later-street policies and payoffs; the [previous conditional turn/river audit](sixmax-conditional-turn-river-betting.md) froze its upstream flop policy and could not do that.

The model has two active players after a completed six-seat preflop history, equal remaining stacks, one configured bet per street, check/bet and call/fold actions, and no raises or rake. The six-seat preflop policy remains frozen and was trained with mandatory checkdown. The new audit is `VALIDATION_ONLY`; it does not change the playable preflop pack, publish a realistic six-max chart, or establish an equilibrium across the entire six-player game.

## Physical cards and recall

Initial chance draws one correlated six-hand deal from the known-flop posterior. All twelve private cards remain out of the deck, including folded players' cards. Each physical deal has **37 possible turns** and **36 subsequent rivers**. Exact chance enumerates all `37 × 36 = 1,332` ordered turn/river trajectories. Showdown scores use the same 666 unordered pairs, evaluated once and cached symmetrically because the final five-card board is independent of turn/river order. Betting decisions retain that order.

The public tree contains separate flop, turn and river histories. A decision key includes the acting player's own combo, positions, public preflop history, known flop, starting pot/stack, configured bet sizes, chance model, observed turn/river and all earlier street actions. It excludes the joint-deal index and all other private hands. Two indistinguishable hidden deals share a decision; a checked flop and a called flop bet remain distinct on subsequent streets. No independent product of live-player marginals is introduced.

Both players' strategies are updated throughout the tree by exhaustive alternating CFR+. Exact information-set best responses independently measure the gap against the complete declared game. The source preflop policy is the only frozen strategy; the older flop-only solve is used for comparison, not as the continuation policy.

## Pots, stacks and decisions

Every matched street bet increases the pot by twice its size and each live commitment by that size. A bet that wins by a fold is returned to its bettor, preserving only earlier matched contributions. Folded-seat preflop losses remain in the settled utility vector, and all six chip utilities sum to zero. The live players' utilities are centered by half the folded seats' commitments for the zero-sum CFR interface; exported values and action EVs restore actual utilities from the start of the hand.

Each street's configured bet is capped at the effective remaining stack after earlier called bets. A flop all-in still reveals both turn and river, but neither street has a betting decision. A turn all-in still reveals the river, which is immediately terminal. A river all-in settles after its response. The fixed bet menu remains the same on checked and called earlier branches except for stack caps; the audit's later requests are calculated from pots after earlier calls and are **not** dynamic half-pot bets on every branch.

`SixMaxPostflopDecisionEvaluator` replays public actions and board observations and gives frequencies, action EVs and EV loss on all three streets. It conditions on the hero's hand and opponent actions, forces the hero's own prior moves, and integrates future policy and chance. Log-space weights preserve repeated rare opponent bets even when their direct product underflows. It uses actual declared card probabilities, including combined duplicate turn-quantile draws. Illegal, terminal, chance-only, blocked, unsupported, zero-opponent-reach or missing-policy requests fail explicitly.

## Restricted turns are a separate game

The optional `QUANTILE_TURN_EXACT_RIVER` mode chooses up to eight declared quantiles of each hidden deal's legal 37-card turn deck. Repeated card draws combine their probability. It keeps all 36 legal rivers after each selected turn, and removes folded cards throughout. The private posterior is unchanged at the root, but public turn chance is different from the physical game. The chance model and quantile menu enter policy keys, preventing a restricted policy from being silently reused as an exact one.

`SixMaxConnectedPostflopAudit` reports convergence, complete-tree traversal counts, information-set counts and fixed-checkdown bias separately. Checkdown bias compares each game's forced-check policy with the independent physical 666-pair flop baseline. It diagnoses that policy only; it is not a bound on the strategic error of the restricted game. A smaller restricted-game best-response gap cannot certify full-deck quality. Policies lack coverage for unselected turns and are not evaluated there with a uniform fallback.

## Reproducible study

The [committed report](data/sixmax-connected-postflop.json) binds the source pack hash `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`, source spot and mandatory-checkdown rule. It contains game settings, budgets, all six profile utilities, checkdown baselines, root EVs/frequencies, exact best-response bounds, tree sizes and SHA-256 hashes of complete sorted solutions. A full policy can contain over 50,000 rows, so this artifact stores their streaming hashes rather than the policies themselves. It is a reproducible study summary, not a playable pack.

Seed 711, half-pot initial bet, 300 exact iterations, 1,000 restricted iterations, turn quantiles `0.125,0.375,0.625,0.875`:

| First / second | Flop | Deals | Flop / turn / river requests (bb) | Exact information sets | Restricted information sets | Exact gap (bb) | Restricted gap (bb) | Restricted checkdown bias (bb) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| BB / UTG | 3c 7s 9d | 2 | 1.25 / 2.5 / 5 | 53,536 | 5,524 | 0.000805312 | 0.000068123 | −0.452797 |
| BB / UTG | 5s 8c Qc | 1 | 3.25 / 6.5 / 13 | 48,400 | 5,236 | 0.004191696 | 0.000375800 | −0.397710 |

The exact games contain 101,936 information sets in total. The two-deal game has 96,800 decision-node visits and 120,328 terminal-node visits per complete traversal; its four-quantile counterpart has 10,472 decision visits. Restricting turns reduces that traversal by about 9.24 times. The entire study took about 186 seconds in one local run; timing is printed to the console and omitted from the deterministic JSON artifact.

The full-deck maximum gap is about **0.004192 bb**, versus **0.000376 bb** in the restricted games, which received a larger iteration budget. The latter still have up to **0.452797 bb** forced-checkdown bias. This is a concrete example of low within-model regret coexisting with board-model error. The exact profile values differ from the converged flop-only profiles by less than 0.004bb here; those small differences are comparable to the remaining solve gap and do not establish a strategic improvement. These tiny private supports, sometimes one known opponent holding, are implementation fixtures rather than representative poker ranges.

Run from the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxConnectedPostflopAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-connected-postflop.json 711 300 1000 0.5 0.125,0.375,0.625,0.875'
mvn -q -pl solver -am spotless:check verify
```

The CLI caps source files at 16 MiB and exports two examples. The audit accepts at most three examples, four posterior deals per flop, 1,000 exact iterations and 10,000 restricted iterations. It rejects empty restricted menus, invalid budgets and quantiles, and source overwrite. Output maps and solution hashes use sorted JSON; elapsed time is excluded from the artifact.

Tests cover full folded-card-aware chance, agreement with the independent 666-pair checkdown baseline, all terminal histories across three streets, uncalled refunds, equal splits, six-seat conservation, stack caps and every all-in street, private information grouping, recall, chance-key separation, duplicate quantiles, deterministic CFR+ and exact best responses, conditional EVs for both players, forced own moves, repeated rare opponent actions, strict input rejection, agreement with the preceding turn/river implementation, solution-hash determinism and byte-reproducible source-bound exports.

The next solver integration is feeding these connected postflop values into a re-solved six-seat preflop model. That requires broad flop coverage and measured cost/accuracy choices; two selected flops cannot replace preflop terminal values. More private hands with defensible position-specific provenance, wider raise/bet menus, multiway postflop continuation and cash rake remain gates for realistic trainer content.
