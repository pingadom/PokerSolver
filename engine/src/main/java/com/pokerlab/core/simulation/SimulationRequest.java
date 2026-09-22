package com.pokerlab.core.simulation;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

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
}
