package com.pokerlab.core.simulation;

import com.pokerlab.core.card.Card;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public record SimulationRequest(
        List<PlayerHand> players,
        List<Card> knownBoard,
        int simulations,
        SimulationMode mode,
        OptionalLong seed,
        int verboseLimit) {
    public SimulationRequest {
        Objects.requireNonNull(players, "players cannot be null");
        Objects.requireNonNull(knownBoard, "knownBoard cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(seed, "seed cannot be null");

        players = List.copyOf(players);
        knownBoard = List.copyOf(knownBoard);
        validateScenario(players, knownBoard);

        if (simulations <= 0) {
            throw new IllegalArgumentException("simulations must be positive");
        }

        if (verboseLimit < 0) {
            throw new IllegalArgumentException("verboseLimit cannot be negative");
        }
    }

    public static SimulationRequest quickWithSeed(
            List<PlayerHand> players,
            List<Card> knownBoard,
            int simulations,
            OptionalLong optlong) {
        return new SimulationRequest(
                players, knownBoard, simulations, SimulationMode.QUICK, optlong, 0);
    }

    public static SimulationRequest quick(
            List<PlayerHand> players, List<Card> knownBoard, int simulations) {
        return new SimulationRequest(
                players, knownBoard, simulations, SimulationMode.QUICK, OptionalLong.empty(), 0);
    }

    public static SimulationRequest verbose(
            List<PlayerHand> players, List<Card> knownBoard, int simulations, int verboseLimit) {
        return new SimulationRequest(
                players,
                knownBoard,
                simulations,
                SimulationMode.VERBOSE,
                OptionalLong.empty(),
                verboseLimit);
    }

    public static SimulationRequest verboseWithSeed(
            List<PlayerHand> players,
            List<Card> knownBoard,
            int simulations,
            OptionalLong optlong,
            int verboseLimit) {
        return new SimulationRequest(
                players, knownBoard, simulations, SimulationMode.VERBOSE, optlong, verboseLimit);
    }

    private static void validateScenario(List<PlayerHand> players, List<Card> knownBoard) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("At least two players are required");
        }

        if (players.size() > 9) {
            throw new IllegalArgumentException("Maximum supported players is 9");
        }

        int boardSize = knownBoard.size();
        if (boardSize > 5) {
            throw new IllegalArgumentException("Board must contain zero to five cards");
        }

        Set<Card> seenCards = new HashSet<>();
        Set<String> seenPlayerNames = new HashSet<>();

        for (PlayerHand player : players) {
            if (!seenPlayerNames.add(player.playerName())) {
                throw new IllegalArgumentException("Duplicate player name: " + player.playerName());
            }

            for (Card card : player.cards()) {
                if (!seenCards.add(card)) {
                    throw new IllegalArgumentException("Duplicate card found: " + card);
                }
            }
        }

        for (Card card : knownBoard) {
            if (!seenCards.add(card)) {
                throw new IllegalArgumentException("Duplicate card found: " + card);
            }
        }
    }
}
