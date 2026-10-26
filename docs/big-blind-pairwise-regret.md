# BB pairwise regret and fresh policy qualification

This milestone adds a reusable conditional regret calculation, an owned six-context study, a complete BB qualification producer and independent policy checks. It preserves the earlier numerical repair, every source EV interval, all six original marginal-loss outcomes and all existing trainer content.

The result is precise: BB77 and BB99 facing a BTN bet now have robust fold/call separation under the new loss protocol, but fail the existing actual-reach gate. **No new trainer question qualifies.** The producer retains every failure and returns no grading capability for any of these six contexts.

## Why the new loss envelope is tighter

For one fixed opponent policy, let `V_a` be the conditional EV of action `a`, including optimal future hero continuation. Regret is:

```text
regret(a) = max_b(V_b - V_a)
          = max(0, max over b != a of (V_b - V_a))
```

The difference of an action against itself is exactly zero. Given simultaneous marginal EV intervals `[L_a, U_a]` on the same question and security face, conservative bounds are:

```text
lower(a) = max(0, max over b != a of (L_b - U_a))
upper(a) = max(0, max over b != a of (U_b - L_a))
```

Each subtraction is rounded outwards by one binary64 step. This uses no assumption of independent action values, and does not assert that unrelated endpoint witnesses occur at the same policy. Duplicate, invalid or oversized action menus, nonfinite intervals and overflowing differences reject. The arithmetic helper confers no solver or trainer authority.

The prior marginal upper-loss calculation also compared an action's own upper and lower EV endpoints. That remains a valid but looser envelope. The new separately named protocol avoids retroactively changing its results. For BB77 after check/bet, fold EV is -3bb while every certified call EV exceeds -2.655bb; call's pairwise upper regret is zero even though its EV interval has substantial width.

## Owned evidence and explicit qualification

`SixMaxBigBlindPairwiseStudy` accepts an owned BB v2/conditional v10 result and retains all six hand/path contexts. Its report embeds the complete unchanged source plus new moves and candidate indices `[1, 3]`. Reading a JSON report creates no owned result and no grading authority.

`SixMaxBigBlindPairwiseFeedback` uses one physical game, recomputes all six interval contexts, trains two new CFR+ policies at 80,000 and 160,000 iterations, and assesses the primary and both references for every solved context: eighteen assessments in total. No policy is imported for generation, and no rejected context is omitted. A package-scoped coverage overload checks exact game identity to share that one physical build; the original standalone coverage output remains identical.

The BB-specific policy checker follows actual BB/BTN states. BB's prior call and check cancel only from counterfactual reach. They remain in actual full-path reach; BTN's later bet remains in both. It checks the same full-parent and reached-flop gap, actual/counterfactual reach, original-face inclusion, EV/loss coverage and selected-decision regret gates used by existing producers. Those original producer files and thresholds remain unchanged.

Only an owned complete result may create a private `QualifiedQuestion`, and `grade` requires that owned result and a surviving question index. A raw parsed report, an action-separated candidate or a failed reach assessment cannot grade. The CLI's `inspect` explicitly labels an unverified stored snapshot; `replay` regenerates the entire physical study and fresh reference pair before requiring full report equality. Existing paths and input/output aliases reject before optimization or training.

## What the fresh runs found

| Context | New pairwise result | Actual reach at 80k / 160k, given the physical flop | Qualification |
|---|---|---:|---|
| BB77 first to act | Bet within; check dependent | Approximately 1/3 / 1/3 | No robust outside-limit alternative |
| BB77 after check / BTN bet | Call within; fold outside | 4.51e-9 / 1.13e-9 | Insufficient actual reach |
| BB99 first to act | Bet within; check dependent | Approximately 1/3 / 1/3 | No robust outside-limit alternative |
| BB99 after check / BTN bet | Call within; fold outside | 4.22e-9 / 1.06e-9 | Insufficient actual reach |
| AcJc first to act | Bet within; check dependent | Approximately 1/3 / 1/3 | No robust outside-limit alternative |
| AcJc after check / BTN bet | Both dependent | 2.50e-9 / 6.24e-10 | Reach, selected regret and separation failures |

The reach floor remains 1e-4. Later-BB counterfactual reach is approximately .253 in the fresh references, but actual reach is tiny because BB almost never checks. The primary equilibrium reaches none of these later contexts and also fails selected-decision regret at its unreached rows. Counterfactual reach cannot replace actual reach to manufacture an admissible lesson.

This is evidence about the declared source, algorithms and reference pair. It is not a proof that every possible equilibrium or alternative game has zero check reach. Another seed or a relaxed gate is not part of this release. The next model work should jointly screen realized reach and useful action separation under an explicitly declared new bounded game before more content integration.

## Reproduction and validation

The source request is `docs/data/supplied-unpaired-bb-refined-flop-coverage-request.json`. The new qualification request is `docs/data/supplied-unpaired-bb-pairwise-feedback-request.json`. Both mains support:

```text
com.pokerlab.solver.SixMaxBigBlindPairwiseStudyMain solve|replay <BB-v2-request> <report.json.gz>
com.pokerlab.solver.SixMaxBigBlindPairwiseFeedbackMain solve|replay <feedback-request> <report.json.gz>
```

Generation budgets are saved before execution. Pairwise discovery uses one build, six contexts, at most eighteen parent solves/twelve action computations, and no CFR runs. Qualification declares the same source bounds, two fresh runs/240,000 iterations, eighteen assessments and at most 635,040,000 reference node visits. Original compiler, LP, byte and numerical limits remain unchanged. Failed outcomes are retained; there is no automatic retry or fallback.

```sh
python3 scripts/check-big-blind-pairwise-study.py --output <new-receipt.json>
python3 scripts/check-big-blind-pairwise-feedback.py --output <new-receipt.json>
```

The independent study check reconstructs the original physical source and verifies all 51 endpoint certificates, then checks each outward difference using exact fractions. The policy check independently recomputes all eighteen full-game/local quality reports, actual and counterfactual masses, conditional EVs/regrets, face inclusion, bound checks and failure inventories. It verifies deterministic reference policies against the prior same-game control and checks work counts; it does no LP/CFR optimization. Owned Java replay, rather than saved-policy equality alone, establishes fresh training.

Java tests cover all vertices of 21,952 three-action interval boxes (526,848 selected-action checks), exact binary64 subtraction, invalid inputs, complete physical and fresh replay, source identity, omitted outcomes, forged qualification fields, CLI preflight and refusal to grade every rejected context. Python corruption controls change contexts, EVs, bounds, candidates, actual/counterfactual reach, policy regrets, inclusion, budgets and failure lists. CI includes the new checks and the complete original test inventory.

All 323 older data files, existing trainer protocols and the paused AWS deployment remain unchanged. The new evidence is backend research; it does not expose unqualified content on the website.
