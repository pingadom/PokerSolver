# Comparing independently solved connected policies

`SixMaxConnectedPolicyStability` compares two complete average policies in **the same declared six-seat game**. It reports action-frequency disagreement alongside fresh same-game quality and retained-content checks. The comparison is offline and does not select or publish a trainer policy.

A small parent NashConv, useful reached hands and reproducible action frequencies answer different questions. Parent and conditional deviations measure policy quality in the bounded game. Retained coverage measures whether its selected lessons remain meaningfully reached and varied. Independent-policy comparison measures how much the action probabilities change between saved solves.

For each information set, total variation is half the sum of absolute action-frequency differences. Identical action distributions give zero; two disjoint pure choices give one. The report gives two separate stage summaries, `PREFLOP` and `POSTFLOP`:

| Field | Meaning |
| --- | --- |
| `informationSets` | All complete policy rows in this stage, including unreachable rows |
| `reachedByEitherPolicy` | Rows with positive numerical decision encounter mass under at least one policy |
| `uniformMeanTotalVariation` | Every row receives equal weight, including unreachable rows |
| `maximumTotalVariation` and its information-set key | Largest row difference; deterministic ties use the first sorted key |
| `firstExpectedDecisionEncounters`, `secondExpectedDecisionEncounters` | Expected decision counts per initial deal under the corresponding full policy and physical chance |
| `reachWeightedTotalVariation` | Mean row disagreement weighted by the symmetric average of the two policies' encounter masses |

The reach walk includes all players' actions, the acting player's own prior choices, root chance and physical public-card chance. Repeated states with the same own-hand information set contribute to the same observation's mass; folded-card uncertainty is not treated as independently observed hand information. The expected encounter count can exceed one because a hand contains several decisions. It is not a probability of reaching the stage.

Each stage is normalized independently. A rare exact flop does not make large **conditional postflop** disagreement look small merely because most hands never reach that board. When neither policy reaches any decision in a stage, weighted disagreement is null and expected encounters are zero. Uniform row differences remain visible. Numerical double underflow is treated as zero in this diagnostic; the separate retained-coverage audit retains its explicit log-reach classification.

Symmetric weighting avoids favoring one policy's visited rows. The comparison does not silently ignore a branch reached only by the other policy. It validates every legal probability row through the existing complete-tree audit, rejects missing or foreign information sets and obeys the declared tree budget before walking. Both solution hashes bind the measured policies.

Low frequency disagreement is not an equilibrium certificate. High disagreement can occur between close-value alternative strategies; it is evidence to investigate, not an automatic quality rejection. This diagnostic has no trainer-admission threshold and does not replace exact best-response or retained-content checks.

## Run it

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxConnectedPolicyStabilityMain' '-Dexec.args=docs/data/sixmax-three-open-source-pack.json .local/sixmax-three-open-policy-711.json .local/sixmax-three-open-policy-712.json .local/sixmax-three-open-policy-stability.json'
```

The source is limited to 16 MiB. Each checkpoint uses the existing strict 128 MiB reader, which verifies source pack/spot hashes, complete legal rows and the canonical policy hash. Both checkpoints must have exactly the same selected histories, boards, bet sizes and budget. A different game or menu must be solved and evaluated separately. Passing the same checkpoint twice is permitted as a zero-disagreement control.

The `six-max-connected-policy-stability/v1` artifact saves source identity, budget, menu, both policy hashes, stage disagreement, fresh parent/conditional quality audits and both retained-content reports. The output cannot alias any input, including normalized paths and existing hard links. All audits and serialization complete before atomic report replacement. Validation failures preserve an existing report and every input.

Tests use known pure policies to verify zero self-disagreement, symmetry and an analytically computed reached preflop difference. A deliberately changed unreachable postflop row keeps null weighted disagreement while appearing in the uniform mean and maximum. A branch reached only by the second policy has fewer than 0.001 expected postflop encounters per hand but a conditional disagreement of exactly **1/6**, demonstrating stage normalization. Other tests reject incomplete/foreign/illegal rows, exceeded budgets, different checkpoint menus, foreign source hashes, oversized files and report/input aliases, and check deterministic output and unchanged checkpoint bytes.

## Completed two-seed comparison

The [saved report](data/sixmax-three-open-policy-stability.json) reloads the twelve-world open-only checkpoints from seeds 711 and 712. Both use the same menu, training budgets and complete 127,318-row game. Fresh audits reproduce their parent scores below 0.00008bb, conditional gaps below 0.01217bb, and passing retained-content decisions.

| Stage | Complete rows | Symmetric reach-weighted total variation | Uniform row mean | Maximum row difference |
| --- | ---: | ---: | ---: | ---: |
| Preflop | 1,118 | 9.4444e-10 | 8.1220e-11 | 1.5116e-8 |
| Postflop | 126,200 | 0.0000152832 | 0.0000837479 | 0.0580231821 |

All rows have positive numerical encounter mass in at least one of these average policies. A small positive value is not evidence of material reach: the content screen separately enforces that condition for selected histories and active hands. Expected preflop decisions are approximately 6.000606 per deal. Expected selected postflop decisions are approximately 0.000257851 per deal; the postflop disagreement above is normalized within that stage, rather than diluted by this small absolute frequency.

The largest postflop frequency difference is about 5.8% total variation on a particular BB `AJ` river response. This remains visible even though the two complete policies have very small mean disagreement. The report records the exact own-hand/public-history information-set key for investigation. Those private diagnostics remain offline.

The paired result is evidence of close action frequencies at these fixed budgets and this one source/menu, alongside independently passing quality and content checks. It does not select a winner, establish general multiplayer convergence or admit a trainer pack. The [revised betting-model study](sixmax-preflop-payoff-reuse.md) supplies independent training commands. More seeds, budgets, physical boards and betting support remain separate validation work.
