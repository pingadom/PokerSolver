package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A conditional, two-player preflop shove/fold then call/fold subgame. Commitments and stack totals
 * are in big blinds. Other folded seats contribute only the fixed dead-money amount. When dead
 * money is positive, real chip profits sum to that fixed amount at every terminal. Negating player
 * zero's profit for player one gives a strategically equivalent zero-sum game, but player one's
 * reported utility is not its literal chip profit.
 */
public final class PreflopAllInGame implements CfrGame<PreflopAllInGame.State> {
    public record State(WeightedCombo first, WeightedCombo second, String history) {}

    private record Matchup(WeightedCombo first, WeightedCombo second) {}

    private final double firstCommitted;
    private final double secondCommitted;
    private final double totalStack;
    private final double deadMoney;
    private final List<ChanceOutcome<State>> deals;
    private final Map<Matchup, EquityEstimate> equities;

    public PreflopAllInGame(
            List<WeightedCombo> firstRange,
            List<WeightedCombo> secondRange,
            double firstCommitted,
            double secondCommitted,
            double totalStack,
            double deadMoney,
            PreflopEquityOracle oracle) {
        Objects.requireNonNull(oracle, "oracle");
        validateRange(firstRange);
        validateRange(secondRange);
        if (!Double.isFinite(firstCommitted)
                || !Double.isFinite(secondCommitted)
                || !Double.isFinite(totalStack)
                || !Double.isFinite(deadMoney)
                || firstCommitted < 0
                || secondCommitted < 0
                || deadMoney < 0
                || totalStack <= Math.max(firstCommitted, secondCommitted)) {
            throw new IllegalArgumentException("Invalid commitments, total stack, or dead money");
        }
        this.firstCommitted = firstCommitted;
        this.secondCommitted = secondCommitted;
        this.totalStack = totalStack;
        this.deadMoney = deadMoney;

        List<Matchup> valid = new ArrayList<>();
        double totalWeight = 0;
        for (WeightedCombo first : firstRange) {
            for (WeightedCombo second : secondRange) {
                if (!first.conflictsWith(second)) {
                    valid.add(new Matchup(first, second));
                    totalWeight += first.weight() * second.weight();
                }
            }
        }
        if (valid.isEmpty() || !Double.isFinite(totalWeight) || totalWeight <= 0)
            throw new IllegalArgumentException("Ranges have no valid unblocked matchups");
        List<ChanceOutcome<State>> preparedDeals = new ArrayList<>(valid.size());
        Map<Matchup, EquityEstimate> preparedEquities = new LinkedHashMap<>();
        for (Matchup matchup : valid) {
            double probability = matchup.first().weight() * matchup.second().weight() / totalWeight;
            preparedDeals.add(
                    new ChanceOutcome<>(
                            new State(matchup.first(), matchup.second(), ""), probability));
            preparedEquities.put(
                    matchup,
                    Objects.requireNonNull(
                            oracle.estimate(matchup.first(), matchup.second()), "equity estimate"));
        }
        deals = List.copyOf(preparedDeals);
        equities = Map.copyOf(preparedEquities);
    }

    public double maximumEquityStandardError() {
        return equities.values().stream()
                .mapToDouble(EquityEstimate::standardError)
                .max()
                .orElseThrow();
    }

    @Override
    public State initialState() {
        return new State(null, null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return state.history().equals("f")
                || state.history().equals("sf")
                || state.history().equals("sc");
    }

    @Override
    public double terminalUtility(State state) {
        return switch (state.history()) {
            case "f" -> -firstCommitted;
            case "sf" -> secondCommitted + deadMoney;
            case "sc" ->
                    equities.get(new Matchup(state.first(), state.second())).equity()
                                    * (2 * totalStack + deadMoney)
                            - totalStack;
            default -> throw new IllegalArgumentException("Not a terminal state");
        };
    }

    @Override
    public int currentPlayer(State state) {
        if (state.first() == null) return -1;
        return switch (state.history()) {
            case "" -> 0;
            case "s" -> 1;
            default -> throw new IllegalArgumentException("Terminal or invalid history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        return switch (state.history()) {
            case "" -> List.of("s", "f");
            case "s" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public String informationSet(State state) {
        return (currentPlayer(state) == 0 ? state.first() : state.second()).key()
                + ":"
                + state.history();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal preflop action: " + action);
        return new State(state.first(), state.second(), state.history() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.first() != null) throw new IllegalArgumentException("Not a chance node");
        return deals;
    }

    private static void validateRange(List<WeightedCombo> range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each player needs a nonempty range");
        Set<String> seen = new HashSet<>();
        for (WeightedCombo combo : range) {
            if (combo == null || !seen.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate combo in range");
        }
    }
}
