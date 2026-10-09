# Affine sequence-form solver

PokerLab's separately identified `FiniteTwoPlayerAffineSequenceForm` eliminates dependent realization masses before calling the existing owned LP solver. It makes larger bounded two-player betting trees tractable without raising the tree, sequence, LP, heap or CI limits. The original [full-flow sequence-form solver](sixmax-owned-sequence-form.md), its algorithm identity and all saved evidence remain supported.

This is still a numerical solution of the supplied bounded, perfect-recall, two-active-player constant-sum game. Six seats do not mean six decision makers. It adds no realistic range, rake model, board coverage, multiplayer equilibrium certificate or trainer admission.

## Algebra and original-game certification

Both paths share a checked immutable snapshot, original joint-chance payoff matrix and original own-action histories. The caller is read once. The lower active seat is the row maximizer, regardless of who acts first. Every other seat must have a fixed terminal utility; varying inactive all-in payouts are unsupported.

At each information set, retain the first `k-1` child realization masses as nonnegative variables. Reconstruct the last child as its parent mass minus the retained children. The empty sequence has mass one. A forced action introduces no variable. Dependency order follows the own-prefix graph rather than label or sequence-index order.

For the first actor this gives `x=x0+Tz`, with `Pz<=p` ensuring residual-child nonnegativity. The second actor similarly has `y=y0+Uw`, `Qw<=q`. Each private information set has its own conservation row; there is no global simplex across private hands. Integer coefficients are checked exactly, including `E*x0=e` and `E*T=0`, before any numerical optimization.

Preserve the original joint-chance payoff `A` and compute:

```text
c0 = x0' A y0       a = T' A y0
b  = U' A' x0      D = T' A U
payoff = c0 + a'z + b'w + z'Dw

row maximum:    max a'z - q'lambda
                -D'z - Q'lambda <= b, Pz <= p, z/lambda >= 0
                lower = c0 + maximum

column maximum: max -b'w - p'mu
                Dw - P'mu <= -a, Qw <= q, w/mu >= 0
                upper = c0 - maximum
```

The constant `c0` is essential, including when every action is forced. Staged matrix products avoid a four-index payoff loop. Both programs use nonnegative variables directly, with no free-variable splitting or doubled flow equalities. The existing signed-RHS LP independently certifies its original primal and dual inequalities and objective gap.

After solving, reconstruct **every original sequence**, check the original conservation/nonnegativity constraints, convert to behavior using the existing zero-own-reach rule, then compute independent information-set best responses over the **entire original snapshot**. Both LP bounds, profile utilities and best-response utilities must agree within `1e-8`. A private-constructor result is returned only after these checks. A public audit record or caller-supplied projection cannot create that result.

The public immutable audit contains complete original flows, integer projections and mappings, original/projected payoff identities, both original LP certificates, separate reduction work and the full behavioral quality report. Compiler failures distinguish invalid input, exhausted work and numerical failure; LP failures retain their existing type. Rejection provides no strategy handle or admission certificate.

The additional reduction cap is **8,000,000 deterministic loop-size units** across projection construction, symbolic checks, payoff reduction, both LP assemblies and full realization reconstruction. It is separate from each LP's unchanged 4,000,000,000-unit budget. It is not a CPU instruction count or timing measurement. Public callers cannot raise the cap; the package test seam only lowers it. All existing 20,000-node, depth-32, 64-information-set, 129-sequence, 16-action and LP bounds remain unchanged.

## Production physical-board derivative

`SixMaxHistoryPhysicalAffineSequenceForm` accepts the existing opaque validated study or accepted CFR derivative. Its policy, refinement and independent decision-screen schemas are distinct from the full-flow path. It binds the complete model, root checkpoint, actual predecessor policy/report, frozen preflop and resulting strategy hashes. It preserves strategy support, all unselected rows and the predecessor's joint iteration count.

Selection and acceptance remain unchanged: material literal boards above target, deterministic gap order, at most 64 repairs, all selected local targets and complete parent/every-conditional no-regression gates. Exact replay reruns all construction, optimization and diagnostics. Rejected attempts export only diagnostics. CLI aliases, hardlinks and existing output files are rejected before input loading.

Saved files share `docs/data/sixmax-staged-history-physical-affine-sequence-form-` and end in `policy.json.gz`, `refinement.json.gz`, and `decisions.json.gz`.

