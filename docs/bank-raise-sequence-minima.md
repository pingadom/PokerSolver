# Sevens and suited ace jack raise minima

The new backend study computes sequence-form worst-case raise-to-9 values for
the original bank's BB77 and BB AcJc hands. It uses the existing supplied joint
game, all folded-card blockers, original global security face and selected-flop
continuation. It shares one owned batch parent across both questions rather than
enumerating hero continuation plans. Nines already has separate complete-menu
sequence-minimum evidence; that evidence and the current trainer are unchanged.

## Results

Values below are absolute terminal profit in BB, before the frontend adds the
already posted 1 BB. The new narrow bracket encloses the **minimum** optimal
continuation value over the supported opponent face. Its upper endpoint is not
an upper bound on every opponent-specific policy value.

| Hand | Earlier conservative lower bound | New minimum bracket | Lower-bound improvement |
| --- | ---: | --- | ---: |
| 7d 7h | -6.3594342122 | [-0.4222958066, -0.4222957865] | 5.9371384057 BB |
| Ac Jc | -6.3594342122 | [-3.4421594057, -3.4421593856] | 2.9172748066 BB |

Displayed bracket endpoints are rounded outwards. Exact binary64 values and
certificates remain in the report. Both endpoints completed under the original
8,000,000 cumulative compiler-unit, 10,000-pivot and 4,000,000,000 arithmetic caps.
The batch used 4,084,758 compiler units, 146 endpoint pivots and 1,144,710,980
endpoint arithmetic operations, with one batch parent call/two parent LPs and
two endpoint LPs. Original parent compilation/arithmetic and fresh references
are separate counters. Failed kernels do not supply complete spent-work totals.

Six checks compare the original primary and fresh 80k/160k policies against the
appropriate hand's new **lower** bound. All pass. A policy can exceed the upper
endpoint of a minimum bracket; that is expected and is tested explicitly.

## Ownership and bounded work

The source bank is pinned by stored gzip SHA-256
`eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd`
and canonical typed study identity
`e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d`.
Strict loading checks bounded regular files and the stored bytes before physical
work. The study rebuilds one complete physical game, parent report and both fresh
reference policies, compares them with the original bank, then recomputes the
original whole-face policy assessments used by its new lower-bound checks.
Captured path/file identity, timestamp and bytes are checked again before return.

The new request fixes the two ordered hands, action and source identities. One
shared-parent batch reserves its original cumulative limits; limits are never
reset per hand. A typed numerical or structural failure is retained as a rejected
batch with empty comparisons and policy support. It cannot become partial success.
Source-parent failure aborts before publication. Numerical failure does not prove
mathematical infeasibility. Deserializing a report does not construct the private
owned result or grant trainer grading authority.

One declared generation and one owned Java replay suite ran locally. Each rebuilt
one physical parent with two fresh references totalling 240,000 iterations, plus
the original primary policy. No numerical rejection was retried, no saved source
was overwritten and no kernel tolerance, iteration tier or endpoint cap increased.
The initial compile caught a mistaken support-record accessor; it was corrected
before numerical work. The standalone checker initially lacked original Java and
analytic fixture dependencies in its isolated copy; completing that copy required
no numerical solver rerun. Those local setup failures and final results are retained.

## Reproduce and verify

With Java 21 and the project's Maven runtime classpath:

```text
com.pokerlab.solver.SixMaxRootHandBankRaiseMain solve docs/data/supplied-bank-raise-sequence-request.json docs/data/supplied-root-hand-bank-report.json.gz NEW-report.json.gz
com.pokerlab.solver.SixMaxRootHandBankRaiseMain replay docs/data/supplied-bank-raise-sequence-request.json docs/data/supplied-root-hand-bank-report.json.gz docs/data/supplied-bank-raise-sequence-report.json.gz
python scripts/check-bank-raise-sequence.py
python -m unittest discover -s scripts -p test_check_bank_raise_sequence.py
```

The CLI requires distinct inputs/output and refuses an existing output before
solving. Request input is capped at 128 KiB; compressed and expanded reports at
8 MiB. A failed solve may leave an empty reserved output, which cannot be read as
a valid report or overwritten on a later invocation.

The independent checker reconstructs physical payoffs, both original parent
certificates, forced-action domains, both endpoint primal/dual certificates,
original flow realizations and information-set-grouped best responses. It verifies
the same global face against the independently checked earlier physical study.
The original nines-only pure-plan oracle is not reused for different private hands.
Existing helper source bytes remain pinned. The checker runs no LP optimiser or CFR.

All 17 Java tests passed, including complete owned replay and parser/provenance/
budget controls. All 14 independent tests passed, including hand substitution,
changed physical payoffs, face/certificate forgery, altered support, misleading
minimum interpretation and partial-failure controls. Full compilation and Spotless
passed. The existing 30 CI jobs, class assignments and checker steps are retained;
the new checker/test commands are additive and the new Java class is discovered
automatically. Owner clearance was given for `a874053`; full PR CI follows PR creation.

## Scope and next step

This is a research-only proof for two supplied private hands, two active players
at six seats, a fixed preflop prefix and a selected-flop continuation model. It
does not establish unrestricted six-player GTO, a coherent full-hand policy,
action frequencies or an attained full-action upper endpoint. `trainerAdmission`
remains false. The frontend/API grades and earlier 427 numerical evidence files
are unchanged. Raises are still unresolved in the trainer.

Next, qualify feedback against these tighter lower bounds with the existing
whole-face, reference and educational gates. Any resulting trainer source needs
its own identity, startup replay and review. AWS deployment remains paused.

## Review status

The project owner cleared code revision `a874053cbda0b74cb80ec45ef01ad50ee3034f84`
on 2026-11-12 (Europe/London), with “cleared a874053”. This applies only to that
revision. Full PR CI follows PR creation; Code Review History retains this and
earlier approvals.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`62a8a68d32d3e1e0e729dcee0dab7c1ff55cb785` and cleared PR creation on
2026-11-13 (Europe/London), with “cleared 62a8a68”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
