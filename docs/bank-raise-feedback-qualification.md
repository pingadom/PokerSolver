# Two hand raise minimum feedback qualification

The new backend qualifies complete fold/call/raise menus for the original BB77
and AcJc roots using the owned sequence-minimum study merged through PR #235.
Only the raise lower bounds change. Fold/call bounds and every original full-action
upper bound remain intact. All original whole-face, public-parent quality, actual
and counterfactual reach, decision-regret and educational checks are retained.

## Results

These are absolute terminal-profit values in BB, before adding the posted blind
for current-decision display. The displayed bounds are rounded outwards; exact
binary64 values remain in the report.

| Hand | Qualified raise EV outer bounds | Earlier raise loss upper bound | New raise loss bounds |
| --- | --- | ---: | --- |
| 7d 7h | [-0.4222958066, 0.6855082472] | 7.0449424594 | [0, 1.1078040537] |
| Ac Jc | [-3.4421594057, 0.1531739219] | 6.5126081341 | [0, 3.5953333275] |

Both complete menus retain **fold outside limit, call within limit, raise strategy
dependent** at the unchanged 0.01 BB threshold. The raise is neither a proven safe
move nor a proven mistake. The new loss bounds improve the explanation; they do
not turn a minimum upper bracket into a full action ceiling or establish action
frequencies. The existing website/API grades remain unchanged in this milestone.

For each action, the new lower bound is the maximum of the original conservative
floor and the sequence minimum lower bound when one exists. Only raise has that
additional input. Loss bounds conservatively compare all three action bounds:
the lower loss is max(0, other lower minus selected upper), and the upper loss is
max(0, other upper minus selected lower). These are complete-menu outer bounds,
not independently attained loss endpoints or a new joint-regret optimisation.

## Reference checks and ownership

Each hand retains the original primary policy and both fresh 80k/160k references.
All six checks verify the matching hand's minimum support and twelve utility/loss
inequalities: 72 inequalities in total. A reference outside the declared whole
face is explicitly rejected without claiming bound checks. Unsupported minima,
changed policy identity, bound violations and original quality/reach failures
remain qualification failures. Both declared hands must pass the aggregate.

The original minimum report is pinned by stored gzip SHA-256
`e01215a5da6ee3ffe3ea4e427c6777ad211881bdbd7ef970c3abb1a9d74df034`
and canonical typed identity
`73e4fcde12e37749fa11278c143830f72f16378802affcd9fa5980e26fc8a8be`.
The request binds its complete original request and the unchanged loss threshold.
Stored/canonical identity and complete reference-supported status are checked
before qualification. Captured path, file key, timestamp and bytes are rechecked
after replay. The nested minimum producer separately owns and checks the bank.

`solve` fully replays the owned physical minimum study and compares the complete
report before deriving feedback. `Result` and `QualifiedFeedback` constructors
are private; a report DTO or a true JSON qualification flag cannot mint a handle.
The handle binds both the feedback report hash and source minimum hash. Public
`trainerAdmission` remains false. Offline qualification is not public publication.

One declared generation and one owned replay suite ran locally. Each rebuilt one
physical game and the original primary plus two fresh references totalling 240,000
reference iterations. Each uses two original parent LPs, two batch-parent LPs and
two endpoint LPs; the qualification layer itself solves no new LP. Endpoint caps
remain 8,000,000 cumulative compiler units, 10,000 pivots and 4,000,000,000 arithmetic
operations. There was no numerical retry, source overwrite or increased tolerance.

## Reproduce and verify

Use Java 21 and the project's solver/engine runtime classpath:

```text
com.pokerlab.solver.SixMaxRootHandBankRaiseFeedbackMain solve docs/data/supplied-bank-raise-feedback-request.json docs/data/supplied-root-hand-bank-report.json.gz docs/data/supplied-bank-raise-sequence-report.json.gz NEW-feedback.json.gz
com.pokerlab.solver.SixMaxRootHandBankRaiseFeedbackMain replay docs/data/supplied-bank-raise-feedback-request.json docs/data/supplied-root-hand-bank-report.json.gz docs/data/supplied-bank-raise-sequence-report.json.gz docs/data/supplied-bank-raise-feedback-report.json.gz
python scripts/check-bank-raise-feedback.py
python -m unittest discover -s scripts -p test_check_bank_raise_feedback.py
```

Inputs and output must be distinct. The CLI refuses an existing output before
solving; a failed solve can leave an empty reserved file. Strict request input is
capped at 128 KiB and stored/expanded reports at 8 MiB. The independent checker
first verifies the original source physics, certificates and grouped best
responses with the unchanged pinned minimum checker, then derives all six moves,
six policy assessments, 72 inequalities and complete previous-menu comparisons.
It runs no optimiser or CFR and applies no frontend EV shift.

All 18 Java tests passed: one complete owned replay and 17 controls. All 16
independent tests and 14 backend CI evidence controls passed. Coverage includes source/model/hand substitution,
minimum-upper misuse, false support or qualification counts, original gate
failures, stronger old floors, request coercions/duplicates and bounded file IO.
Full solver/engine compilation and Spotless passed. The original 430 evidence
files, kernels, prior tests/assignments, dependencies, API and frontend are unchanged.
CI retains all 30 jobs and adds the new independent checker/test commands.
The two new Java classes are discovered automatically; the new owned replay is
placed on solver-0 to leave room in the previously longest solver-5 job. All
380 earlier class assignments remain unchanged. Full PR CI follows clearance.

## Scope and next boundary

This remains a supplied-range, fixed-prefix, two-active-player game at six seats
with folded-card blockers and selected-flop continuation. It does not establish
general six-player GTO, whole-hand frequencies, full action maximum endpoints or
coherent complete-hand play. Next add a separately pinned cache/API that accepts
only the owned qualified result, replays at startup and projects public context
without hidden cards or future board information. Existing sources keep their
identities and grades. AWS deployment remains paused.

## Review status

This new qualification revision awaits explicit owner clearance. The latest
confirmation below covers only the earlier minimum solver code, not this later
qualification code.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`a874053cbda0b74cb80ec45ef01ad50ee3034f84` and cleared PR creation on
2026-11-12 (Europe/London), with “cleared a874053”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
