package com.pokerlab.core.simulation;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.EvaluatedHand;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Seeded Monte Carlo simulator for Texas Hold'em. Samples missing community cards uniformly without
 * replacement. Timing is observational and is excluded from reproducibility guarantees.
 */
public final class MonteCarloSimulation {

    private MonteCarloSimulation() {}

    public static SimulationResult run(SimulationRequest request) {
        Objects.requireNonNull(request, "request");

        long start = System.nanoTime();

        Random random =
                request.seed().isPresent() ? new Random(request.seed().getAsLong()) : new Random();

        List<Card> knownCards = collectKnownCards(request);
        Card[] baseDeck = buildRemainingDeck(knownCards).toArray(Card[]::new);

        SimulationResult result = new SimulationResult(request.players(), request.simulations());

        for (int trialNumber = 0; trialNumber < request.simulations(); trialNumber++) {
            List<Card> completedBoard = completeBoard(request.knownBoard(), baseDeck, random);
            boolean captureVerbose =
                    request.mode() == SimulationMode.VERBOSE
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
                result.addVerboseTrial(
                        new SimulationTrial(
                                trialNumber + 1,
                                request.players(),
                                completedBoard,
                                evaluatedHands,
                                winners));
            }
        }

        long end = System.nanoTime();
        result.setRuntimeMillis((end - start) / 1_000_000);

        return result;
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
        if (knownBoard.size() == 5) return knownBoard;
        List<Card> board = new ArrayList<>(knownBoard);
        int[] sampled = new int[5 - knownBoard.size()];
        for (int i = 0; i < sampled.length; i++) {
            int index;
            boolean duplicate;
            do {
                index = random.nextInt(deck.length);
                duplicate = false;
                for (int j = 0; j < i; j++) {
                    if (sampled[j] == index) duplicate = true;
                }
            } while (duplicate);
            sampled[i] = index;
            board.add(deck[index]);
        }
        return board;
    }

    private static Map<String, EvaluatedHand> evaluatePlayers(
            List<PlayerHand> players, List<Card> completedBoard) {
        Map<String, EvaluatedHand> evaluatedHands = new LinkedHashMap<>();
        Card board0 = completedBoard.get(0);
        Card board1 = completedBoard.get(1);
        Card board2 = completedBoard.get(2);
        Card board3 = completedBoard.get(3);
        Card board4 = completedBoard.get(4);

        for (PlayerHand player : players) {
            EvaluatedHand evaluatedHand =
                    HandEvaluator.evaluateBest(
                            player.card1(), player.card2(), board0, board1, board2, board3, board4);
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
            int score =
                    HandEvaluator.evaluateBestScore(
                            player.card1(), player.card2(), board0, board1, board2, board3, board4);

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
