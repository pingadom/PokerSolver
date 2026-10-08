# Conditional refinement and decision stability in the history-specific board model

PokerLab can now derive and replay a targeted postflop policy in the [history-specific physical observation model](sixmax-history-physical-observations.md), then check the action EVs each player would see in a trainer. The saved accurate-board derivative passes its research quality gate; 28 of 32 screened board/history cases pass every material decision check. This is still a bounded research sample, with `trainerAdmission = false`.

## Frozen policy, fresh local work

The predecessor is the separately trained 500-iteration policy for the fixed 530-board menu. Its model, twelve legal hidden worlds, six selected heads-up histories, 100bb stacks, no rake, 3bb open/9bb re-raise rules and one half-pot flop bet remain unchanged. Turn and river are integrated into exact showdown shares. Aggregate fallback observations and unselected/multiway checkdown branches retain their existing semantics.

`SixMaxHistoryPhysicalStudy.Validated` is an opaque result constructed only after a complete study assessment or exact report replay. It binds the full checkpoint and report to the verified payoff model. The downstream optimizer can reuse that complete assessment without accepting a caller-supplied report as evidence or repeating its expensive parent audit.

For each selected physical board/history pair, refinement:

1. Freezes every preflop strategy row and reconstructs the complete joint private-world posterior from public history and board support. It does not multiply independent player marginals.
2. Runs exhaustive CFR+ from zero regrets at declared fresh budgets, with literal fixed-utility pruning. Budgets are independent trials, not resumed or cumulative training.
3. Computes information-set best responses and retains a local trial only if it improves that case's NashConv. It stops when the best case meets the declared target, so an already accurate predecessor can remain unchanged.
4. Replaces only that local game's existing information sets, keeping every other row exactly unchanged. It retains the predecessor's joint iteration count, complete support and frozen preflop hash.
5. Reassesses every reached history/observation case and the complete parent game's unilateral responses. A candidate must meet every selected local target and must not worsen parent NashConv, reach-weighted local gains or the worst local gap beyond the existing 1e-9bb comparison tolerance.

Only an opaque successful refinement result exports a derived policy. Failed candidates export a diagnostic report, including their hypothetical full-parent score, but no policy file. The new artifact schemas are `six-max-history-physical-derived-policy/v1`, `six-max-history-physical-conditional-refinement/v1` and `six-max-history-physical-decision-stability/v1`. Bindings include the original source/spot, rank table, exact history-specific table, complete game identity, predecessor checkpoint/report and frozen preflop policy. Old suit-model schemas, hashes and saved evidence remain separate and replay unchanged.

## Selection and saved candidates

Both saved runs alternate between the largest local gap and largest joint history/board reach, removing duplicate candidates in a deterministic menu/observation order. They require history reach at least 0.0001 and at least two combos with conditional mass at least 5% for each active player. They examine at most 64 physical cases using fresh 500/1,000-iteration budgets and a 0.001bb selected-case target.

`ALL_MATERIAL` includes weak primary cases. `PRIMARY_ACCURATE` explicitly limits selection to cases whose original local gap is at most 0.001bb. This second selection does not repair, certify or count the excluded weak cases. The full parent audit still includes those cases and every aggregate observation.

| Measurement | All-material candidate | Primary-accurate candidate |
| --- | ---: | ---: |
| Selected physical cases | 64 | 64 |
| Fresh local trials | 78 | 64 |
| Selected cases still above 0.001bb | 14 | 0 |
| Candidate rows replaced / preserved | 296 / 69,691 | 344 / 69,643 |
| Full parent NashConv before | 0.00136993463bb | 0.00136993463bb |
| Full parent NashConv after | 0.00136965639bb | 0.00136982526bb |
| Reach-weighted local NashConv after | 0.00022832793bb | 0.00022849669bb |
| Largest conditional gap after, including aggregates | 2.69278728bb | 2.69278728bb |
| Local training node visits | 6,326,000 | 4,372,000 |
| Research refinement gate | Rejected | Accepted |
| Derived policy exported | No | Yes |

The broad candidate illustrates why a better parent score is insufficient: fourteen selected local cases still fail. The accepted derivative improves 43 cases and preserves 21 selected cases where the fresh trial does not improve on the predecessor. Each of its 64 trials stops at 500 iterations; that does not make the resulting policy a fresh 1,000-iteration joint checkpoint. It remains a 500-iteration predecessor with explicitly recorded local replacements.

Saved files share `docs/data/sixmax-staged-history-physical-`:

- `all-64-refinement.json.gz`: rejected diagnostic report; there is deliberately no `all-64-policy.json.gz`.
- `accurate-64-policy.json.gz` and `accurate-64-refinement.json.gz`: accepted derived policy and full before/after diagnostics.
- `accurate-64-decisions.json.gz`: exact fresh-budget/own-card decision screen.

Accepted solution hash: `bf7874163c7044a47f62b1b87a99a525225b96c4e5f269d86508f8d161efd75d`. All 69,987 information sets remain supported, including all 9,161 preflop rows. Study and derivative reports remain bounded to 32MiB decompressed, policy to 64MiB; the existing one-million-state, 2,000-observation and 1,000-iteration caps are unchanged.

## Per-player decision stability

