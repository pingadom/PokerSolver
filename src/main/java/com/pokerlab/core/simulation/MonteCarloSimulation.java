package com.pokerlab.core.simulation;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.EvaluatedHand;
import com.pokerlab.core.hand.HandEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Monte Carlo card-by-card simulator for Texas Hold'em.
 *
 * This file is deliberately self-contained for now. It includes the simulator
 * plus the small data types it needs: PlayerHand, SimulationRequest,
 * SimulationResult, SimulationTrial, and Mode.
 *
 * Put this file here:
 * src/main/java/com/pokerlab/core/simulation/MonteCarloSimulation.java
 */
public final class MonteCarloSimulation {

    private MonteCarloSimulation() {
    }

    public static SimulationResult run(SimulationRequest request) {
        validateRequest(request);

        long start = System.nanoTime();

        Random random = request.seed().isPresent()
                ? new Random(request.seed().getAsLong())
                : new Random();

        List<Card> knownCards = collectKnownCards(request);
        Card[] baseDeck = buildRemainingDeck(knownCards).toArray(Card[]::new);

        SimulationResult result = new SimulationResult(request.players(), request.simulations());

        for (int trialNumber = 1; trialNumber <= request.simulations(); trialNumber++) {
            List<Card> completedBoard = completeBoard(request.knownBoard(), baseDeck, random);
            boolean captureVerbose = request.mode() == SimulationMode.VERBOSE
                    && result.verboseTrials().size() < request.verboseLimit();

            Map<String, EvaluatedHand> evaluatedHands = null;
            List<String> winners;

            if (captureVerbose) {
                evaluatedHands = evaluatePlayers(request.players(), completedBoard);
                winners = findWinners(evaluatedHands);
            } else {
                winners = findWinners(request.players(), completedBoard);
            }

            if (winners.size() == 1) {
                result.recordWin(winners.get(0));
            } else {
                result.recordTie(winners);
            }

            if (captureVerbose) {
                result.addVerboseTrial(new SimulationTrial(
                        trialNumber,
                        request.players(),
                        completedBoard,
                        evaluatedHands,
                        winners
                ));
            }
        }

        long end = System.nanoTime();
        result.setRuntimeMillis((end - start) / 1_000_000);

        return result;
    }

    private static void validateRequest(SimulationRequest request) {
        if (request.players().size() < 2) {
            throw new IllegalArgumentException("At least two players are required");
        }

        if (request.players().size() > 10) {
            throw new IllegalArgumentException("Maximum supported players is 10");
        }

        int boardSize = request.knownBoard().size();
        if (!(boardSize == 0 || boardSize == 3 || boardSize == 4 || boardSize == 5)) {
            throw new IllegalArgumentException("Board must contain 0, 3, 4, or 5 cards");
        }

        Set<Card> seenCards = new HashSet<>();
        Set<String> seenPlayerNames = new HashSet<>();

        for (PlayerHand player : request.players()) {
            if (!seenPlayerNames.add(player.playerName())) {
                throw new IllegalArgumentException("Duplicate player name: " + player.playerName());
            }

            for (Card card : player.cards()) {
                if (!seenCards.add(card)) {
                    throw new IllegalArgumentException("Duplicate card found: " + card);
                }
            }
        }

        for (Card card : request.knownBoard()) {
            if (!seenCards.add(card)) {
                throw new IllegalArgumentException("Duplicate card found: " + card);
            }
        }
    }

    private static List<Card> collectKnownCards(SimulationRequest request) {
        List<Card> knownCards = new ArrayList<>();

        for (PlayerHand player : request.players()) {
            knownCards.addAll(player.cards());
        }

        knownCards.addAll(request.knownBoard());
        return knownCards;
    }

    private static List<Card> buildRemainingDeck(List<Card> knownCards) {
        List<Card> remainingCards = new ArrayList<>(new Deck().cards());
        remainingCards.removeAll(knownCards);
        return remainingCards;
    }

