# Four-hand paired-board curriculum and cached trainer

BTN faces a 3.25bb BB bet into a 6.5bb pot on `8d Kc Kd`. The separate
four-question backend covers `9s Js`, `Ah Kh`, `As Qs` and `Qh Th`, with fold/call
choices, certified EV/loss ranges and exact session reviews. This is a finite
24-world model with two active players at a six-seat table. It does not establish
full six-max cash coverage, unrestricted ranges, rake or multiway equilibrium.

## Qualification process

The request freezes all four v7 conditional selections, the common physical
parent, the .01bb teaching threshold and both reference/work budgets before
construction begins. One immutable game supplies all four owned studies. Each
study regenerates its complete parent and both action calculations, including
their own parent baselines. All 32 original endpoint certificates remain required.

After every study completes, exactly two fresh CFR+ solvers train for 80,000 and
160,000 iterations. Each starts with a new regret table. The two resulting policies
are shared across the four questions because the full physical game is identical.
Caller policies, checkpoints and parsed reports cannot supply fresh work. Literal
inactive-seat utilities are checked before the existing pruning mode is used.

Every question checks the primary policy and both references: full-parent quality,
reached-flop quality, actual and counterfactual full-path reach, complete opponent
security-face inclusion, action EV/loss inclusion and selected-decision regret.
Both a robust within-limit move and a robust outside-limit move are required.
Only the complete owned calculation or a complete matching replay constructs the
private qualified result used by this trainer. The public report remains
`trainerAdmission=false` and research-only.

The aggregate budget reserves the existing per-operation worst cases. It covers
12 parent solves (four explicit and eight endpoint baselines), 24 parent LPs,
eight conditional action calculations and 32 endpoint LPs. Compiler work includes
all twelve reductions and eight endpoint compilers. LP caps cover 24 separately
bounded parent LPs and eight cumulatively bounded endpoint groups. Actual charges,
fresh iterations and reference node visits are exported and independently summed.
These are numerical loop/work bounds, not a wall-clock or cloud cost estimate.

The report pin is
`c0e778afe5530740ee8d4dad3b9f9716a05fc45d8c1d141880d99212af99f61e`.
It binds the complete request, all questions, certificates, policies, qualification
checks and actual work. The older single-AsQs source and its original pin remain
separate; they do not grant this four-question capability.

## Cached trainer and API

The startup loader requires distinct bounded regular request/report files and the
exact report pin. It snapshots real file identity, size, modification time and
bytes before replay, fully regenerates qualification, then verifies the same file
snapshots before returning the cache. Startup is deliberately expensive; allow
up to 15 minutes on slower machines. No HTTP handler invokes an optimizer.

The new default-disabled route is
`/api/v1/trainer/research/paired-flop-interval`:

| Method/path | Result |
| --- | --- |
| `GET /` | Pinned metadata and four-question inventory size |
| `GET /questions/{0..3}?reportHash=...` | Public table state and selected hero hand |
| `POST /grade` | Cached grade for an explicit pin, question index and action |
| `POST /review` | Canonical review of explicit indexed choices |
| `POST /review/export` | The same exact replayable JSON document as a download |

Bodies reject unknown/duplicate fields, trailing tokens, coercion, stale pins,
invalid indices and illegal actions. Reviews have one global 100-attempt cap;
retries count as attempts and distinct question coverage is reported separately.
Clients submit choices, never grades, solver inputs or filesystem paths. Public
questions expose no opponent cards, private worlds, strategy tables or witnesses.

Local opt-in:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-paired-flop-interval.yml up --build -d
python scripts/smoke-paired-flop-interval-trainer.py
```

The overlay mounts the two source files read-only and supplies the exact report
pin. AWS remains paused. The existing browser trainer and six-question collection
retain their original protocols; this change supplies the backend for subsequent
table integration.

## Reproduction and verification

The Java entry points are `com.pokerlab.solver.SixMaxPairedFlopCurriculumMain`
(`solve|replay <request> <report>`) and
`com.pokerlab.solver.SixMaxPairedFlopTrainerMain`
(`create <request> <report> <pin> <new-review> [index:fold|call ...]`, or
`replay <request> <report> <pin> <review>`). Outputs must be new paths. The saved
review contains all eight question/action combinations.

```sh
python -m unittest discover -s scripts -p 'test_check_paired_flop_curriculum.py'
python -m unittest discover -s scripts -p 'test_check_paired_flop_drill.py'
python -m unittest discover -s scripts -p 'test_smoke_paired_flop_interval.py'
```

The independent checker reconstructs the physical parent and path posteriors,
verifies all 32 original endpoint certificates, checks all twelve policies against
literal game-tree best responses, validates inactive-payoff pruning and sums every
work charge. It verifies supplied evidence; it does not claim to run fresh training.
Freshness is established by complete owned Java execution/replay. Corruption tests
cover partial inventories, mixed sources, changed budgets, policies, reach, regret,
posteriors, grades, counts, ordering and provenance. A dedicated CI Docker smoke
checks default absence, real pinned startup, all questions/grades, exact exports,
typed OpenAPI, limits and rejections.

Next: a separately versioned ten-question collection containing two BB contexts,
four original BTN check/bet contexts and these four paired BTN fold/call contexts,
followed by table UI integration. The older AsQs v6/v7 evidence represents the
same teaching context and must not be counted twice as new scenario coverage.
