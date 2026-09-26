package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fixed-board heads-up river game. Player zero checks or bets; after a check player one checks or
 * bets. Either bet faces a call/fold decision. Raises and further streets are outside this tree.
 */
public final class RiverBetGame implements CfrGame<RiverBetGame.State> {
    public record State(WeightedCombo first, WeightedCombo second, String history) {}

    private record Matchup(WeightedCombo first, WeightedCombo second) {}

    private final RiverBetSpot spot;
    private final List<ChanceOutcome<State>> deals;
    private final Map<Matchup, Integer> showdownResults;

    public RiverBetGame(RiverBetSpot spot) {
        this.spot = Objects.requireNonNull(spot, "spot");
        List<Matchup> legal = new ArrayList<>();
        double totalWeight = 0;
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (!first.conflictsWith(second)) {
                    legal.add(new Matchup(first, second));
                    totalWeight += first.weight() * second.weight();
                }
            }
        }
        if (legal.isEmpty() || !Double.isFinite(totalWeight) || totalWeight <= 0)
            throw new IllegalArgumentException("Ranges have no legal joint deal");
        List<ChanceOutcome<State>> preparedDeals = new ArrayList<>(legal.size());
        Map<Matchup, Integer> preparedShowdowns = new LinkedHashMap<>();
        for (Matchup matchup : legal) {
            preparedDeals.add(
                    new ChanceOutcome<>(
                            new State(matchup.first(), matchup.second(), ""),
                            matchup.first().weight() * matchup.second().weight() / totalWeight));
            preparedShowdowns.put(matchup, showdownResult(spot.board(), matchup));
        }
        deals = List.copyOf(preparedDeals);
        showdownResults = Map.copyOf(preparedShowdowns);
    }

    public RiverBetSpot spot() {
        return spot;
    }

    @Override
    public State initialState() {
        return new State(null, null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return switch (state.history()) {
            case "kk", "bf", "bc", "kbf", "kbc" -> true;
            default -> false;
        };
    }

    /** Player-zero chip EV centered on half the starting pot; all terminals sum to zero. */
    @Override
    public double terminalUtility(State state) {
        double halfPot = spot.potBb() / 2;
        return switch (state.history()) {
            case "bf" -> halfPot;
            case "kbf" -> -halfPot;
            case "kk" -> showdownResults.get(new Matchup(state.first(), state.second())) * halfPot;
            case "bc", "kbc" ->
                    showdownResults.get(new Matchup(state.first(), state.second()))
                            * (halfPot + spot.betBb());
            default -> throw new IllegalArgumentException("Not a terminal river state");
        };
    }

    @Override
    public int currentPlayer(State state) {
        if (state.first() == null) return -1;
        return switch (state.history()) {
            case "", "kb" -> 0;
            case "k", "b" -> 1;
            default -> throw new IllegalArgumentException("Terminal or invalid river history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        return switch (state.history()) {
            case "", "k" -> List.of("k", "b");
            case "b", "kb" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Not a river decision state");
        };
    }

    @Override
    public String informationSet(State state) {
        int player = currentPlayer(state);
        return (player == 0 ? state.first() : state.second()).key() + ":" + state.history();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal river action: " + action);
        return new State(state.first(), state.second(), state.history() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.first() != null) throw new IllegalArgumentException("Not a chance node");
        return deals;
    }

    private static int showdownResult(List<Card> board, Matchup matchup) {
        int firstScore =
                HandEvaluator.evaluateBestScore(
                        matchup.first().first(),
                        matchup.first().second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        board.get(4));
        int secondScore =
                HandEvaluator.evaluateBestScore(
                        matchup.second().first(),
                        matchup.second().second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        board.get(3),
                        board.get(4));
        return Integer.compare(firstScore, secondScore);
    }
}
