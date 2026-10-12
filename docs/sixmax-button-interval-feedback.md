# Button decisions with stronger reference evidence

The seven-question collection adds three BTN decisions to the original four BB decisions. BTN faces a BB three-bet to 9bb after opening to 3bb. The exact hero hands are `9s Js`, `Ah Kh` and `Qh Th`; legal actions are fold, call and raise to 22bb. J9s calls within the 0.01bb loss limit, while AKs and QTs raise within that limit. These statements apply only to the saved supplied-belief model.

The new request/report format is `pokerlab-supplied-interval-feedback-{request,report}/v3`. It retains the v2 owned L1 numerical representation and fixes fresh CFR+ references at **2,000 and 4,000 iterations**. The version identifies the budget choice; callers cannot submit arbitrary budgets or ask the API to solve. Parsed reports must contain exactly their version's reference inventory. Existing v1/v2 formats retain 500/1,000 and their original artifacts and identities.

The v2 BTN cases remain rejected: 500/1,000 references require approximately 0.000483/0.000121bb security slack, outside the declared 0.0001bb face. New references require approximately 0.0000302/0.00000756bb. The face, minimum reach of 0.01 and loss limit of 0.01bb are unchanged. Materiality, primary regret/quality, reference quality, mix regret, posterior and EV/joint-loss checks still run; scalar action-EV drift remains recorded separately. A later BB77 candidate still fails primary policy reach and is excluded.

Every new artifact is generated through the owned solver and physically replayed before constructing a trainer capability. Each game enumerates 17,766,216 boards over the same 27 supplied joint worlds, with three exact hands per active player and folded-card blockers. Scope remains two active players at a six-seat table, equal 100bb stacks, no rake and mandatory checkdown. Source-history reach is unavailable and general trainer admission remains false. This does not solve ordinary six-max ranges or postflop betting.

## Collections and replay

The original [four-question manifest](data/supplied-four-question-interval-collection-manifest.json) and pin `227fd33b33ce8ed8be46af3d0d4515e4c7b017c3468ec6f9a025bf96d67070b2` remain unchanged. The separate [seven-question manifest](data/supplied-seven-question-interval-collection-manifest.json) has pin `776fcc01e70027b5584dbe3232d19e1670bbd8e8e087879ae020c510dccc6a00`. It keeps the old four questions first and appends J9s, AKs and QTs. The loader physically replays all seven mixed-version sources before serving cached questions or grades.

The [canonical review](data/supplied-seven-question-interval-collection-review.json) contains eight explicit attempts: seven distinct questions, six within-limit choices, one outside-limit choice and one strategy-dependent choice. The browser handles the new hero position, 6bb call and raise-to-22 action through its existing inventory-driven protocol. Old collection pins and cross-question report pins reject.

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-interval-collection-seven.yml up --build -d
python scripts/smoke-interval-collection.py --seven
```

Open `#interval-collection`. BTN starts at zero-based question 4. To replay a browser download with the solver runtime classpath:

```text
com.pokerlab.solver.SixMaxSuppliedIntervalCollectionMain replay
  docs/data/supplied-seven-question-interval-collection-manifest.json
  776fcc01e70027b5584dbe3232d19e1670bbd8e8e087879ae020c510dccc6a00
  <downloaded-review.json>
```

The original four-question Docker overlay remains available. AWS deployment stays paused.

## Independent numerical checks

The optional [BTN oracle](../scripts/check-supplied-extended-button-feedback.py) constructs a literal 729×5,832 complete-plan game from integer showdown counts. BTN reach depends on BB's actions, so its Charnes–Cooper denominator includes BB's raise-to-9 probability and hero's exact-hand mass. The existing BB-root oracle retains its separate constant-reach assumptions.

The [recorded check](data/supplied-button-five-target-extended-feedback-independent-check.json) contains 71 fresh LP solves, zero cached endpoints and 45 original joint-witness checks. It verifies original security, BB complete-plan mixtures, positive reach, hidden-world posteriors, continuations, action endpoints and correlated losses, plus saved reference inventories, inclusion margins and EV/loss rows. Java replay separately regenerates physical boards and references. The oracle grants no capability; NumPy/SciPy remain optional offline dependencies.

Manual negative controls also rejected a changed reference budget, a 0.25bb change to both copies of an EV endpoint, and a 0.25bb change to a joint-witness loss. Each failed without writing a success receipt. Backend tests bind the recorded check to its oracle source and all three compressed and expanded report bytes.

```sh
python scripts/check-supplied-extended-button-feedback.py \
  docs/data/supplied-button-five-target-js-extended-interval-feedback-report.json.gz \
  docs/data/supplied-button-five-target-aks-extended-interval-feedback-report.json.gz \
  docs/data/supplied-button-five-target-qts-extended-interval-feedback-report.json.gz \
  --output <new-independent-check.json>
```

Backend tests cover mixed-version physical loading, old-format rejection, primary reach, schema/budget mismatch, actor changes, canonical replay and stale pins. Real MVC tests exercise seven-question startup and all verdicts. Frontend tests preserve the original collection, exercise both hero seats and compare an eight-attempt download with the physical fixture. CI enforces fixture byte identity and smokes both overlays. Broader hands, realistic priors, additional active players and later-street betting remain further model milestones.
