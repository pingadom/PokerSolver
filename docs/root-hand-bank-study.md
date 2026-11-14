# Three-hand preflop root bank

The backend now solves all three BB private hands in the supplied joint game in
one owned run: `7d 7h`, `9d 9h` and `Ac Jc`. Every declared hand retains its own
context, envelope or typed numerical failure, three-action menu, and primary plus
80,000/160,000-iteration policy checks. The parent game and both fresh reference
policies are computed once and shared. The sevens and nines outcomes match their
existing standalone evidence exactly.

This is a research source for a future mixed-hand trainer. There is no new HTTP
route, cache, browser fallback or admission capability. Two players remain active
at a six-seat table. The supplied ranges, folded-card blockers, selected flop and
postflop sizing model remain fixed; this is not a full six-player cash-game solve.

## Results and interpretation

The original public history is UTG/HJ/CO fold, BTN raises to 3 BB, SB folds, BB
acts. The pot is 4.5 BB; call adds 2 BB and raise-to-9 adds 8 BB. Each hand has
eight original joint worlds and chance mass 1/3. Public-game quality checks use
the whole original game, not a private-hand-conditioned exploitability claim.

| Private hand | Fold | Call | Raise to 9 BB |
| --- | --- | --- | --- |
| 7d 7h | Outside loss limit | Within loss limit | Strategy dependent |
| 9d 9h | Outside loss limit | Within loss limit | Strategy dependent |
| Ac Jc | Outside loss limit | Within loss limit | Strategy dependent |

All three saved outcomes have complete conservative outer bounds and pass the
feedback checks at a 0.01 BB maximum-loss threshold. A strategy-dependent action
remains unresolved. These are outer bounds for optimal future continuation, not
attained endpoint claims or action frequencies. EV is absolute terminal profit:
fold is approximately -1 BB. This research source does not add the frontend's
posted-blind shift.

## Ownership, failures and work limits

`SixMaxRootHandBankStudy.solve` captures the original parent report, checks its
stored SHA-256 and typed request before physical work, constructs one physical
game, reproduces the complete parent and fresh references, then attempts all
three envelopes. The captured source identity, metadata and bytes are checked
again before returning a privately constructed `Result`. Deserializing a report
does not create an owned result or permission to grade a trainer attempt.

The request fixes the entire ordered hand inventory. Omitting, duplicating or
substituting a hand is rejected. A typed per-hand LP, parent-kernel or envelope
failure retains its slot, failure reason and empty menu, while later hands are
still attempted. A shared physical-parent failure aborts the whole run. Numerical
failure is not evidence of mathematical infeasibility.

The bank reserves one physical game, up to four parent-kernel calls/eight parent
LPs, fifteen envelope LPs and two fresh reference runs totalling 240,000 iterations.
Each of the three envelope attempts keeps the existing 8,000,000 compiler-unit,
10,000-pivot and 4,000,000,000-arithmetic limits. Their cumulative reservation is
24,000,000 units, 30,000 pivots and 12,000,000,000 arithmetic operations. No kernel
tolerance or per-hand budget was increased.

`completedWork` sums only completed envelope audits, explicitly labelled
`COMPLETED_ENVELOPE_AUDITS_ONLY/v1`. Rejected kernels do not return spent-work
audits, so this field cannot measure all attempted work. The reservation still
covers all three attempts. The saved bank uses 15 envelope LPs, 3,361,536 compiler
units, 468 pivots and 1,440,054,300 arithmetic operations. Parent work and reference
iterations are separate from these envelope counters.

## Reproduce and check

Build the solver with Java 21 and the project's Maven configuration. With the
solver and engine classes plus runtime dependencies on the classpath, run:

```text
com.pokerlab.solver.SixMaxRootHandBankMain solve docs/data/supplied-root-hand-bank-request.json docs/data/supplied-flop-size-menu-report.json.gz NEW-report.json.gz
com.pokerlab.solver.SixMaxRootHandBankMain replay docs/data/supplied-root-hand-bank-request.json docs/data/supplied-flop-size-menu-report.json.gz docs/data/supplied-root-hand-bank-report.json.gz
python scripts/check-root-hand-bank.py
python -m unittest discover -s scripts -p 'test_check_root_hand_bank.py'
```

`solve` requires a new output path and refuses aliases, including hard links.
Request input is capped at 128 KiB; stored and expanded gzip reports at 8 MiB.
The strict mapper rejects duplicate/unknown fields and numeric coercions. A failed
CLI solve can leave an empty reserved output file; it cannot be mistaken for a
valid gzip report and the CLI will not overwrite it on a later attempt.

The independent Python checker reconstructs the original physical/action tree
once per bank, checks the original parent and all fifteen envelope certificates,
and recomputes 27 forced-continuation values and 27 losses. It runs neither LP nor
CFR. The receipt pins its own source, seven unchanged helpers, request, report and
parent bytes. Corruption controls cover the three-hand inventory, cross-hand
substitution, partial failures, work counters, policy identity and conservative
bounds. One owned Java replay checks full report equality after recomputing the
physical game and shared references. Parser controls never solve a game.

The existing 29-job CI layout is retained. The two new Java suites are discovered
automatically in solver-0; the new independent checker is an additive solver-3
step. Previous test classes, checker steps and numerical evidence remain intact.

## Review status

The project owner cleared code revision `f7ea2f9ae6c8ee9e8ba2faf1ec9f780aa195733f`
on 2026-11-08 (Europe/London), with “cleared f7ea2f9”. This confirmation covers
that revision only. Full PR CI follows PR creation; Code Review History preserves
this and earlier approvals.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`df4101ea7b0e3b11467bd1d28c2f59c2768e3725` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared df4101e”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
