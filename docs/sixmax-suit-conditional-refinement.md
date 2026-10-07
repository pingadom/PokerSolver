# Targeted conditional refinement

Status: offline validation evidence, 7 October 2026. This stage improves selected decisions in the [bounded suit model](sixmax-suit-refinement.md), using its saved 500-iteration joint policy as the predecessor. It does not expand the game, observe more boards, resume the joint optimizer, or admit a trainer pack.

## Why a separate stage

The predecessor's complete-game NashConv is 0.0013595868bb, but its largest reached conditional gap is 6.5968356bb. A rare decision contributes little to the weighted average even when its local strategy is poor. Additional local training directs computation to those cases while preserving the preflop ranges that determine their private-card posteriors.

Three modes make the choice explicit:

- `LARGEST_GAP` ranks cases by conditional NashConv.
- `REACH_WEIGHTED_GAP` ranks by history probability × observation probability given that history × conditional NashConv.
- `BALANCED_GAP_AND_REACH` alternates the two queues, starting with largest gap and skipping already selected cases. Equal priorities retain the bound menu order and then observation index.

Only reached cases above the requested target are eligible. Zero-history and unsupported-observation statuses remain in the complete diagnostics, never become training roots, and cannot manufacture a lesson. No eligible cases produces a rejected no-op report.

## Posterior, training and assembly

For each selected public history and observation, the local chance roots use the complete joint distribution:

`P(world | actions, observation) ∝ P(world | actions) × physicalObservationCount(world) / 9880`.

All six private hands, including folded blockers and card exclusions, remain in each world. This is not a product of the two active players' marginal ranges. The shared posterior implementation also checks finite, normal reach probabilities; unsupported or underflowed distributions cannot silently pass.

Every listed budget starts a fresh exhaustive CFR+ solve with zero regrets and literal fixed-utility pruning. Budgets are strictly increasing, each between 1 and 1000, with at most eight trials and 64 selected cases. A local tree may contain at most 1000 states; the complete parent retains its existing one-million-state cap. The default evidence uses fresh budgets 10, 100, 500 and 1000, stopping once a selected solution reaches 0.001bb. The best measured trial is retained, rather than assuming finite-budget quality decreases monotonically.

Assembly replaces all player rows in that conditional game together. It checks that every row already belongs to the parent, no cases overlap, the parent support remains complete, every preflop row is identical, and every unselected row is identical. The candidate retains the predecessor's `solution.iterations = 500`; that field describes its predecessor and must not be read as total or resumed training. Each branch records its chosen local budget, all attempted fresh trials, local solution hashes, six-player quality and traversal counts.

## Acceptance and reproducibility

The candidate receives a fresh unrestricted six-player parent best response and a fresh audit of **all 7806 reached cases**. Each selected conditional result must equal its independently trained local result after assembly; every unselected conditional record must remain identical. Direct unilateral response witnesses again match the reach-weighted local gains and stay below the unrestricted parent deviations.

Export requires every selected case to meet its requested target, no parent-total NashConv regression, no reach-weighted conditional regression, and no maximum conditional regression. Comparisons allow only 1e-9bb arithmetic tolerance. The parent gate concerns total NashConv; it does not assert that every player's individual deviation gain improves. A failing candidate exports a diagnostic report and no policy. Policy output must be a new path, so rejection cannot leave an old candidate masquerading as the result; aliased input/output paths and hard links are rejected before work begins.

Accepted policies use `six-max-suit-conditional-derived-policy/v1`, distinct from the fresh joint checkpoint schema. They bind the canonical complete predecessor checkpoint hash, predecessor solution hash, unchanged game hash, frozen preflop hash, settings and candidate hash. The predecessor checkpoint transitively binds source pack, spot, rank table, suit table, public menu, algorithm and complete support. Neither accepted artifact nor report can be labelled a fresh joint CFR+ checkpoint.

Read-only replay reconstructs the predecessor game, selects the cases again, reproduces every fresh local trial, reassembles the policy and remeasures all parent/conditional diagnostics. It requires exact artifact and report equality. Replay therefore verifies the recorded training provenance, rather than trusting a replacement strategy with a self-reported quality score. Synthetic all-tie accounting tests additionally exercise known 6.5bb folding mistakes, rejected budgets, zero reach, balanced/weighted selection, changed lineage/settings/rows, aliases and parent regression. Saved evidence tests independently enumerate pure plans for the five largest selected gaps before and after refinement.

