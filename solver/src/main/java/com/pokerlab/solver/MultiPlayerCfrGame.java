package com.pokerlab.solver;

import java.util.List;

/** A finite perfect-recall game with chance and two to six players. */
public interface MultiPlayerCfrGame<S> {
    int playerCount();

    S initialState();

    boolean isTerminal(S state);

    /** Literal chip profit for every player, in seat order. */
    double[] terminalUtilities(S state);

    /** Player index, or -1 for chance. */
    int currentPlayer(S state);

    List<String> legalActions(S state);

    /** Must be identical at states indistinguishable to the acting player. */
    String informationSet(S state);

    S afterAction(S state, String action);

    List<ChanceOutcome<S>> chanceOutcomes(S state);
}
