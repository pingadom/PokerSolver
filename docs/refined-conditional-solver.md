# Paired-board conditional action study

The explicit conditional mode `OWNED_PHYSICAL_FLOP_ASCENDING_REFINED_PARENT_AND_ENDPOINTS/v6`
uses the corrected ascending refined parent solver and the same ascending refined LP representation
for every reach and endpoint solve. The caller selects this mode before any solve begins. Original
v1–v5 methods, report identities and saved evidence keep their behavior. A rejected solve never
triggers a retry with a different representation.

The new `SixMaxRefinedConditionalStudy` binds that choice to a refined-parent request, one owned
public decision, its table context, both legal actions and the complete conditional audits. Each
action receives original-dual utility bounds, conservative loss bounds, counterfactual reach bounds
and all original LP primal/dual witnesses. Only an owned solve or full physical replay creates its
private research result. There is no trainer qualification or admission capability.

## Saved decision

The [request](data/supplied-paired-btn-facing-bet-conditional-request.json) and
[report](data/supplied-paired-btn-facing-bet-conditional-report.json.gz) model BTN holding `As Qs`
on `8d Kc Kd`, after BB calls preflop and bets half pot on the flop. The table shows a 9.75 bb pot
and a 3.25 bb call. All six originally dealt hands, including folded blockers, remain in the physical
model. The full parent retains 24 supplied joint worlds, 1,321 states and 73 information sets.

| Action | Terminal-profit interval (bb) | Loss interval (bb) |
| --- | --- | --- |
| Fold | approximately −3 | 3.16334–3.16406 |
| Call | 0.16334–0.16406 | 0–0.000725 |

These are absolute terminal profits in the declared finite model, including the 3 bb already
committed before this decision. They are not incremental returns on the call or predictions for
unrestricted poker ranges. The conditional reach lower bound exceeds 0.2499 on the complete numerical
opponent security face; no low-reach opponent strategy is discarded to produce the interval.

The study keeps the original 0.0001 conditional reach floor, 1e-8 security slack/certificate tolerance,
8,000,000-unit compiler bound, cumulative 4,000,000,000-unit arithmetic cap and 10,000-pivot cap per
conditional action solve. All four LP solves for an action share that arithmetic and pivot budget.
The two actions require about 105 million and 172 million charged units respectively. Parent solves
retain their separate original bounds. No fresh CFR references are trained in this study.

```text
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain solve <request> <new-report.json[.gz]>
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain replay <request> <report.json[.gz]>
```

Persistence is exclusive with independent 8 MiB stored and expanded limits. Strict JSON parsing
rejects duplicate/unknown fields, wrong mode/schema and trailing input. Replay rebuilds the physical
game, selects the decision from compatible worlds and regenerates every report field.

## Independent verification

```sh
python scripts/check-refined-conditional-study.py docs/data/supplied-paired-btn-facing-bet-conditional-request.json docs/data/supplied-paired-btn-facing-bet-conditional-report.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_refined_conditional_study.py'
```

The checker runs no LP optimizer or CFR training. It first verifies the original paired parent,
including all 666 physical runouts for each world, its tree/payoff projections and both parent LP
certificates. It then reconstructs the complete opponent security face, the literal bet path and
denominator, both action numerators and each transformed endpoint LP. Eight endpoint certificates,
all posterior weights, original realization conservation, behavioral best responses, table stacks,
utilities/losses and cumulative budgets are checked independently. The receipt binds request/report
bytes and checker/helper sources. Semantic corruption controls run before the fixed-byte check.

## Remaining qualification

This supplies one complete research decision, not a new admitted trainer question. Fresh independent
reference runs, their inclusion in the full security face, regret checks and the existing educational
qualification gates remain necessary. Other screened questions retain their recorded zero-reach,
work-limit or numerical rejections. In particular, BTN after checking still has a zero-reach witness;
this mode does not manufacture its conditional posterior. Wider ranges, richer betting streets,
multiplayer equilibrium and AWS deployment remain separate work.
