# ADR-004: First preflop solver validation game

**Status:** Accepted for solver validation, 23 September 2026. This is not a published training solution.

## Context

The equity simulator estimates the outcome of known hole cards. A GTO trainer needs a complete decision game and a measured solution. Unrestricted 100bb six-player no-limit Hold'em is too large for the first correctness milestone. We need a small preflop game whose chance distribution, legal actions and chip flows can be inspected independently.

## Decision

Use a **no-rake, 100bb, six-seat cash table** as the first validation context. Blinds are 0.5bb/1bb, with no antes. The action history is:

1. SB posts 0.5bb; BB posts 1bb.
2. UTG opens to 3bb; HJ and CO fold.
3. BTN raises to 10bb; SB and BB fold.
4. UTG raises to 22bb; BTN raises to 40bb.
5. UTG may **shove to 100bb or fold**. If UTG shoves, BTN may **call to 100bb or fold**. No other action is modelled.

The two live players are UTG and BTN. Their committed amounts are 22bb and 40bb. The folded blinds contribute 1.5bb of dead money. The initial ranges in `ValidationSpot` contain only three weighted physical card combinations per live seat. They are synthetic test inputs, not a population model or a recommendation for real play.

| Terminal action | UTG net chip profit |
| --- | ---: |
| UTG folds | −22bb |
| UTG shoves; BTN folds | 41.5bb |
| UTG shoves; BTN calls | `UTG showdown equity × 201.5bb − 100bb` |

Exact four-card matchups with shared cards are impossible and removed before range weights are normalized. The called all-in payoff uses a preflop showdown-equity oracle. Exhaustive enumeration handles the eight unblocked matchups in this fixture; seeded Monte Carlo is a faster provisional oracle for broader payoff tables. The solver reports a best-response gap **against the resulting payoff tree**. With Monte Carlo inputs, payoff-estimation error must be reported separately before treating a pack as usable training content.

With dead money, the two live players' literal profits sum to 1.5bb at every terminal. CFR's player-one utility is defined as the negative of UTG's profit; this is a constant shift from BTN's literal profit and leaves best responses unchanged. All reported UTG action EVs remain literal net chip profit.

The `PreflopAllInSpot` hash binds the six-seat context, action history, stack, blinds, weighted exact-card ranges and no-rake model to a versioned SHA-256 value. A changed assumption must produce a different hash. The current history validator checks contributions, folds and the final raise, but it is **not** a complete no-limit betting-rule engine. This bounded spot must not be relabelled as an unopened-raise chart, general 100bb preflop strategy, raked cash solution or multiway solution.

The committed [validation pack](../../solver/src/test/resources/validation-pack.json) uses exact enumeration and 3,000 vanilla CFR iterations. Its hash is `f7b669b57c3e212f16dc8e80ee5a270407b04f09c22757e906b637cb3d6630b5`, its best-response gap is **0.011635bb**, and its payoff sampling error is zero. These numbers describe only the small synthetic range fixture and its restricted action tree; `publicationStatus` is `VALIDATION_ONLY`.

The offline builder also supports a CFR+ variant with nonnegative cumulative regrets and linearly weighted average strategies. On the same exact eight-matchup payoff table, 3,000 iterations produce a roughly **0.0000078bb** best-response gap. The committed vanilla fixture remains unchanged for reproducibility. This comparison is specific to the restricted synthetic game; larger ranges and runtime/memory measurements are needed before choosing a production solver configuration.

## Consequences and next checks

- Keep solution generation offline; page requests must never run CFR.
- Benchmark seeded equity-estimation error over broader payoff tables; the first eight-matchup fixture now uses exact enumeration.
- Define publication thresholds and a reviewed content set before exposing a read-only trainer API. The validation-only pack already has structural, payoff and action-EV checks. Its current strategy gives nearly every hero combo the same action, so it is unsuitable as a lesson.
- Solve non-all-in preflop decisions only after their postflop continuation model and rake treatment are validated.
