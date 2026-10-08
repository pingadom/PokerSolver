# Owned bounded maxmin repair for physical-board studies

PokerLab now has its own finite maxmin solver, with no production LP dependency. The saved pipeline preserves the accepted CFR refinement and repairs its twenty remaining weak physical-board games. All 530 reached physical-board roots meet the 0.001bb local gate; the independently screened sample still retains 28 of 32 cases. All new artifacts remain `VALIDATION_ONLY` with `trainerAdmission = false`.

## What the solver actually solves

The local game has two decision makers at a six-seat table, a complete joint private-world posterior, a revealed physical flop, and at most one half-pot flop bet. Folded seats have fixed utilities. Turn and river are exactly integrated into showdown shares. This is a finite constant-sum reduction, not a general six-player equilibrium claim or a solution for realistic cash-game ranges.

`FiniteTwoPlayerMaxmin` snapshots the complete bounded local tree and validates legal actions, positive normalized chance probabilities and information-set consistency. Every information set must have one public depth and the same sequence of prior own information sets/actions across its indistinguishable states. Repeated own information sets, forgotten own actions/private information and a third decision maker fail before optimization.

For each actor, it enumerates every pure information-set plan in deterministic key/action order. A plan chooses actions per information set, never per hidden state. It evaluates every plan pair over the complete chance tree, checks that the two active expected utilities have a constant sum and that other seats' utilities do not depend on decisions, then builds the payoff matrix for the lower seat-index actor. Row/column order is recorded; it does not imply who acts first in poker.

`FiniteMatrixMaxmin` shifts this matrix to positive payoffs. It solves `max sum(y)` subject to `A y <= 1`, `y >= 0`, using a feasible slack basis and bounded simplex pivots. The dual row weights and primal column weights normalize into mixed strategies. It independently recomputes `min_column(pᵀA)` and `max_row(Aq)` on the **original** matrix and rejects a certificate gap above 1e-9bb, nonfinite arithmetic, invalid mixtures or exhausted work budgets.

Mixed pure plans convert to behavioral rows by conditioning their mass on **every earlier action taken by that player**. An unconditional average is wrong at later own decision nodes because it includes plans that chose another earlier action. Zero own-prefix mass receives a uniform, unreachable row. The resulting complete behavioral strategy must independently pass information-set best responses in the original game, including fixed seats, with NashConv at most 1e-9bb. This second check validates the conversion, not just the matrix LP.

The hard bounds are 64 plans per actor, 1,000 expanded tree nodes, depth 32, 4,096,000 profile node visits, 10,000 pivots and payoff magnitude at most 1,000,000. Each limit fails closed. Plan counts grow exponentially with private combos and decisions; a broader game needs a separately validated sequence-form method rather than a raised normal-form cap.

## Frozen-policy integration and lineage

`SixMaxHistoryPhysicalMaxmin` accepts only an opaque fully assessed/replayed joint study, or an opaque accepted CFR derivative. A raw checkpoint/report pair cannot manufacture this input. It selects physical cases above the declared target by descending gap, with deterministic support-order ties, history reach at least 0.0001 and at least two combos at 5% mass for each active player. Selection is capped at 64; reaching that cap does not certify unselected cases.

Only existing rows in the selected local namespace can change. Preflop, every unselected row, the full information-set key set and predecessor joint iteration count remain fixed. The complete parent and every reached conditional case are reassessed. Export requires all selected local targets and no regression in parent NashConv, weighted local gains or maximum conditional gap beyond 1e-9bb. Rejected attempts export diagnostics and **no policy**.

The new schemas are `six-max-history-physical-maxmin-policy/v1`, `six-max-history-physical-maxmin-refinement/v1` and `six-max-history-physical-maxmin-decision-stability/v1`. They bind the exact source/spot/rank/physical payoff/game identity, root checkpoint, predecessor kind, actual input artifact/report, frozen preflop and output policy. The report records each matrix hash, both plan mixtures, information sets with own histories, tree/profile traversal counts, simplex pivots and independent behavioral best responses.

Replay reconstructs the entire input lineage, every matrix, optimization result, behavioral row and full-game report. A derivative cannot be replayed against the raw joint policy with the same root checkpoint. The policy retains 500 joint CFR iterations; simplex pivots are separate work, never mislabeled as CFR training. Existing CFR/suit artifacts and hashes retain their identities.

## Saved combined result

The inputs are the existing twelve-world 3bb-open/9bb-re-raise source, 530-board menu, exact payoff table, fresh 500-iteration study, and accepted `accurate-64` CFR derivative. Files share `docs/data/sixmax-staged-history-physical-`:

- `maxmin-policy.json.gz`: accepted combined derived policy.
- `maxmin-refinement.json.gz`: complete model/lineage, twenty matrix solves and before/after diagnostics.
- `maxmin-decisions.json.gz`: fresh-reference action-EV and posterior screen.