    private static List<Card> completeBoard(List<Card> knownBoard, Card[] deck, Random random) {
        return switch (knownBoard.size()) {
            case 0 -> {
                int first = random.nextInt(deck.length);
                int second = randomIndexExcept(deck.length, random, first, -1, -1, -1);
                int third = randomIndexExcept(deck.length, random, first, second, -1, -1);
                int fourth = randomIndexExcept(deck.length, random, first, second, third, -1);
                int fifth = randomIndexExcept(deck.length, random, first, second, third, fourth);
                yield List.of(deck[first], deck[second], deck[third], deck[fourth], deck[fifth]);
            }
            case 3 -> {
                int first = random.nextInt(deck.length);
                int second = randomIndexExcept(deck.length, random, first, -1, -1, -1);
                yield List.of(
                        knownBoard.get(0),
                        knownBoard.get(1),
                        knownBoard.get(2),
                        deck[first],
                        deck[second]
                );
            }
            case 4 -> List.of(
                    knownBoard.get(0),
                    knownBoard.get(1),
                    knownBoard.get(2),
                    knownBoard.get(3),
                    deck[random.nextInt(deck.length)]
            );
            case 5 -> knownBoard;
            default -> throw new IllegalArgumentException("Board must contain 0, 3, 4, or 5 cards");
        };
    }

    private static int randomIndexExcept(int bound, Random random, int first, int second, int third, int fourth) {
        int index;
        do {
            index = random.nextInt(bound);
        } while (index == first || index == second || index == third || index == fourth);

        return index;
    }

    private static Map<String, EvaluatedHand> evaluatePlayers(List<PlayerHand> players, List<Card> completedBoard) {
        Map<String, EvaluatedHand> evaluatedHands = new LinkedHashMap<>();
        Card board0 = completedBoard.get(0);
        Card board1 = completedBoard.get(1);
        Card board2 = completedBoard.get(2);
        Card board3 = completedBoard.get(3);
        Card board4 = completedBoard.get(4);

        for (PlayerHand player : players) {
            EvaluatedHand evaluatedHand = HandEvaluator.evaluateBest(
                    player.card1(),
                    player.card2(),
                    board0,
                    board1,
                    board2,
                    board3,
                    board4
            );
            evaluatedHands.put(player.playerName(), evaluatedHand);
        }

        return evaluatedHands;
    }

    private static List<String> findWinners(List<PlayerHand> players, List<Card> completedBoard) {
        int bestScore = -1;
        List<String> winners = new ArrayList<>();
        Card board0 = completedBoard.get(0);
        Card board1 = completedBoard.get(1);
        Card board2 = completedBoard.get(2);
        Card board3 = completedBoard.get(3);
        Card board4 = completedBoard.get(4);

        for (PlayerHand player : players) {
            int score = HandEvaluator.evaluateBestScore(
                    player.card1(),
                    player.card2(),
                    board0,
                    board1,
                    board2,
                    board3,
                    board4
            );

            if (score > bestScore) {
                bestScore = score;
                winners.clear();
                winners.add(player.playerName());
            } else if (score == bestScore) {
                winners.add(player.playerName());
            }
        }

        return winners;
    }

    private static List<String> findWinners(Map<String, EvaluatedHand> evaluatedHands) {
        EvaluatedHand bestHand = null;
        List<String> winners = new ArrayList<>();

        for (Map.Entry<String, EvaluatedHand> entry : evaluatedHands.entrySet()) {
            String playerName = entry.getKey();
            EvaluatedHand hand = entry.getValue();

            if (bestHand == null) {
                bestHand = hand;
                winners.add(playerName);
                continue;
            }

            int comparison = hand.compareTo(bestHand);

            if (comparison > 0) {
                bestHand = hand;
                winners.clear();
                winners.add(playerName);
            } else if (comparison == 0) {
                winners.add(playerName);
            }
        }

        return winners;
    }
}
