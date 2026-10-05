package com.pokerlab.solver;

import java.util.List;
import java.util.OptionalDouble;

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

    /**
     * Optional exact best-response shortcut. Present only when this player never acts again and
     * every descendant terminal has this same literal utility, under every action and chance
     * outcome. Empty by default. This is not an estimate, baseline or policy-dependent value.
     */
    default OptionalDouble inactivePlayerUtility(S state, int player) {
        return OptionalDouble.empty();
    }

    /** Fixed internal control variate; never a policy feature or a replacement payoff. */
    default double chanceBaselineUtility(S state, int player) {
        return 0;
    }
}
