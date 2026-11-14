# Root-envelope policy qualification

## Declaration before generation

Attempt one formal qualification of the existing BB99 root envelope, keeping
fold/call/raise to 9 bb, the original joint prior, physical model, pure-policy
selection and every numerical limit unchanged. No new hand, board, menu, history,
policy-selection rule or tolerance is searched. Conservative outer bounds remain
distinct from attained exact endpoint claims. A complete source alone grants no
qualification.

Reserve one physical game, one shared parent and two fresh 80k/160k references
(240k total iterations), plus one envelope parent: two parent calls/four parent
LPs and five envelope LPs. Those five share the original 10,000-pivot/four-billion
arithmetic limit; compilation retains eight million units and both report byte
caps remain 8 MiB. One generation, one local complete validation replay and one
replay per CI run are reserved. No numerical retry, fallback or larger cap is
permitted. Every old report, mode, qualification gate and trainer remains intact.

Assessment performs no LP optimization, hand evaluation or CFR training. Reuse
the owned game's validated immutable snapshot and the source's three complete
policies. Check exact source/game/menu lineage; complete normalized finite policy
rows; full-parent quality; membership in the original opponent security face;
actual and counterfactual root reach; every forced optimal-continuation value and
loss against its conservative bounds; and the selected decision's regret.

The question precedes all player actions. Its reach is original chance mass,
not selected-flop probability or policy reach at a later street. The reached
public continuation is the whole original parent, with public-root reach one;
the local public-game quality check therefore uses the same full-parent report.
Conditioning a two-player Nash-gap test on hero's private 99 would change the
opponent's beliefs and is not the reached public game. Do not reuse the flop-only
qualifier or expose the selected future flop as a visible preflop board.

Retain the original .001 bb full/public quality limit, .0001 reach floor, .01 bb
decision-regret and maximum-loss limits, 1e-8 security slack and certificate
tolerance. Loss lower bound is max(0, max move lower EV minus selected upper EV);
loss upper bound is max(0, max move upper EV minus selected lower EV). No joint
attainment of these conservative endpoints is assumed. Require at least one
robust within-limit move and one robust outside-limit move. Ambiguous moves stay
unresolved under the existing STRATEGY_DEPENDENT classification.

Only an owned complete envelope Result may enter qualification. Its privately
constructed qualified capability binds the canonical full report hash. Reading
or constructing a report, caller-selected hash or passing assessment DTO cannot
issue that capability. Incomplete numerical sources retain their failure and
cannot qualify. Public trainer admission remains false; this milestone adds no
loader, API or UI.

Before generation, analytic controls cover feasible and outside-face opponents,
bad root decisions, original hidden-world root reach, public/private conditioning,
changed games, invalid policy coverage/probabilities, mismatched menus/bounds,
ownership and robust contrast requirements. Source validation reuses one complete
physical replay for the primary and both references. Its predetermined negative
controls force hero's root fold and replace all opponent rows by uniform legal
probabilities; these perform assessment only. Retain their outcomes without
searching for another negative fixture. Independent Python checks reconstruct
every policy assessment, action value/loss, classification and rejection, with
no optimization or training.

## Declared result

The single attempt completed in about 80 seconds and qualified for offline drill
use. All three policies passed, including both fresh references; no rejection
reason was removed or numerical limit changed. Conservative terminal-profit and
loss bounds are:

| Move | Terminal profit (bb) | Loss (bb) | Classification |
| --- | --- | --- | --- |
| Fold | [-1.000000010, -0.999999990] | [1.785779180, 1.785779293] | Outside limit |
| Call | [0.785779190, 0.785779283] | [0, 0.000000093] | Within limit |
| Raise to 9 bb | [-6.359434212, 0.785779283] | [0, 7.145213495] | Unresolved |

The maximum loss is .01 bb. Add BB's committed 1 bb to both terminal-profit
endpoints to display current-decision EV; losses do not change. The raise bounds
remain wide and need not be attained. The qualification does not replace them
with scalar values or establish a unique best action.

Primary/reference selected regrets were approximately 5.6e-15, 8.3e-10 and
2.1e-10 bb. Each policy used original root mass 1/3 and passed full/public quality,
face inclusion and all three action-value/loss checks. Assessment performed no
additional LP or CFR work. The embedded PR208 source reproduces exactly: five
LPs, 153 pivots, 471,304,900 arithmetic units and 1,120,512 compiler units. The
new report is 69,188 bytes stored and 922,823 bytes expanded, below both caps.

Reach is measured within the supplied-prior game at its fixed public-history
prefix. The probability of reaching that prefix in an entire hand remains
unavailable, as recorded in the unchanged source binding. This is still two
active players at six seats with supplied joint ranges, rather than a general
six-player equilibrium or whole-hand reach claim.

## Validation and use

`RootEnvelopePolicyChecks` performs bounded snapshot assessment only.
`SixMaxRootEnvelopeFeedback` exclusively accepts an owned envelope Result at its
qualification boundary. A qualified result exposes an opaque report-hash
capability; parsing the same report never creates one. Incomplete source reports
retain their typed failure and reject qualification. Every prior solver mode and
data artifact remains unchanged.

The independent Python checker reconstructs the complete original envelope and
five certificates, all three policy assessments, nine forced continuation values
and nine losses, every move classification and the complete rejection list.
Canonical Java hashes and full-report equality are checked by the owned physical
replay. Analytic controls and corruption tests cover forged quality, private-hand
conditioning, altered chance mass/security, missing moves/references, false
values/losses/regret and attempts to relabel qualification as public admission.

```powershell
mvn -pl solver -am test "-Dtest=RootEnvelopePolicyChecksTest,RootEnvelopeFeedbackTest" "-Dsurefire.failIfNoSpecifiedTests=false"
python scripts/check-root-envelope-feedback.py
python -m unittest discover -s scripts -p test_check_root_envelope_feedback.py
```

`com.pokerlab.solver.SixMaxRootEnvelopeFeedbackMain` accepts
`solve <request> <new-report.json.gz>` or `replay <request> <existing-report.json.gz>`.
Outputs cannot overwrite files or alias inputs. The request, report and receipt
are `docs/data/supplied-root-envelope-feedback-{request.json,report.json.gz,independent-check.json}`.

The next milestone is a pinned owned loader/cache with all three cached grades
and canonical reviews, followed by a separate default-disabled preflop API. A
later table UI must show no flop at this preflop decision and must preserve the
raise's unresolved classification. This release adds neither API nor UI routes.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`cc53f13ce75c053c298fe32c5a0d3fb67663cd2d` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared cc53f13”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
