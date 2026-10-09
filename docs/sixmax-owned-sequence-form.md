# Owned sequence-form solver and physical-board integration

PokerLab now solves bounded two-player perfect-recall games without enumerating complete pure plans. The production implementation owns the sequence reduction, two-phase revised simplex, basis factorization, behavioral conversion and validation. No LP library is added to the application. The saved physical-board derivative passes its full policy gate and independent action-EV screen; it remains `VALIDATION_ONLY`, with `trainerAdmission = false`.

## Why this matters

A complete plan chooses an action at every information set, including unreachable ones. With 32 private types and two choices per type, each player has 2^32 = 4,294,967,296 complete plans. The new solver represents that same synthetic game with 65 sequences per player, including the empty sequence. It solves both players' LPs and independently checks the resulting behavioral strategy. This is an algorithm-capacity control, not a realistic 32-combo poker pack.

The formulation follows the realization-plan method in [von Stengel, *Efficient Computation of Behavior Strategies* (1996)](https://doi.org/10.1006/game.1996.0050). Our implementation uses deliberately bounded dense matrices; the sequence representation avoids exponential plan enumeration, but does not make all game trees or matrix computations cheap. The existing normal-form solver remains a separately tested small-game control with its original caps.

## Tree, hidden information and sequence flow

`FiniteTwoPlayerSequenceForm` captures the complete legal game once into an immutable snapshot. Subsequent optimization, evaluation and best responses use this snapshot, so a mutable caller cannot change payoffs between construction and certification. The identity hash includes every terminal utility, ordered action, information-set key, chance outcome and probability, plus the seat count.

Every information set must have a consistent actor, public depth, legal action order and **entire prior own information/action history**. Forgotten private information, forgotten own actions, repeated own information sets and a third decision maker fail before optimization. Terminal utilities must be finite and bounded; the two active seats must have the same terminal sum and every other seat a fixed utility. The lower seat index is the row maximizer, regardless of who acts first in poker.

Each information-set/action pair defines an own sequence. The empty sequence has realization mass one. At each information set, the sum of its child-action sequence masses equals its preceding own-sequence mass. Chance is accumulated from the complete supplied joint tree at terminal sequence pairs. It is never reconstructed by multiplying private marginals; correlated worlds have their own test with a different game value from the corresponding independent product.

With first-player flow `E x = e`, second-player flow `F y = f`, and joint-chance-weighted first-player payoff matrix `A`, the two programs are:

```text
First player:  maximize f.v    E x = e,  A^T x >= F^T v, x >= 0, v free
Second player: minimize e.u   F y = f,  A y   <= E^T u, y >= 0, u free
```

Free variables become positive-minus-negative components; each equality becomes two inequalities in canonical order. Both programs use the same owned general LP implementation. Behavioral probabilities are child realization mass divided by preceding own mass. Unreachable own prefixes get a complete uniform row. Tiny floating-point ratio noise may be bounded and normalized only within 1e-8, after which the **whole snapshot** must independently pass existing information-set best responses, profile-value/LP-value agreement and both active deviation bounds at 1e-8bb.

The private `Result` constructor is available only to successful solving. Raw JSON audits, caller-provided realization vectors or ordinary strategy containers grant no solved handle.

## Owned LP and numerical policy

`BoundedLinearProgram` maximizes `c.x` subject to `A.x <= b`, `x >= 0`. It copies and validates the original problem before allocation of the working system. Negative right-hand sides use signed slack/surplus columns and artificial variables; phase I removes artificial mass, cleanup removes artificial basis columns, and phase II optimizes the original cost. Original signed slack columns span every row, so cleanup never drops an input constraint.

Every iteration rebuilds a partial-pivot LU factorization from the **original current basis**. This avoids accumulated tableau elimination error. Largest positive reduced cost enters first, with stable index ties. The ratio test uses basis-index ties; any repeated basis hash conservatively switches that phase permanently to Bland, including on a hash collision. There is no environment variable or system property selecting another production algorithm.

Before returning, the solver recomputes nonnegativity, every original primal and dual inequality, original objective values and their absolute gap. Residuals and objective gap must be at most 1e-8. These are numerical certificates, not exact rational proofs. The stable entering/direction threshold is 1e-10, LU singular threshold 1e-12. A resolvably positive reduced cost below the stable entering threshold rejects the solve rather than silently treating an improving direction as optimal. The conservative pricing guard uses basis-objective scale, pricing magnitudes and machine ulps; tiny and cancellation-generated unbounded-objective controls must return no result.

Invalid inputs, exhausted work, singular/nonfinite arithmetic, unresolved pricing or failed original certificates produce a typed rejection and no solved handle. A rejected phase-I or recession step is **not** presented as a mathematical infeasibility/unboundedness certificate. No tolerance promises that every well-posed floating-point LP will be accepted. Sequence-form results additionally require the independent game best-response certificate above.

| Limit | Declared bound |
| --- | ---: |
| Snapshot nodes / depth | 20,000 / 32 |
| Information sets per active actor | 64 |
| Own sequences per actor, including empty | 129 |
| Actions per information set | 16 |
| Individual label / total captured label characters | 1,024 / 2,000,000 |
| LP original nonnegative variables / inequalities | 512 / 512 |
| LP pivots across phase I, cleanup and phase II | 10,000 per LP |
| Charged arithmetic-work units | 4,000,000,000 per LP |
| Input coefficient / terminal payoff magnitude | 1,000,000 |

Work units are deterministic loop-size charges for factorization, triangular solves, column pricing, setup and original certificates. They bound work before those loops; they are neither wall-clock time nor a literal CPU instruction count. Both LP certificates record phase-specific pivots, factorization count, charged work and Bland fallback. Public callers cannot raise these caps. Existing model, normal-form, 2GiB heap and 35-minute CI caps are unchanged.

## Frozen-policy integration and saved evidence

`SixMaxHistoryPhysicalSequenceForm` accepts an opaque fully validated joint study or accepted CFR derivative. It has separate policy/refinement/screen schemas and algorithm identity. It preserves the root checkpoint lineage, actual predecessor artifact/report hashes, frozen preflop, complete strategy support, every unselected row and joint CFR iteration count. This is a derived strategy, not a resumed joint solve. LP pivots are recorded separately from the predecessor's 500 training iterations.

The selection and acceptance rules match the existing physical-board repair: material literal boards above the declared local target, deterministic descending-gap selection, at most 64 cases, full joint posteriors, all selected local targets, and no parent/weighted-local/maximum-gap regression beyond the existing 1e-9 tolerance. Replay reruns selection, snapshots, reductions, both LPs, behavioral conversion, the complete parent and **every reached conditional case**. Rejected attempts export a diagnostic report and no policy.

The independent decision screen still solves fresh 500/1,000-iteration CFR+ references and checks primary-question posterior transfer, all-action EV drift, both cross-mixture regrets and posterior distance. Accurate local root values alone cannot pass that screen.

Saved files share `docs/data/sixmax-staged-history-physical-sequence-form-`:

- `policy.json.gz`: accepted derived policy.
- `refinement.json.gz`: complete input lineage, sequence/flow/LP audits and before/after diagnostics.
- `decisions.json.gz`: independent-reference own-hand action-EV screen.

| Measurement | Saved result |
| --- | ---: |
| Repaired cases / sequences per actor in each case | 20 / 9 |
| Snapshot nodes per case | 55 |
| Pivots, both LPs across all repairs | 838 |
| Charged LP work units, summed | 9,983,740 |
| Changed / preserved strategy rows | 144 / 69,843 |
| Complete strategy rows / joint iterations retained | 69,987 / 500 |
| Largest repaired local NashConv | 6.67e-16bb |
| Physical roots passing 0.001bb / maximum physical gap | 530 / 0.00046750486bb |
| Full parent NashConv before / after | 0.00136982526 / 0.00136880895bb |
| Reach-weighted local NashConv after | 0.00022747669bb |
| Largest conditional gap, including aggregate observations | 2.69278728bb |
| Independently screened / retained physical cases | 32 / 28 |
| Material / stable own-hand decisions | 151 / 143 |
| Retained fraction of **all** heads-up reach | 0.131820% |

Policy hash: `02b7cd59a91f21370a5cd380d4076bfc1925fbdedb75141315c3f3f1ffc346f2`. Frozen preflop hash stays `212d93753addaa772ac2ac06affd36398f510a708896da92b501f11a0dd1a21e`. Each repaired value agrees with the independently implemented normal-form control; equilibrium policies may differ at indifferent actions. This small physical game needs more pivots than the old 286-pivot normal-form path. No universal speedup is claimed.

Four sampled cases remain unstable and 498 eligible cases are unexamined, receiving no retained coverage credit. The 25% retained-content target remains unmet. This solver adds neither physical boards nor rake, realistic ranges, general six-player equilibrium or later-street betting. Those remain separate milestones in the [broader capacity design](sixmax-broader-physical-capacity-design.md). AWS deployment remains paused.

## Reproduction and independent controls

Java 21 and Maven, from the repository root:

```powershell
mvn -q -pl solver -am install -DskipTests
$env:MAVEN_OPTS = '-Xmx2g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalSequenceFormDecisionStabilityMain' '-Dexec.args=replay-derived docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-accurate-64-policy.json.gz docs/data/sixmax-staged-history-physical-accurate-64-refinement.json.gz docs/data/sixmax-staged-history-physical-sequence-form-policy.json.gz docs/data/sixmax-staged-history-physical-sequence-form-refinement.json.gz docs/data/sixmax-staged-history-physical-sequence-form-decisions.json.gz'
mvn -q -pl solver -am test '-Dtest=BoundedLinearProgramTest,FiniteTwoPlayerSequenceFormTest,SixMaxHistoryPhysicalRefinementTest,SixMaxHistoryPhysicalRefinementArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`SixMaxHistoryPhysicalSequenceFormMain refine-derived` takes the first nine file paths through the refinement report, followed by `<maximum-cases> <target-gap-bb>`. Both output paths must be new. `replay-derived` rechecks saved outputs. `refine`/`replay` omit the CFR predecessor paths. The screen CLI supports `screen-derived`/`replay-derived` and corresponding raw-study forms. Every CLI checks normalized/file aliases and output existence before input loading; old bounded decompression limits still apply.

`solver/src/test/resources/bounded-lp-oracle-controls.json.gz` stores 292 literal original LPs: both programs for twenty physical trees and six synthetic hidden-type games, plus 240 seeded generic bounded problems with negative right-hand sides, active equalities, duplicate rows and random costs. Independent [SciPy HiGHS](https://docs.scipy.org/doc/scipy/reference/optimize.linprog-highs.html) values are offline mathematical controls, not external poker strategy data. CI needs no Python/SciPy: Java checks every original primal/dual certificate and oracle value, then two seeded row permutations and one positive power-of-two row scaling per problem (876 more variants). `python scripts/verify-sequence-form-lp-controls.py` independently recomputes all saved oracle values with optional local NumPy/SciPy.

Game tests cover known Kuhn value −1/18, hidden-action separation, full own-prefix recall, unreachable own rows, correlated chance, deterministic identities and all six hidden-type sizes. Original normal form controls small cases; independent best responses check every accepted strategy. Numerical controls include degenerate cycling with Bland fallback, malformed inputs, work exhaustion, infeasible/unbounded examples and near-zero improving directions. Physical tests reuse fully replayed payoff/study fixtures, independently enumerate all twenty repaired pure responses, compute every screened primary action EV from pot/share accounting, and reject changed snapshot/reduction/flow/work/policy/lineage/admission fields and file aliases.
