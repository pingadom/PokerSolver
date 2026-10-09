# Explicit behavioral-floor solver

PokerLab now has a separately identified owned solver for bounded two-player, perfect-recall games in which every legal action has a declared minimum probability. It is a diagnostic backend, not newly admitted trainer content. The original unrestricted solver, saved packs and admission gates retain their identities.

## Why this exists

The actual-card CO/BB five-target preflop game has multiple unrestricted equilibria. An opponent's behavior at an unreached information set can change the EV of an alternative raise without changing reached play or the global best-response gap. More CFR iterations alone cannot certify those feedback values. An explicit action floor makes every own-action sequence positive and gives a separately defined problem to compare across independent algorithms. It changes the game; it is not a hidden numerical adjustment or proof that the original game's feedback is unique.

The fixed test game remains the [actual-card conditional preflop model](sixmax-actual-preflop-conditional-study.md): CO versus BB at a six-seat table, twelve correlated joint card worlds including folded blockers, 100bb stacks, no rake, raise targets 3/9/22/50/100bb, and mandatory checkdown after non-all-in calls. It does not supply realistic cash ranges or strategic postflop betting.

## Owned solve and certificates

`FiniteTwoPlayerBehaviorFloor` uses algorithm identity `BOUNDED_BEHAVIOR_FLOOR_AFFINE_SEQUENCE_FORM_OWNED_LP/v1`. The caller must supply epsilon in `[1e-6, 0.01]`. For every original conservation row and every child sequence it imposes:

```text
q_child >= epsilon * q_parent
```

The existing exact integer projection proves original conservation with `q = q0 + T*z`. Substitution produces explicit double-valued floor inequalities. Both maxmin LPs use nonnegative independent variables and nonnegative inequality duals. Neither signed full-flow equality splitting nor exponential pure-plan enumeration is needed in the production solver. The existing owned LP, its tolerances and all dimension/work caps remain unchanged; compilation has a separate eight-million-unit cap.

The result includes the immutable snapshot hash, declared floor, reduction hash, both projection and original realization audits, every floor constraint, both LP certificates and the complete behavioral policy hash. Reconstruction checks every original sequence, relative behavioral conservation and relative floor violation. Zero-own-reach uniform fallback is forbidden. A numerical failure returns no solved result.

Two distinct quality reports accompany each result:

- Independent bottom-up **floor-constrained** information-set best responses certify the modified game and both LP value bounds to `1e-8bb`.
- Existing **unrestricted original-game** best responses measure how far the floored profile is from equilibrium in the unmodified game. A tiny constrained gap does not replace this measurement.

`FiniteTwoPlayerFloorCfr` is a separate exhaustive alternating CFR+ reference with algorithm identity `BOUNDED_BEHAVIOR_FLOOR_ALTERNATING_CFR_PLUS/v1`. Regret matching selects an intent distribution `s`; actual play is `epsilon + (1-k*epsilon)*s` for `k` actions. Counterfactual regrets compare intent utilities with the same scale, while all reach and linear average weights use actual played probabilities. Each target pass freezes its strategy before traversing all worlds. Runs are bounded by 10,000 iterations and twenty million visits, with work preflight and complete independent response reports.

## Actual-card results

The optional independent HiGHS oracle reconstructs original full-flow programs directly from a literal chance/action/terminal tree. It adds nonnegative floor slacks and free conservation duals, without reading the owned affine reduction. All three certified owned values match it within `1e-8bb`:

| Minimum action probability | CO constrained value | Unrestricted original-game gap |
| ---: | ---: | ---: |
| 0.01 | 0.798358911170816bb | 0.049111949068265bb |
| 0.001 | 0.789687700619478bb | 0.004099548061572bb |
| 0.0001 | 0.789117076324427bb | 0.000409946612888bb |

The first integer-projection floor representation rejects this game at `0.00001` and `0.000001` because its LP cannot certify the numerical result. The rejection is preserved; no shared tolerance was loosened. This limitation belongs to that representation, not an identified upstream library bug. The separately identified excess-mass follow-up below certifies both smaller floors.

The saved `heads-up-preflop-five-target-floor-report.json` uses epsilon `0.0001`. Its constrained gap is zero to rounding and its original-game gap passes the unchanged `0.001bb` threshold. It still fails the full feedback screen:

| Fresh floor CFR+ reference | Constrained gap | Original-game gap | Largest fixed-question action-EV drift |
| ---: | ---: | ---: | ---: |
| 500 iterations | 0.00343266bb | 0.00382566bb | 0.41644377bb |
| 1,000 iterations | 0.00088064bb | 0.00126473bb | 0.59931923bb |