The new-model screen reuses the validated derivative and examines the 32 eligible physical cases with largest joint reach. Eligible primary local gaps must be at most 0.001bb, with the same history and two-combo materiality gates. For each case it independently solves fresh 500- and 1,000-iteration references from the same full joint posterior.

Each question conditions on the acting player's own physical cards and the public flop actions. Action EV permits optimal hero continuation against the fixed opponent policy. Reference action values are transferred onto the **primary question's posterior**, so strategy differences are compared on the same hidden-world distribution. A separate total-variation check compares the reference policy's reached posterior after that public action sequence. It detects changed inference rather than hiding it inside the EV comparison.

All primary material questions need prefix reach at least 1% and own-hand conditional mass at least 5%. Both active seats must be represented and pass. The existing gates remain: reference local NashConv at most 0.001bb, maximum action-EV drift at most 0.01bb, both cross-policy mixture regrets at most 0.01bb, and private-posterior total variation at most 0.01. Zero reference reach fails. Action-frequency differences are recorded separately; interchangeable low-regret mixes do not by themselves fail a question.

The saved screen finds:

- 510 eligible physical cases; 20 remain above the primary local accuracy gate.
- 32 screened cases, of which 28 pass every material question.
- 151 material own-hand decisions, of which 143 pass.
- Eight action-EV failures across four cases; no private-posterior failures. Maximum observed EV drift is 0.49563bb, while maximum posterior distance is about 0.00001272.
- Retained whole-game reach of 0.00087840219, divided by **all** heads-up reach of 0.66636517012: **0.131820%** retained heads-up coverage.

The other 478 eligible cases are unexamined by this bounded screen and receive no retained credit. The 28 passing cases are evidence about this explicit sample, not the whole 530-board menu or full deck. The unchanged retained-coverage target is 25%, and the menu still concentrates on BB versus a CO open. Root local accuracy can coexist with unstable values of inferior/off-path actions; an EV failure should not automatically be described as preferred-action disagreement or a library defect.

## Reproduction and independent controls

Using Java 21 and Maven from the repository root:

```powershell
mvn -q -pl solver -am install -DskipTests
$env:MAVEN_OPTS = '-Xmx2g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalConditionalRefinementMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-all-64-policy.json.gz docs/data/sixmax-staged-history-physical-all-64-refinement.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxHistoryPhysicalDecisionStabilityMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-history-physical-payoffs.json.gz docs/data/sixmax-staged-history-physical-policy-500.json.gz docs/data/sixmax-staged-history-physical-study-500.json.gz docs/data/sixmax-staged-history-physical-accurate-64-policy.json.gz docs/data/sixmax-staged-history-physical-accurate-64-refinement.json.gz docs/data/sixmax-staged-history-physical-accurate-64-decisions.json.gz'
mvn -q -pl solver -am test '-Dtest=SixMaxHistoryPhysicalRefinementTest,SixMaxHistoryPhysicalRefinementArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Generation uses `refine` with seven distinct paths, then `<maximum-cases> <fresh-budgets-csv> <target-gap-bb> <ALL_MATERIAL|PRIMARY_ACCURATE>`. Both derivative output paths must be new. Screening uses `screen` with the eight paths shown above and a new screen output. Each CLI validates normalized/hard-linked aliases before loading inputs, exactly regenerates all literal payoffs, replays the complete predecessor study and reproduces every derivative trial. Screen replay also reproduces every reference, posterior, EV and coverage field. It never overwrites an accepted predecessor with a rejected candidate.

Synthetic all-tie tests isolate deliberate fold mistakes, policy repair, complete posterior support, frozen preflop/unselected rows, no-export rejection, schema/lineage/trial tampering and file safety. Saved-data tests replay both candidates and the decision screen, enumerate independent pure own-information-set plans for five selected cases before/after, and verify every screened primary action EV with separate closed-form pot/share accounting. Existing suit refinement and decision evidence replay through the shared engine, controlling backward compatibility.

## Next solver work

The current literal-board capacity itself rules out the unchanged 25% retained target. Each hidden world removes twelve cards, leaving `C(40,3) = 9,880` equiprobable flops. A particular physical board has probability at most `1/9,880` after any preflop history, averaged over any private posterior. For `R` total history/board revelations, summing their reach gives at most `R/9,880` of all heads-up reach, because each selected history's probability is at most the all-heads-up probability. At the 600-revelation cap, this optimistic ceiling is **6.072874%**, even before blocked cards, diversity, solver quality or stability remove cases. The 530-board menu's corresponding ceiling is 5.364372%, above its measured 2.495162% material reach. More iterations or a wider stability sweep within this capacity cannot reach 25%.

The menu selector now rejects requests above that capacity before frozen-policy checks or expensive board enumeration. It also rejects serialized selection evidence whose claimed reach exceeds its menu's own optimistic bound. This is a one-sided impossibility bound, not a promise that requests below it are feasible. Meeting the current target needs a declared broader observation/storage/runtime design and performance evidence; raising a compute cap or lowering the content gate silently would conceal the problem.

In parallel, address the fourteen weak selected cases within a declared local-solver design/budget, then investigate the four accurate-but-EV-unstable cases without weakening the decision gates. A broader stability sweep must report work cost and actual retained reach rather than crediting unexamined boards. Expanding history/seat-pair coverage, realistic private ranges, rake, multiway postflop and richer later-street betting require their own model and validation changes. These results do not qualify a new trainer pack. Public AWS deployment remains paused.
