# Connected flop action bounds

PokerLab can now compute complete-parent numerical security-face bounds for the initial flop decisions in its supplied two-player game. The saved case contains three BB hands, each with check and bet actions, on `2s 8s Ts` after calling the BTN open. These are research results; `trainerAdmission=false` remains explicit. They do not describe a general six-player GTO strategy.

The [report](data/supplied-four-button-hand-flop-action-bounds.json.gz) embeds the unchanged [connected physical parent](supplied-connected-flop-betting.md). Original private chance, all twelve dealt cards, residual flops, preflop actions and payoffs remain in that parent. Every action endpoint constrains the opponent over the complete approximate global security face, with `1e-8 bb` security slack. No candidate-policy posterior or positive-reach subset replaces that face.

## Reach and utility units

The physical flop event has prior mass `E = sum(originalJointProbability * physicalFlopProbability)`. It is `1/9880` for the saved all-compatible support. Blocked deals contribute zero to this event while remaining in the parent game. Physical incidence is reported for every original deal.

Question reach fixes and cancels the hero's common perfect-recall past. Opponent action likelihoods remain. Numerator and denominator measurement coefficients are both divided by the same owned event mass `E`; conditional utility is unchanged. The original parent payoff matrix and global security constraints are not divided by `E`.

The declared `0.0001` floor is counterfactual question reach **given the physical flop**. Its raw equivalent is `E * 0.0001`, and both units are saved. Each initial BB-hand question has conditional reach `1/3` and raw reach `1/(3*9880)`. Feeding that raw reach into the older compiler's `0.0001` raw floor would reject a supported question. Changing the parent chance to avoid that rejection would change the game. The new path has a separate algorithm identity and obtains `E` from the owned game; there is no public arbitrary-scale parameter.

Action utility is absolute terminal chip profit in big blinds. The numerical envelope uses original endpoint **dual** objectives, padded by the existing `1e-8` certificate tolerance. For an action `a`, the conservative loss envelope is:

```text
lowerLoss(a) = max(0, max_b lowerUtility(b) - upperUtility(a))
upperLoss(a) = max(0, max_b upperUtility(b) - lowerUtility(a))
```

Different endpoints can use different opponent strategies. This is a conservative envelope across correlated possibilities, not an exact joint-regret optimization or a strategy frequency. In the saved case, betting `7d 7h` has a lower loss of approximately `0.09178 bb`. The other hands retain wider or overlapping action bounds instead of receiving unsupported precise grades.

## Numerical solver change

The default LP and ascending-row equilibrium algorithms remain unchanged. A new explicitly selected `BoundedLinearProgram.solveResolvableCosts` mode applies to the action-bound LPs. Phase I retains its existing rule. In phase II, a positive reduced cost above the existing computed roundoff estimate can enter even below the old `1e-10` stable-entry threshold. Ratio comparison uses the smaller computed double ratio; only an equal computed ratio uses the original Bland index tie rule. It does not choose a larger step because the ratios are approximately equal.

Both changes were needed on the saved endpoints. Applying either rule globally disrupted the already-working equilibrium solve; merely allowing small costs also exhausted work on some endpoints. The shipped mode is selected before optimization, never used as a retry. It keeps original coefficient, constraint, pivot and arithmetic caps and the original certificate tolerance. Its duals are mapped back from sorted rows and its primal/dual certificate is recomputed on the original LP. A failure produces no owned result handle.

Controls cover all 292 independent oracle LPs in four row/scaling representations, the positive optimum of a tiny bounded objective, tiny and cancellation-created unbounded objectives, an approximately equal ratio where the actual smaller step matters, original row duals, immutable ownership and work limits. Existing default fail-closed behavior stays covered.

## Physical and independent verification

The [stdlib checker](../scripts/check-supplied-flop-action-bounds.py) reuses the physical card/tree checker against the byte-pinned parent. It independently rebuilds sequence-flow projections, reduces the literal physical payoff matrix, compiles every continuation numerator and every original endpoint LP, and checks all 27 original primal/dual certificates. It independently reconstructs each opponent behavior, verifies whole-parent best-response security and recomputes the action-conditioned response and posterior.

Two fresh exhaustive CFR+ policies use 2,000 and 4,000 iterations, with full-parent and reached-flop quality reported. Their measured whole-parent gaps are approximately `5.19e-6` and `1.30e-6 bb`. These are separate runs, not copies of the owned LP candidate. The checker independently assesses their full-parent and reached-flop quality.

The [receipt](data/supplied-four-button-hand-flop-action-bounds-independent-check.json) binds both checker source files and the exact request, parent and stored/expanded report bytes. The checker uses byte-pinned previously replayed full preflop counts, regenerates selected-flop cards, and runs no optimizer. Java replay regenerates the complete physical source, all owned solves and both fresh policies, then compares the entire report. Java replay also checks lineage hashes that the Python checker does not independently reproduce.

```text
com.pokerlab.solver.SixMaxSuppliedFlopActionBoundsMain solve <connected-request.json[.gz]> <new-bounds.json[.gz]>
com.pokerlab.solver.SixMaxSuppliedFlopActionBoundsMain replay <connected-request.json[.gz]> <bounds.json[.gz]>
```

```sh
python scripts/check-supplied-flop-action-bounds.py docs/data/supplied-four-button-hand-connected-flop-request.json docs/data/supplied-four-button-hand-flop-action-bounds.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_supplied_flop_action_bounds.py'
```

Stored and expanded reports retain the 8MiB cap and exclusive output creation. Corruption controls cover changed physical event/floor units, priors, matrices, numerators, duals, original flows, behavioral responses, posteriors, loss envelopes, reference quality and admission.

Next: make qualification criteria for these bounds explicit, test unsupported later histories and blocked physical support, and only then publish a small replayable flop question pack with API grading. Broader flop coverage, turn/river betting, rake and genuinely multiplayer strategy remain separate work. AWS deployment remains paused.