| Saved measurement | Full-flow path | Affine path |
| --- | ---: | ---: |
| Repaired literal-board cases | 20 | 20 |
| Original sequences per actor per repair | 9 | 9 |
| Variables / inequalities per LP per repair | 19 / 19 | 8 / 8 |
| Summed LP pivots | 838 | 140 |
| Summed charged LP work | 9,983,740 | 273,760 |
| Separate charged affine reduction work | — | 27,200 |
| Changed / preserved strategy rows | 144 / 69,843 | 144 / 69,843 |
| Original joint iterations retained | 500 | 500 |
| Accurate reached physical roots | 530 | 530 |
| Independently screened / retained cases | 32 / 28 | 32 / 28 |
| Material / stable own-hand decisions | 151 / 143 | 151 / 143 |
| Retained fraction of all heads-up reach | 0.131820% | 0.131820% |

These are saved-instance work comparisons, not universal timing claims. Affine policy hash: `d90b6739bd17297d76d7c892c58fd20b947e2ae5f31023a97ee7c6b9fe35cec6`. Frozen preflop hash: `212d93753addaa772ac2ac06affd36398f510a708896da92b501f11a0dd1a21e`. Parent NashConv is `0.001368808948992867bb`; the largest repaired local gap is below `9e-16bb`. Different policies at indifferent actions are possible.

The fresh 500/1,000-iteration reference screen still checks every primary action EV, posterior transfer and cross-mixture regret. Four sampled cases remain unstable and 498 eligible cases remain unexamined, receiving zero retained credit. The 25% retained-content target is unchanged and unmet. All three artifacts remain `VALIDATION_ONLY`, `trainerAdmission=false`. [Broader observation capacity](sixmax-broader-physical-capacity-design.md), realistic ranges and multiway strategy quality remain separate milestones. AWS deployment remains paused.

## Controls and reproduction

Source JUnit controls cover known Kuhn value, perfect recall and forgetting rejection, correlated joint chance, immutable single snapshots, zero-own-reach behavior, exact sequence limits, positive/negative constants, shifts/scales, both actor orders and forced actions to depth 32. An external-package client compiles against every new public audit component. Exact/exhausted/raised compiler budgets are checked.

Independent own-action products and original bilinear payoff loops check **200 shuffled flow pairs / 20,000 pure and mixed profiles**. **160 literal reduced LPs plus 160 reordered/scaled variants** check original primal/dual certificates against independent HiGHS values. **54 synthetic one-bet games** span 2/4/8/16/24/32 private types, three synthetic share models and half/pot/double-pot bets; all remain within the unchanged limits. The largest one-bet control has 129 sequences and 128 variables/inequalities per LP. The full-flow implementation previously exhausted its LP work cap on the larger members of this family.

**15 synthetic fixed-raise trees** use 1/2/4/8/16 private types, correlated joint chance, fixed raises 8/22/100bb, fold/call payoffs and fixed folded-seat loss. They match an independent original full-flow HiGHS oracle; three 24-type cases reject the unchanged sequence cap. These controls use supplied synthetic shares, **not cards, equity enumeration, realistic preflop ranges or a playable cash strategy**. Saved full literal trees and an optional verifier allow reproduction without relying on the affine compiler. SciPy/NumPy are research-only dependencies.

The existing costly physical fixtures now also check complete affine saved replay, all 20 independent pure-plan responses, every primary action EV, frozen rows and iteration counters. Modified projection, constant, dimensions, work, policy, predecessor and screen/admission claims must fail replay. Original full-flow saved replay remains in the same suite. Static fixture references are released only after every assertion completes.

```powershell
mvn -q -pl solver -am test '-Dtest=*Affine*Test,FiniteTwoPlayerSequenceFormTest,BoundedLinearProgramTest,SixMaxHistoryPhysicalRefinementTest,SixMaxHistoryPhysicalRefinementArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
python scripts/verify-affine-sequence-form-controls.py
```

The optional Python verifier performs 160 literal reduced-LP solves and independently reconstructs/solves all 15 full-flow raise trees. It writes no files and is not used by the application or required CI.

For saved complete lineage and fresh-reference replay after compiling/installing the modules:

```powershell
$env:MAVEN_OPTS = '-Xmx2g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalAffineSequenceFormDecisionStabilityMain' '-Dexec.args=replay-derived docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-accurate-64-policy.json.gz docs/data/sixmax-staged-history-physical-accurate-64-refinement.json.gz docs/data/sixmax-staged-history-physical-affine-sequence-form-policy.json.gz docs/data/sixmax-staged-history-physical-affine-sequence-form-refinement.json.gz docs/data/sixmax-staged-history-physical-affine-sequence-form-decisions.json.gz'
```

`SixMaxHistoryPhysicalAffineSequenceFormMain refine-derived` takes paths through the two affine outputs, then `<maximum-cases> <target-gap-bb>`. `replay-derived` checks saved outputs. `refine`/`replay` omit the two CFR-predecessor paths. The screen CLI uses `screen-derived`/`replay-derived` or the raw-study forms. Generate new paths rather than overwriting saved evidence.
