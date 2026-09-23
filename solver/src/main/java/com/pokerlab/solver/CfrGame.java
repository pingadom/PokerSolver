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
}
