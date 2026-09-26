package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact information-set best responses for finite, perfect-recall two-player games. Each target
 * information set gathers all its hidden states with chance/opponent reach only. Choosing one
 * action against that weighted set prevents the response from seeing private opponent cards.
 */
public final class HeadsUpBestResponse {
    public record Report(
            double firstBestResponse, double secondBestResponse, double profileValue, double gap) {}

    private HeadsUpBestResponse() {}

    public static <S> Report assess(CfrGame<S> game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        double first = new Response<>(game, solution, 0).value();
        double second = -new Response<>(game, solution, 1).value();
        double profile = StrategyEvaluator.playerZeroUtility(game, solution);
        double gap = first - second;
        if (gap < -1e-8 || profile > first + 1e-8 || profile < second - 1e-8)
            throw new IllegalStateException("Inconsistent best-response bounds");
        return new Report(first, second, profile, Math.max(0, gap));
    }

    private record WeightedState<S>(S state, double counterfactualReach) {}

    private static final class Response<S> {
        private final CfrGame<S> game;
        private final CfrSolution solution;
        private final int target;
        private final Map<String, List<WeightedState<S>>> states = new LinkedHashMap<>();
        private final Map<String, List<String>> legal = new HashMap<>();
        private final Map<String, String> choices = new HashMap<>();

        private Response(CfrGame<S> game, CfrSolution solution, int target) {
            this.game = game;
            this.solution = solution;
            this.target = target;
        }

        private double value() {
            collect(game.initialState(), 1);
            return evaluate(game.initialState());
        }

        private void collect(S state, double reach) {
            if (game.isTerminal(state) || reach == 0) return;
            int player = game.currentPlayer(state);
            if (player == -1) {
                for (ChanceOutcome<S> outcome : chance(state))
                    collect(outcome.state(), reach * outcome.probability());
                return;
            }
            List<String> actions = List.copyOf(game.legalActions(state));
            if (player == target) {
                String key = game.informationSet(state);
                List<String> previous = legal.putIfAbsent(key, actions);
                if (previous != null && !previous.equals(actions))
                    throw new IllegalArgumentException("Inconsistent actions in information set");
                states.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new WeightedState<>(state, reach));
                for (String action : actions) collect(game.afterAction(state, action), reach);
            } else {
                Map<String, Double> strategy = strategy(state, player, actions);
                for (String action : actions)
                    collect(game.afterAction(state, action), reach * strategy.get(action));
            }
        }

        private double evaluate(S state) {
            if (game.isTerminal(state)) {
                double utility = game.terminalUtility(state);
                return target == 0 ? utility : -utility;
            }
            int player = game.currentPlayer(state);
            if (player == -1) {
                double utility = 0;
                for (ChanceOutcome<S> outcome : chance(state))
                    if (outcome.probability() > 0)
                        utility += outcome.probability() * evaluate(outcome.state());
                return utility;
            }
            if (player == target) {
                String key = game.informationSet(state);
                String action = choices.get(key);
                if (action == null) action = resolve(key);
                return evaluate(game.afterAction(state, action));
            }
            List<String> actions = game.legalActions(state);
            Map<String, Double> strategy = strategy(state, player, actions);
            double utility = 0;
            for (String action : actions) {
                double probability = strategy.get(action);
                if (probability > 0)
                    utility += probability * evaluate(game.afterAction(state, action));
            }
            return utility;
        }

        private String resolve(String key) {
            List<WeightedState<S>> candidates = states.get(key);
            if (candidates == null) throw new IllegalArgumentException("Missing information set");
            String bestAction = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (String action : legal.get(key)) {
                double value = 0;
                for (WeightedState<S> candidate : candidates)
                    value +=
                            candidate.counterfactualReach()
                                    * evaluate(game.afterAction(candidate.state(), action));
                if (value > bestValue) {
                    bestValue = value;
                    bestAction = action;
                }
            }
            choices.put(key, bestAction);
            return bestAction;
        }

        private Map<String, Double> strategy(S state, int player, List<String> actions) {
            Map<String, Double> probabilities = solution.at(player, game.informationSet(state));
            if (probabilities == null || probabilities.size() != actions.size())
                throw new IllegalArgumentException("Missing opponent strategy");
            double sum = 0;
            for (String action : actions) {
                Double probability = probabilities.get(action);
                if (probability == null || !Double.isFinite(probability) || probability < 0)
                    throw new IllegalArgumentException("Invalid opponent strategy");
                sum += probability;
            }
            if (Math.abs(sum - 1) > 1e-9)
                throw new IllegalArgumentException("Opponent strategy must sum to one");
            return probabilities;
        }

        private List<ChanceOutcome<S>> chance(S state) {
            List<ChanceOutcome<S>> outcomes = game.chanceOutcomes(state);
            if (outcomes.isEmpty()) throw new IllegalArgumentException("Empty chance node");
            double sum = 0;
            for (ChanceOutcome<S> outcome : outcomes) {
                if (!Double.isFinite(outcome.probability()) || outcome.probability() < 0)
                    throw new IllegalArgumentException("Invalid chance probability");
                sum += outcome.probability();
            }
            if (Math.abs(sum - 1) > 1e-9)
                throw new IllegalArgumentException("Chance probabilities must sum to one");
            return outcomes;
        }
    }
}
