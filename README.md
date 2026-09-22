# PokerLab Cloud (in development)

PokerLab is being expanded into a distributed poker simulation platform. The existing engine and CLI are preserved in the `engine` Maven module. See [build progress](docs/build-progress.md) for verified milestones and remaining acceptance gates.

## Features

- Card, rank, and suit representation
- Standard 52-card deck
- Shuffling and dealing
- 5-card poker hand evaluator
- 7-card Texas Hold'em evaluator by checking all 21 five-card combinations
- Showdown comparison between two players
- Starter JUnit tests
- Simple CLI demo

## Requirements

- Java 21
- Maven 3.8+

## Run tests

```bash
mvn test
```

## Run demo

```bash
mvn -pl engine exec:java
```

Or provide hero, villain, and board using compact two-character card notation:

```bash
mvn -pl engine exec:java -Dexec.args="AsKs QdQc Ah7c2s9dJc"
```

Use `T` for ten, e.g. `Ts` = Ten of spades.

## Package layout

```text
com.pokerlab.core.card      Card, Suit, Rank, Deck
com.pokerlab.core.hand      HandCategory, HandRank, EvaluatedHand, HandEvaluator
com.pokerlab.core.showdown  Showdown, ShowdownResult, Winner
com.pokerlab.app           CLI demo
```

## Next steps

1. Add stricter input validation for CLI use.
2. Add more evaluator tests for every hand category.
3. Add benchmark tests before optimising.
4. Add Monte Carlo equity simulation in Phase 2.