| Measurement | Combined result |
| --- | ---: |
| Newly repaired physical cases | 20 |
| Pure plans per actor in each repaired game | 16 |
| Simplex pivots, summed | 286 |
| Payoff profile node visits, summed | 104,960 |
| Strategy rows changed / preserved relative to CFR derivative | 144 / 69,843 |
| Largest repaired local NashConv | About 2.45e-15bb |
| Physical roots meeting 0.001bb gate | 530 / 530 |
| Maximum physical root gap | 0.00046750486bb |
| Full parent NashConv before / after | 0.00136982526 / 0.00136880895bb |
| Reach-weighted local NashConv after | 0.00022747669bb |
| Largest conditional gap including aggregate observations | 2.69278728bb |
| Independently screened / retained physical cases | 32 / 28 |
| Material / stable own-hand decisions | 151 / 143 |
| Retained fraction of **all** heads-up reach | 0.131820% |

Solution hash: `9e496c516b335ff34e992ac59a77e4d188d17af648e9d8b09900dcf88933add8`. Frozen preflop hash remains `212d93753addaa772ac2ac06affd36398f510a708896da92b501f11a0dd1a21e`. The complete game still has 69,987 information sets, including 9,161 preflop rows.

Root accuracy does not certify inferior/off-path action EVs. The screen still uses independent fresh 500/1,000-iteration CFR+ references, fixed primary-question posterior transfer, all-action EV drift, both cross-mixture regrets and on-policy posterior distance. Four sampled board/history cases remain unstable; 498 eligible cases are unexamined and receive no retained credit. The existing coverage denominator and 25% retained target remain unchanged.

An earlier direct repair of the raw joint policy passed root quality but retained only 21 of 32 screened cases. It remains an ignored experiment, not a shipped artifact. Preserving the accepted CFR improvements before maxmin repair restores the 28-case retained result. The combined evidence does not claim that maxmin improves those separate EV-stability failures.

## Reproduction and controls

From the repository root with Java 21 and Maven:

```powershell
mvn -q -pl solver -am install -DskipTests
$env:MAVEN_OPTS = '-Xmx2g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalMaxminDecisionStabilityMain' '-Dexec.args=replay-derived docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-accurate-64-policy.json.gz docs/data/sixmax-staged-history-physical-accurate-64-refinement.json.gz docs/data/sixmax-staged-history-physical-maxmin-policy.json.gz docs/data/sixmax-staged-history-physical-maxmin-refinement.json.gz docs/data/sixmax-staged-history-physical-maxmin-decisions.json.gz'
mvn -q -pl solver -am test '-Dtest=FiniteMatrixMaxminTest,FiniteTwoPlayerMaxminTest,SixMaxHistoryPhysicalRefinementTest,SixMaxHistoryPhysicalRefinementArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`SixMaxHistoryPhysicalMaxminMain refine-derived` takes the first nine paths above (through the new maxmin report) followed by `<maximum-cases> <target-gap-bb>`; both output paths must be new. `replay-derived` reproduces those saved outputs. `refine`/`replay` omit the CFR artifact/report paths and explicitly use a fresh joint-study predecessor. The screen CLI supports matching `screen-derived`/`replay-derived` and raw `screen`/`replay` forms. All paths are checked for normalized and file aliases before input work; all decompressed byte caps remain enforced.

Tests cover matching pennies, rock-paper-scissors, dominance, negative constants, rectangles, duplicate plans, seeded analytic 2×2 games, additive games, affine transforms and malformed/numerically bounded inputs. Extensive-game controls demonstrate hidden-action separation, correct own-prefix conversion, fixed folded utilities and rejection of imperfect recall, changing inactive payoffs, third actors, invalid chance, inconsistent actions and excessive trees/plans. Saved study tests independently enumerate pure responses for **all twenty repairs** before/after and independently compute every screened primary action EV from pot/share accounting. Tampered matrix hashes, traversal work, policies and predecessor/screen lineage fail replay; an empty selection exports no policy and cannot be screened.

`docs/data/finite-maxmin-independent-controls.json.gz` contains 120 literal matrix controls and two original-payoff LP certificates generated independently with SciPy HiGHS. CI recomputes their original-matrix bounds and compares the owned Java solver; it does not require SciPy. `scripts/generate-maxmin-controls.py` reproduces the research oracle with locally installed NumPy/SciPy and a new output path. These are mathematical test matrices, not external poker strategy data. No production dependency or library defect was introduced or identified.

The next model milestone is the [sparse physical-capacity prototype](sixmax-broader-physical-capacity-design.md). The current 600-revelation cap makes 25% retained coverage impossible even with perfect local policies; improving convergence does not remove that constraint.
