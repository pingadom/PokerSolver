# Six seat payoff work budgets and execution

This proposed backend step connects a complete six-seat range preflight to explicit
physical payoff work. It plans the cost before creating an oracle, requires an
exact or sampled board model, and can execute that complete plan into an owned
private payoff table. This later revision awaits owner review. The historical
confirmation below applies only to its named earlier code revision.

## Work that is counted

All six private hands remove twelve cards from the deck, including folded seats.
An exact preflop payoff needs choose(40,5), or 658,008 full boards per legal joint
world. Each board evaluates six seven-card hands. Those scores are already shared
across all 57 active-seat subsets containing two or more players by the existing
oracle. The board count must not be multiplied by 57.

For the existing twelve-world correlated example, complete exact payoff work is
7,896,096 boards, 47,376,576 hand evaluations and 684 stored subset estimates.
A shared-board Monte Carlo configuration with eight trials per world instead
requires 96 board draws and 576 hand evaluations, with the same 684 estimates.
These are two distinct payoff models. An exact joint private prior does not make
sampled board payoffs exact.

`SixSeatPayoffWork.plan(input, configuration, budget)` accepts only an owned complete
range-preflight result. It performs no board evaluation or strategy solving.
Budgets may only lower hard limits of ten million boards and sixty million hand
evaluations per execution. Both complete costs must fit. Over-budget requests throw
`BudgetExceeded` with required work and supplied budgets; they never switch to
sampling, trim worlds, reduce trials or return a partial plan. Existing 64-world,
prefix-search and game/public-state caps remain unchanged.

Configuration explicitly chooses exact enumeration or shared-board Monte Carlo.
Exact requires zero sampled trials and zero seed. Sampling requires a positive
trial count and a signed 64-bit seed. Counts use checked long arithmetic before
budget comparison, including huge sampled trial requests. The plan identity binds
the full range-input hash, payoff method, trials and seed under a versioned scope.
Successful lower budgets do not change the planned model or identity. Betting
rules, strategy policies and trainer authority are outside this identity.

## Owned physical execution

`Plan.execute()` creates its own oracle and fixed complete query order. It visits
every legal world and all 57 active-seat subsets. Exact mode uses the existing
one-deal exact cache. Sampled mode owns a bounded shared-board cache, sized to the
complete supported world count. No external oracle or mutable cache is injected.
Every estimate must have six finite shares, conserved pot mass, nonnegative finite
standard errors, zero shares/errors for inactive seats and the requested trial
count. Results are privately constructed and immutable.

Execution reconciles the row count and per-world estimate trial counts with the
plan. Sampled mode additionally compares the existing oracle's actual
`boardsEvaluated` counter with planned board work. The result reports these as
separate values: `boardsFromPerDealEstimates` and `observedSharedBoards`. The latter
is null for exact mode; an observed exact board counter is not invented.

`Result.oracle()` serves the owned physical payoff table without more board work.
It rejects unknown ordered hands and active subsets. Physical shares do not depend
on input frequencies, so lookup can reuse identical cards under different weights;
a new input's model identity and strategy must still be recomputed. Payoff rows
and returned share/error arrays are defensive. There is no JSON loader that can
manufacture an owned result or grading authority from a report.

Each `execute()` invocation gets a fresh oracle and its own budget. Repeating a
plan is allowed and repeats its work; the budget is not a global or cumulative CPU
quota. This does not account for later CFR, LP, game-tree construction, persistence
or wall-clock time. Unsupported declared range hands remain visible through the
original preflight; its research-spot bridge still refuses those inputs.

## Private CLI

Use the existing engine/solver runtime classpath. Planning is explicit:

```text
com.pokerlab.solver.SixSeatPayoffWorkMain plan NEW-plan.json exact 658008 3948048 AcAd KcKd QcQd JcJd TcTd 9c9d
```

Evaluation must be requested separately:

```text
com.pokerlab.solver.SixSeatPayoffWorkMain evaluate NEW-payoffs.json shared:8:711 8 48 AcAd KcKd QcQd JcJd TcTd 9c9d
```

The last six arguments are UTG, HJ, CO, BTN, SB and BB range expressions. Budget
arguments are maximum board and hand evaluations. `exact` never takes sampled
trials/seed; `shared:TRIALS:SEED` makes them explicit. Plan-only mode emits no payoff
rows. Evaluation mode includes all completed rows and work reconciliation.

The `pokerlab-six-seat-payoff-work/v1` JSON contains full private ranges/worlds,
original support, input/plan identities, configuration, budgets and work. Seeds
are decimal strings so JavaScript cannot round signed 64-bit values. Admission
and grading authority are always false. All concealed cards make this research
output private; never send it as a public trainer question.

Existing files, hard links and existing symlink paths reject before evaluation.
Planning/validation and final serialization finish before CREATE_NEW publishes a
new file. A concurrent creator can still win that final race, in which case its
file is preserved although evaluation work may have happened. A filesystem failure
can leave an incomplete new file. The output directory must already exist; no
transactional publication guarantee is claimed.

## Completed controls and remaining qualification

All seventeen new Java cases pass: eleven payoff controls and six CLI controls.
All forty-one retained range/preflight cases also pass, for 58 selected cases.
One complete six-player exact execution values 658,008 physical boards and all 57
subsets. Its first two seats agree with a separate dead-card heads-up enumerator
which visits another 658,008 boards while keeping every dealt card removed.
This is new physical payoff evidence for one private world, not a solved strategy.

The final selected suite also evaluates 114 sampled boards across its controls.
The twelve-world sampled table has all 684 rows, exactly 96 observed board draws,
and feeds the unchanged research game without further board evaluation. Independent
CLI checks use Python's choose(40,5) and verify complete subset inventory, work,
finite errors, pot conservation and folded-seat zero shares in a four-board capture.
These checks do not certify sampled payoff accuracy.

Spotless, compilation and packaging passed. Existing Java sources, dependencies,
441 data files, 28 solver resources, 400 CI class assignments and workflow limits
remain unchanged. No new CFR or LP solving runs. Full PR CI follows owner clearance.
Exact enumeration rows use binary64 shares. Sample standard errors are empirical
diagnostics, can be zero for small samples and are not confidence or convergence
certificates. Reviewed source assumptions, payoff accuracy, strategy solving and
independent qualification still precede any trainer admission. No website lesson
or public HTTP response changes. AWS deployment stays paused.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`1dfc54ab65f422fc658aa53311f1b18cea8e5c21` and cleared PR creation on
2026-11-18 (Europe/London), with “I reviewed 1dfc54a — clear it”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
