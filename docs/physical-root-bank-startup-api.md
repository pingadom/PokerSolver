# Physical root-bank startup source and HTTP provenance

The proposed backend integration lets an operator select the reproduced physical
parent cache while preserving the existing website's complete public protocol.
The original startup remains the default and the global research flag remains off.
This later code awaits explicit owner review; the historical approval below applies
only to the earlier cache revision.

## Operator selection and owned authority

Set `TRAINER_ROOT_HAND_BANK_STARTUP_SOURCE=PHYSICAL_PARENT_REUSE` together with the
existing enabled flag, exact public study hash and the new physical manifest path.
The two accepted source names are `ORIGINAL` and `PHYSICAL_PARENT_REUSE`; case,
whitespace, unknown names and mismatched manifests reject. There is no automatic
fallback to a different source. An invalid selection is ignored only when the
entire research feature is disabled, in which case no source bean or route exists.

`RootHandBankStartupSource` has a private constructor. Its loader selects one
existing owned loader and returns a source capability only after the full replay
succeeds. The old bean name `rootHandBankDrill` and the existing direct configuration
validation entry point remain. The source and grade handlers share the same cached
result object. DTOs, JSON flags or client-supplied values cannot mint a source.

The original path retains four parent calls/eight parent LPs; the physical reuse
path uses one parent call/two parent LPs. Both retain one physical game, fifteen
endpoint LPs and fresh 80k/160k references. Existing numerical caps, source pins,
quality gates, grades and trainer admission are unchanged. Logs distinguish a
startup reservation from a completed owned replay, so rejected configuration does
not count as successful numerical work.

## Preserve the public lesson contract

The frontend compares the complete metadata response against its frozen public
protocol, including every field and the historical eight-parent startup reservation.
Changing that response to advertise two parent LPs would break the current client.
The existing metadata, question, grading, review and export handlers remain unchanged.
All three hands, nine grades, current-decision EV shifts, loss bounds, unresolved
raises, source IDs and canonical exports retain their original semantics.

The separate no-store endpoint
`GET /api/v1/trainer/research/root-hand-bank-drill/startup` reports runtime provenance.
Its schema is `pokerlab-root-hand-bank-startup/v1`. It returns the selected source,
public study ID, source proof ID, actual startup reservation, question/grade counts
and an explicit `UNCHANGED_HISTORICAL_PUBLIC_PROTOCOL_RESERVATION` explanation of
existing metadata. Original proof identity is the original bank hash; physical
reuse proof identity is the separately pinned `feb33163...` report. Public question
and grade requests still use the original `e3331443...` source ID.

Provenance is cached with the verified source. Reading it, grading or reviewing
choices never reads files or restarts the solver. It exposes no input paths,
opponent cards, future boards, private strategies or certificates. The endpoint
is absent when the research feature is off. It adds no new grading authority.

## Local reproduction

The new overlay mounts exactly four bounded, read-only inputs and selects the
physical source explicitly:

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-physical-root-bank.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.trainer-physical-root-bank.yml up -d --no-deps api
python scripts/smoke-physical-root-bank-startup.py
```

The API image must contain this revision first. To inspect an original startup
with the same public protocol, run the existing root-hand-bank overlay followed
by `python scripts/smoke-physical-root-bank-startup.py --source ORIGINAL`.
Both source choices remain research-only and require the corresponding manifest.
AWS deployment remains paused.

## Validation and work accounting

Twenty-five Java cases passed without skips: eight original-default HTTP cases
(including one new source assertion), nine physical-source HTTP cases, four new
source guards, one new disabled-source case and three retained disabled controls.
All seven earlier original HTTP cases and their identities remain. The suite
performed one original and one physical owned startup, completing in 184.125 seconds
including Maven. These are functional observations, not comparative benchmarks.

A separate native HTTP process performed one physical startup and passed the
complete original three-question/nine-grade contract, strict inputs, mixed/empty/
repeated/max-128 reviews, canonical exports, provenance and OpenAPI. All four startup
inputs were then made unavailable and the full contract passed again. The log
contained exactly one completed startup. The process was stopped afterward; no
approved browser preview or existing server was changed. Native verification took
95.313 seconds including startup and both contract passes.

All three local physical starts were declared before execution: 8+2+2 parent LPs,
45 endpoint LPs and six fresh references/720k total iterations. No numerical failure
was retried and no public numerical fixture was generated. All source/test/resource
bytes were frozen before the first owned run and remain unchanged. All 441 earlier
data files and all 28 solver resources are preserved.

Eleven new smoke controls and five retained controls passed. The new smoke pins and
reuses the unchanged original HTTP verifier, then strictly checks source identities,
field types, reservations, counts and admission. Spotless, compilation and final
packaging/verify with tests skipped passed. Strict SnakeYAML parsing verifies all
thirty expanded jobs, original steps/commands, limits, disabled/original defaults
and four read-only mounts. Docker Compose was unavailable locally; actual container
verification remains for PR CI after owner clearance.

CI retains all 392 earlier class assignments and auto-discovers three new classes
on services-1. All thirty jobs and original timeouts remain. The existing root-hand-
bank job additionally checks disabled provenance, original-source provenance and
one separately opted-in physical startup. Existing original and qualified-raise
smoke paths stay in place. No numerical command or dependency is removed or upgraded.

The test run emitted the existing Mockito dynamic-agent attachment warning. Tests
passed. This is recorded as test configuration cleanup, not an upstream defect.
A local YAML audit initially counted matrix job definitions as executed jobs; the
check was corrected to account for the ten backend matrix entries. No solver
replay was needed for that audit correction.

## Scope and next step

The model still has two active players at a six-seat table, supplied ranges and a
fixed prefix. It is not an unrestricted six-player equilibrium or a coherent full-
hand strategy. Trainer admission stays false. The separately qualified tighter
77/AJs source is unchanged. Next continue bounded range/continuation coverage with separate qualification
and review for each new educational decision. The provenance endpoint supplies
operator status as that coverage grows.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`0c134ab61c74462795a41552a9c17ac75aa882e7` and cleared PR creation on
2026-11-15 (Europe/London), with “I reviewed 0c134ab — clear it”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
