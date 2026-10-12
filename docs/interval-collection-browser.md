# Interval collection browser trainer

The [seven-question collection](sixmax-button-interval-feedback.md) adds three BTN responses to the original four BB questions. Its separate overlay and pin preserve the original collection. The browser validates the configured inventory and displays the acting hero at either position.

Open `#interval-collection` from the solver section. The collection API must be explicitly enabled with the existing [collection overlay](sixmax-l1-interval-collection.md#separate-http-opt-in); the original `#interval-preflop?report=<hash>` route remains independent.

The browser loads metadata and the complete public question inventory before enabling practice. It validates collection identity, selection, ordered indices, distinct questions, public table contributions, hero cards and legal actions. Inner questions and grades keep the original strict interval protocol. Opponent cards and solver internals are never fetched by this page.

Each question appears on the six-seat table. Previous/next buttons sit below the action buttons. The prompt shows both the current question and distinct questions practised; the attempt count is separate. After choosing a move, every action button shows its EV range, joint loss range and verdict. Feedback appears immediately below the controls. Returning to a question restores its latest grade; “Try another move” records another explicit attempt. Navigation preserves the session. “Start over” clears it and reloads current evidence.

There are at most 100 attempts across the entire collection. Navigation and review remain available after that cap. Repeating one question 100 times still covers one question. The last question is labelled explicitly; there is no automatic wrap, fabricated seed variation, average EV or scalar accuracy.

Grades bind collection hash, question index, report hash and action. The client requires stable alternatives within each question, while different questions can correctly prefer different moves. Duplicate submissions and navigation during a request are blocked. Unmounting, resetting or externally changing the URL aborts work and discards late responses. Transient network failures preserve successful attempts; stale evidence or inconsistent responses block practice until an explicit reload.

Reviews send only ordered explicit choices. The response must match the complete cached question inventory, captured grades, attempt order, coverage and all three verdict counts. Only then is JSON export offered. Downloads retain the backend's canonical offline review format and can be independently replayed:

```text
com.pokerlab.solver.SixMaxSuppliedIntervalCollectionMain
  replay <manifest.json> <collection-hash> <downloaded-review.json>
```

Deep links use `#interval-collection?collection=<hash>&question=<zero-based-index>`. Invalid indices, duplicate/unknown query parameters and stale pins reject before practice. The displayed current question always comes from the validated inventory.

The fixture is a byte-identical copy of the physically replayed offline review, enforced by CI. Tests cover distinct calling/raising decisions, all verdicts, navigation, reset, caps, explicit retries, stale or forged responses, cancellation and exported JSON. These remain supplied-belief research scenarios with two active players, no rake and mandatory postflop checkdown. General trainer admission and AWS deployment remain off.
