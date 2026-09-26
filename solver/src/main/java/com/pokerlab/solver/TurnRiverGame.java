package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Two-player turn betting, exact public river chance, then river betting and showdown. */
public final class TurnRiverGame implements CfrGame<TurnRiverGame.State> {
    public record State(
            WeightedCombo first,
            WeightedCombo second,
            String turnHistory,
            Card river,
            String riverHistory) {}

    private record Showdown(WeightedCombo first, WeightedCombo second, Card river) {}

    private final TurnRiverSpot spot;
    private final List<ChanceOutcome<State>> deals;
    private final Map<Showdown, Integer> showdownResults;

    public TurnRiverGame(TurnRiverSpot spot) {
        this.spot = Objects.requireNonNull(spot, "spot");
        double totalWeight = 0;
        for (WeightedCombo first : spot.firstRange())
            for (WeightedCombo second : spot.secondRange())
                if (!first.conflictsWith(second)) totalWeight += first.weight() * second.weight();
        if (totalWeight <= 0 || !Double.isFinite(totalWeight))
            throw new IllegalArgumentException("Ranges have no legal joint deal");
        List<ChanceOutcome<State>> prepared = new ArrayList<>();
        Map<Showdown, Integer> results = new HashMap<>();
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (first.conflictsWith(second)) continue;
                prepared.add(
                        new ChanceOutcome<>(
                                new State(first, second, "", null, ""),
                                first.weight() * second.weight() / totalWeight));
                for (Card river : remainingCards(first, second))
                    results.put(new Showdown(first, second, river), showdown(first, second, river));
            }
        }
        deals = List.copyOf(prepared);
        showdownResults = Map.copyOf(results);
    }

    public TurnRiverSpot spot() {
        return spot;
    }

    @Override
    public State initialState() {
        return new State(null, null, "", null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        if (state.turnHistory().equals("bf") || state.turnHistory().equals("kbf")) return true;
        return state.river() != null
                && (isComplete(state.riverHistory())
                        || state.riverHistory().equals("bf")
                        || state.riverHistory().equals("kbf"));
    }

    @Override
    public double terminalUtility(State state) {
        double halfPot = spot.potBb() / 2;
        if (state.turnHistory().equals("bf")) return halfPot;
        if (state.turnHistory().equals("kbf")) return -halfPot;
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        double turnContribution = state.turnHistory().equals("kk") ? 0 : spot.turnBetBb();
        double riverHalfPot = halfPot + turnContribution;
        return switch (state.riverHistory()) {
            case "bf" -> riverHalfPot;
            case "kbf" -> -riverHalfPot;
            case "kk" -> showdownResult(state) * riverHalfPot;
            case "bc", "kbc" -> showdownResult(state) * (riverHalfPot + spot.riverBetBb());
            default -> throw new IllegalArgumentException("Invalid river terminal history");
        };
    }

    @Override
    public int currentPlayer(State state) {
        if (state.first() == null || (isComplete(state.turnHistory()) && state.river() == null))
            return -1;
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        String history = state.river() == null ? state.turnHistory() : state.riverHistory();
        return switch (history) {
            case "", "kb" -> 0;
            case "k", "b" -> 1;
            default -> throw new IllegalArgumentException("Invalid history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        String history = state.river() == null ? state.turnHistory() : state.riverHistory();
        return switch (history) {
            case "", "k" -> List.of("k", "b");
            case "b", "kb" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public String informationSet(State state) {
        WeightedCombo own = currentPlayer(state) == 0 ? state.first() : state.second();
        if (state.river() == null) return own.key() + "|T:" + state.turnHistory();
        return own.key()
                + "|T:"
                + state.turnHistory()
                + "|R:"
                + state.river().compact()
                + ":"
                + state.riverHistory();
    }

    @Override
    public State afterAction(State state, String action) {
        if (currentPlayer(state) == -1 || !legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal action: " + action);
        return state.river() == null
                ? new State(state.first(), state.second(), state.turnHistory() + action, null, "")
                : new State(
                        state.first(),
                        state.second(),
                        state.turnHistory(),
                        state.river(),
                        state.riverHistory() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.first() == null) return deals;
        if (!isComplete(state.turnHistory()) || state.river() != null)
            throw new IllegalArgumentException("Not a chance node");
        List<Card> remaining = remainingCards(state.first(), state.second());
        List<ChanceOutcome<State>> outcomes = new ArrayList<>(remaining.size());
        for (Card card : remaining)
            outcomes.add(
                    new ChanceOutcome<>(
                            new State(state.first(), state.second(), state.turnHistory(), card, ""),
                            1.0 / remaining.size()));
        return List.copyOf(outcomes);
    }

    private List<Card> remainingCards(WeightedCombo first, WeightedCombo second) {
        Deck deck = new Deck();
        spot.turnBoard().forEach(deck::remove);
        deck.remove(first.first());
        deck.remove(first.second());
        deck.remove(second.first());
        deck.remove(second.second());
        return deck.cards();
    }

    private int showdownResult(State state) {
        return showdownResults.get(new Showdown(state.first(), state.second(), state.river()));
    }

    private int showdown(WeightedCombo first, WeightedCombo second, Card river) {
        List<Card> board = spot.turnBoard();
        int firstScore =
                HandEvaluator.evaluateBestScore(
                        first.first(),
                        first.second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        river);
        int secondScore =
                HandEvaluator.evaluateBestScore(
                        second.first(),
                        second.second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        river);
        return Integer.compare(firstScore, secondScore);
    }

    private static boolean isComplete(String history) {
        return switch (history) {
            case "kk", "bc", "kbc" -> true;
            default -> false;
        };
    }
}
