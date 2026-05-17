# PokerLab Phase 1

Phase 1 of PokerLab: a core poker engine for cards, decks, hand evaluation, and showdown comparison.

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

- Java 17+
- Maven 3.8+

## Run tests

```bash
mvn test
```

## Run demo

```bash
mvn exec:java
```

Or provide hero, villain, and board using compact two-character card notation:

```bash
mvn exec:java -Dexec.args="AsKs QdQc Ah7c2s9dJc"
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
