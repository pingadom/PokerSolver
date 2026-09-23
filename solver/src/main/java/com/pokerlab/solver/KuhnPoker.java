package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;

/** Three-card Kuhn poker, used to check the solver before Hold'em game trees are added. */
public final class KuhnPoker implements CfrGame<KuhnPoker.State> {
    public record State(int firstCard, int secondCard, String history) {}

    @Override
    public State initialState() {
        return new State(0, 0, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return switch (state.history()) {
            case "kk", "bf", "bc", "kbf", "kbc" -> true;
            default -> false;
        };
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        return switch (state.history()) {
            case "bf" -> 1;
            case "kbf" -> -1;
            case "kk" -> Integer.signum(state.firstCard() - state.secondCard());
            case "bc", "kbc" -> 2 * Integer.signum(state.firstCard() - state.secondCard());
            default -> throw new IllegalStateException("Unexpected history");
        };
    }

    @Override
    public int currentPlayer(State state) {
        if (state.firstCard() == 0) return -1;
        return switch (state.history()) {
            case "", "kb" -> 0;
            case "k", "b" -> 1;
            default -> throw new IllegalArgumentException("Terminal or invalid history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        return switch (state.history()) {
            case "", "k" -> List.of("k", "b");
            case "b", "kb" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public String informationSet(State state) {
        int card = currentPlayer(state) == 0 ? state.firstCard() : state.secondCard();
        return card + ":" + state.history();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal Kuhn action: " + action);
        return new State(state.firstCard(), state.secondCard(), state.history() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.firstCard() != 0) throw new IllegalArgumentException("Not a chance node");
        List<ChanceOutcome<State>> outcomes = new ArrayList<>(6);
        for (int first = 1; first <= 3; first++) {
            for (int second = 1; second <= 3; second++) {
                if (first != second)
                    outcomes.add(new ChanceOutcome<>(new State(first, second, ""), 1.0 / 6));
            }
        }
        return List.copyOf(outcomes);
    }
}
