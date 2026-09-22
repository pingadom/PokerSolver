package com.pokerlab.core.simulation;

import com.pokerlab.core.card.*;
import com.pokerlab.core.hand.*;
import java.util.List;
import java.util.Map;

public record SimulationTrial(
        int trialNumber,
        List<PlayerHand> players,
        List<Card> board,
        Map<String, EvaluatedHand> evaluatedHands,
        List<String> winners) {
    public SimulationTrial {
        players = List.copyOf(players);
        board = List.copyOf(board);
        evaluatedHands = Map.copyOf(evaluatedHands);
        winners = List.copyOf(winners);
    }
}