Three of five material questions pass the action-EV drift component at each budget, but **zero of five passes every required gate at both budgets**. Original-game reference quality also fails. All ten information sets are reported, including the five nonmaterial rows, which receive no stable credit. The screen retains the original material reach, hand diversity, `0.001bb` global quality, `0.01bb` action EV/cross-mixture regret and `0.01` posterior-distance gates.

Action feedback keeps its existing meaning: unrestricted optimal hero continuation against a fixed opponent, from the current decision with already committed chips treated as sunk. Every reference's alternative action is evaluated on the same candidate question posterior. These EVs are explicitly separate from the floor-constrained best-response certificate.

## Nonnegative excess-mass follow-up

`FiniteTwoPlayerSlackFloor` preserves the first representation and uses its own algorithm identity, `BOUNDED_BEHAVIOR_FLOOR_SLACK_SEQUENCE_FORM_OWNED_LP/v1`. For every information set, all but one child has a free nonnegative excess mass `z`; the last child is dependent:

```text
q_free = epsilon*q_parent + z_free
q_last = (1-(k-1)*epsilon)*q_parent - sum(z_free)
sum(z_free) <= (1-k*epsilon)*q_parent
```

There is one explicit inequality per original information set. Nonnegative free masses encode the other action bounds implicitly. A topological own-action traversal constructs an **epsilon-dependent double-valued** affine projection; it does not relabel this as the earlier exact integer projection. Every original conservation coefficient is independently checked to `1e-12`. Projection, original flow and reduced payoff identities are recorded separately. Reconstruction follows the same local excess-mass recurrence, checks every relative behavioral floor and conservation row, and compares the result with the affine realization. The shared LP, numerical tolerances and work/dimension caps remain unchanged.

Both smaller floors now pass owned LP bounds, complete original-flow/relative-floor checks and independent constrained information-set best responses:

| Minimum action probability | CO constrained value | Unrestricted original-game gap |
| ---: | ---: | ---: |
| 0.00001 | 0.789061245659269bb | 0.000040994579356bb |
| 0.000001 | 0.789055674932289bb | 0.000004099457116bb |

The optional original full-flow/slack HiGHS oracle agrees at all five floors. JUnit independently enumerates every extreme floored intent plan for each actual-card solution, checks nested own-action and hidden-chance games, verifies action-reordering identity, and solves a 32-private-type control within the original bounds. This new library solver returns its own opaque result and cannot manufacture the existing trainer's accepted study handle.

The saved diagnostic report above intentionally continues to replay the first representation and its original fixed reference budgets. Mathematical certification of the smaller-floor solutions does not imply a completed independent feedback screen or trainer admission for them.

## Replay and verification

`SixMaxHeadsUpFloorStudy` exports a single bounded diagnostic report with status `DIAGNOSTIC_ONLY_NO_TRAINER_ADMISSION` and `trainerAdmission=false`. It has a private-constructor result handle, exports no trainer policy and cannot substitute for the existing trainer's accepted study handle. Exact replay validates source identity, reconstructs the game, reruns the owned LP and both fresh CFR references, then compares every policy, certificate, feedback row and diagnostic field. Plain JSON and bounded expanded gzip are supported; outputs use exclusive creation.

Run from the repository root with the solver runtime classpath:

```text
java -cp <solver-runtime-classpath> com.pokerlab.solver.SixMaxHeadsUpFloorStudyMain solve docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-five-target-specification.json 0.0001 <new-report.json[.gz]>
java -cp <solver-runtime-classpath> com.pokerlab.solver.SixMaxHeadsUpFloorStudyMain replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/heads-up-preflop-five-target-floor-report.json
python scripts/verify-behavior-floor-controls.py
```

The Python oracle requires optional NumPy/SciPy and is not an application or CI dependency. JUnit independently compares its literal fixture with every live chance node, six-card world, legal action and terminal payout. Tests also enumerate all extreme floored intent plans on small hidden-information/repeated-own-action games and the actual CO/BB game, check CFR visit accounting and analytic values, and reject forged replay data, unsupported schemas, oversized input, output collisions and invalid budgets.

The next step is to investigate how to select stable alternative-action continuations and improve bounded numerical conditioning, then repeat the unchanged complete feedback screen. The existing two validated offline trainer menus remain usable. This work does not increase the physical-board retained coverage, establish a six-player cash equilibrium or resume AWS deployment.