## Measured results

All three experiments start from the same predecessor. Each selected case reaches at most 0.001bb, while all cases outside the selection keep their old strategy and conditional quality.

| Policy | Selected cases | Parent NashConv (bb) | Reach-weighted local NashConv (bb) | Largest conditional gap (bb) | Cases above 0.01bb |
| --- | ---: | ---: | ---: | ---: | ---: |
| Predecessor | 0 | 0.0013595868 | 0.0002240485 | 6.5968356 | 2934 |
| Largest gap | 32 | 0.0013588522 | 0.0002234587 | 1.2831518 | 2902 |
| Reach weighted | 32 | 0.0012826979 | 0.0001867098 | 6.5968356 | 2933 |
| Balanced | 64 | 0.0012819633 | 0.0001861200 | 1.2831518 | 2901 |

The balanced run reduces the maximum local gap by about 81%, the parent total by about 5.7%, and the weighted local total by about 16.9%. It replaces 488 of 70703 information sets and preserves 70215. Nineteen selected cases observe literal boards; the other 45 still observe only rank/texture. Two cases choose 100 fresh iterations, 61 choose 500, and one chooses 1000. Across all 191 trials, the solver visits 8620640 nodes and 3812000 terminals and prunes 1524800 fixed-utility suffix roots, with zero sampled chance nodes or baseline corrections. These are deterministic observed work counts, not a wall-clock benchmark.

The three policy hashes are:

- Largest: `8c61070f9249b2e79be2c2f4f8f503c5927203f290caaf037f8c5d44d468e4ba`.
- Reach weighted: `69a6bf54f45c0a5df5775d5aa56f54c0d7240861c8109753e58ae163090742a3`.
- Balanced: `9c0ff927de44cfedd29d075b35b1821800e4b45b8da8b54037d4bf3ce3cc50cf`.

All bind predecessor checkpoint `9387b3eca90b0d1d35b806a6c5d893a0957c6ff03535fc9f5df313eefcf47e9b`, predecessor solution `b72b4aa36698eb274dfc9bef6ee2549c8e6952bcc85633fff43bb68569719c38`, and frozen preflop `b6584162768c9196a064c246a63e292e9a06756a7872f66d230a82ab459a1c82`. Saved policy/report pairs are `docs/data/sixmax-staged-suit-conditional-{largest,weighted,balanced}-500-{policy,report}.json.gz`.

## Reproduce

From the repository root, after installing local dependencies:

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitConditionalRefinementMain' '-Dexec.args=refine docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz .local/balanced-derived-policy.json.gz .local/balanced-derived-report.json.gz BALANCED_GAP_AND_REACH 64 10,100,500,1000 0.001'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitConditionalRefinementMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-policy.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-report.json.gz'
mvn -q -pl solver -am test '-Dtest=SixMaxSuitConditionalRefinement*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Use a new output path for each refinement attempt. Reports are capped at 32MiB and policies at 64MiB, with existing strict JSON, atomic bounded writes and deterministic gzip. Solver test JVMs default to a 2GiB maximum heap; this prevents machine-sized automatic heap growth from exhausting the Windows paging-file commitment during repeated replay. It is a test-runner resource fix, not an upstream library defect; `-DargLine` can override it explicitly.

## Remaining gate and interview explanation

Acceptance means the bounded research refinement passed its declared checks. It is not trainer admission or a full 6-max equilibrium claim. After the balanced run, 2901 reached cases still exceed 0.01bb, and the worst unselected gap is 1.283bb. Broad ranges, realistic cash rake, richer raises, multiway postflop betting and later streets remain separate work. The model still reveals actual suits on only about 0.88% of heads-up reach, far below the earlier 25% content target; preflop freezing leaves that coverage unchanged. AWS deployment remains paused.

Next, select material physical board/history decisions with adequate hand diversity, measure independent-budget policy stability, and retain those gates before building a lesson pack. Broader observation coverage needs an explicit runtime/memory design rather than silently increasing caps. These three candidates are independent derivatives of the same fresh predecessor; recursive refinement chains are not supported by this schema.

For an interview: explain why a low weighted error can conceal poor rare decisions, derive the full-joint posterior, describe fresh local optimization under frozen ranges, and show why the parent strategy must be re-audited after simultaneous replacements. The balanced experiment demonstrates an explicit tradeoff between worst-case local quality and likely play, with reproducible evidence and preserved model limitations.
