package com.pokerlab.solver;

import java.util.List;

/** A finite two-player zero-sum game with perfect recall. Player -1 is chance. */
public interface CfrGame<S> {
    S initialState();

    boolean isTerminal(S state);

    /** Utility for player 0 at a terminal state; player 1 receives its negation. */
    double terminalUtility(S state);

    /** Returns 0, 1, or -1 for a chance node. */
    int currentPlayer(S state);

    List<String> legalActions(S state);

    /** Must be identical at states indistinguishable to the acting player. */
    String informationSet(S state);

    S afterAction(S state, String action);

    List<ChanceOutcome<S>> chanceOutcomes(S state);

    /**
     * Draws one outcome from a uniform quantile in [0, 1). Games with very large chance nodes may
     * override this to avoid constructing the complete outcome list. The override must preserve the
     * distribution returned by {@link #chanceOutcomes(Object)}.
     */
    default ChanceOutcome<S> sampleChanceOutcome(S state, double quantile) {
        if (!Double.isFinite(quantile) || quantile < 0 || quantile >= 1)
            throw new IllegalArgumentException("Chance quantile must be in [0, 1)");
        List<ChanceOutcome<S>> outcomes = chanceOutcomes(state);
        if (outcomes.isEmpty()) throw new IllegalArgumentException("Empty chance node");
        double sum = outcomes.stream().mapToDouble(ChanceOutcome::probability).sum();
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Chance probabilities must sum to one");
        double cumulative = 0;
        for (ChanceOutcome<S> outcome : outcomes) {
            cumulative += outcome.probability();
            if (quantile < cumulative) return outcome;
        }
        return outcomes.getLast();
    }
}
