# Conditional turn and river betting from a six-seat policy

`SixMaxPolicyTurnTransition` connects the [conditional flop model](sixmax-conditional-flop-betting.md) to `SixMaxHeadsUpTurnRiverGame`. The backend can now solve two connected betting rounds after observing a completed flop round and a physical turn card. It retains the joint six-hand distribution, including all folded cards, instead of multiplying independent live-player ranges.

This is a conditional research milestone. Each street allows check/bet and call/fold with one configured bet size, no raises and no rake. Both live players have equal remaining stacks. The saved six-seat preflop policy and the preceding flop policy are frozen. The flop policy was solved with mandatory later checkdown; replacing that checkdown with these new values has **not** yet re-solved the flop or preflop game. The export is `VALIDATION_ONLY`, not a trainer-admitted pack or a general six-max cash equilibrium.

## How a hand reaches the new game

1. Replay the saved six-seat preflop policy into its correlated heads-up flop posterior.
2. Solve the existing one-bet flop game on the observed flop.
3. Observe a complete, non-folded flop history: check/check, bet/call, or check/bet/call. Multiply each joint deal's probability by both players' action likelihoods. Normalize in log space, preserving rare histories even when their direct reach probability underflows.
4. Carry called flop bets into the pot, live commitments and remaining stacks. A flop all-in has no later betting continuation and is rejected by this bridge.
5. Observe a distinct turn card. Remove deals where any of the six hands blocks it and renormalize. Given one compatible six-hand deal there are 37 possible turns, so the public card probability is compatible posterior mass divided by 37.
6. Solve the turn/river game with exhaustive CFR+, then independently evaluate information-set best responses and actual six-seat utilities.

The audit chooses the highest-probability eligible non-folded flop history for each selected preflop/flop example, breaking ties by public history. It then draws a seeded turn by drawing a joint deal followed by a uniform legal card, and conditions on the public card across all compatible deals. This produces reproducible conditional examples; it does not average continuation values across every flop history and turn.

## River chance and private information

At the turn root, chance draws the correlated six-hand deal. After two turn checks or a called turn bet, a public chance node enumerates all **36 remaining rivers** for that deal with probability `1/36`. All twelve hole cards and all four shown board cards stay removed. A different hidden deal can have a different legal river set. Observing a river therefore also updates the private posterior; using a common two-player deck would incorrectly put folded cards back in play.

Showdown values are precomputed once for each compatible deal and legal river using the seven-card evaluator. No hand evaluation or board sampling occurs inside CFR iterations. The game has exact river chance for its declared private support. If an upstream source uses empirical joint deals, that label remains; full river enumeration does not make empirical private support exact.

Information sets contain only the acting player's own combo and public information: positions, preflop/flop history, known board, pot, stack, configured sizes, turn history, river and river history. They exclude the joint-deal index and all other private cards. The complete turn history remains in river keys, preserving recall and separating a checked turn from a called bet even when the river and own hand match.

## Chips, all-ins and EV feedback

For a starting turn pot `P`, live commitment `C_i`, matched turn bet `t` and matched river bet `r`, showdown utility is:

`(P + 2t + 2r) × share_i − C_i − t − r`.

A bet that wins by a fold is returned to the bettor; only bets matched on earlier streets remain in the settled pot and commitments. Folded-seat losses remain unchanged. The six actual utilities sum to zero. As in the flop model, the two live players' constant utility sum is centered by subtracting half the folded seats' preflop contributions. CFR and best-response bounds use centered values; decision EVs and profile reports restore actual chip values from the start of the hand.

Requested turn bets are capped at the remaining stack. River bets are capped separately for each turn branch: a called turn bet reduces the available river stack, while a checked turn does not. After a turn all-in call, river chance still runs, but the river is immediately terminal with no check/bet decision. River all-in calls settle normally. The audit's configured river request is fixed from the pot after a called turn bet; it is not a dynamic half-pot size on every branch. Both requested size and actual capped size are exposed by the game.

`SixMaxHeadsUpTurnRiverDecisionEvaluator` supplies legal-action frequencies, exact conditional action EVs and EV loss on either street. Opponent moves and shown cards condition the posterior. The requested player's prior moves are forced, allowing inspection after their own zero-frequency check or bet. Future public chance and policy actions are integrated into turn EVs. Terminal/chance requests, unknown hands, blocked cards, illegal histories, zero opponent reach, and missing or invalid strategies are rejected. There is no uniform fallback for a missing policy.

## Reproducible results

The [committed audit](data/sixmax-conditional-turn-river-betting.json) binds the source pack hash `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf` and spot hash. It stores the exact source flop policies, observed histories and card probabilities, turn/river solutions, budgets, quality reports, checkdown comparisons and first-player EV feedback. The CLI limits the source to 16 MiB, each solver budget to 100,000 iterations, and the export to five examples. It sorts output maps and refuses to overwrite its source.

With seed 711, 5,000 flop iterations, 5,000 turn/river iterations and a half-pot turn request:

| First / second | Flop + turn | Flop history | Turn pot / bet / river request (bb) | Deals | Information sets | Conditional gap (bb) | First-player change from turn checkdown (bb) |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| BB / UTG | 3c 7s 9d / 5s | check/check | 2.5 / 1.25 / 2.5 | 1 | 436 | 0.000001158 | +0.138888 |
| BB / UTG | 5s 8c Qc / 5h | bet/call | 13 / 6.5 / 13 | 1 | 436 | 0.000018854 | −0.722218 |
| SB / UTG | 2s 3c 5s / Kd | bet/call | 14 / 7 / 14 | 1 | 436 | 0.000020304 | −0.777773 |
| UTG / BTN | 2s 5d Tc / 2d | bet/call | 15 / 7.5 / 15 | 2 | 666 | 0.000021754 | −0.833329 |
| UTG / CO | 7d 9d Jc / 3d | check/check | 7.5 / 3.75 / 7.5 | 2 | 460 | 0.000003475 | +0.416664 |

The five games contain 2,434 information sets in total. Their worst conditional gap is about **0.000021754 bb**, and first-player values change by up to about **0.833329 bb** from the same observed turn's checkdown baseline. These are tiny synthetic private distributions; some have only one possible opponent holding. The exact gap certifies the finite conditional game being tested. It does not certify other boards, realistic ranges, the frozen upstream policies under changed continuation, or a connected six-player equilibrium.

Run from the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTurnRiverContinuationAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-conditional-turn-river-betting.json 711 5000 5000 0.5'
mvn -q -pl solver -am spotless:check verify
```

Tests cover physical turn conditioning and 36-card river chance, folded blockers, turn-averaged equivalence with the independent 666-board flop baseline, perfect recall, all terminal paths against an unbeatable royal flush, six-seat conservation, uncalled bet refunds, branch-specific stack caps, all-in runouts, exhaustive CFR and independent best responses, both players' conditional EVs, forced own actions, rare reach underflow, strict input/policy rejection, source replay and reproducible CLI output.

The [connected postflop tree and chance study](sixmax-connected-postflop.md) now re-solve flop, turn and river decisions together, with exact physical chance and a separately labelled restricted-turn comparison. The next integration is feeding those values into a re-solved six-seat preflop game with broader flop coverage and measured computation cost. Wider private support, position-specific ranges, raises, multiway continuation and a declared cash rake model remain separate gates before publishing realistic GTO lessons.
