package com.pokerlab.solver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact full-game best responses within a declared, finite physical-deck chance subgame. */
public final class PhysicalDeckSubgameBestResponseAudit {
    public record Report(
            String physicalGameHash,
            List<Double> flopQuantiles,
            List<Double> turnQuantiles,
            List<Double> riverQuantiles,
            long visitedStates,
            int learnedInformationSets,
            int completedInformationSets,
            double fallbackPathProbability,
            HeadsUpBestResponse.Report bestResponse) {}

    private PhysicalDeckSubgameBestResponseAudit() {}

    public static Report assess(
            PhysicalDeckChanceSubgame game, CfrSolution sampled, long maximumVisitedStates) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(sampled, "sampled");
        if (maximumVisitedStates < 1)
            throw new IllegalArgumentException("A positive state budget is required");
        var projection = new Projection(game, sampled, maximumVisitedStates);
        projection.walk(game.initialState());
        var completed = new CfrSolution(sampled.iterations(), projection.strategies);
        double fallbackMass = projection.fallbackMass(game.initialState(), false, completed);
        var response = HeadsUpBestResponse.assess(game, completed);
        return new Report(
                game.physicalGameHash(),
                game.flopQuantiles(),
                game.turnQuantiles(),
                game.riverQuantiles(),
                projection.visited,
                projection.strategies.size() - projection.fallbackKeys.size(),
                projection.fallbackKeys.size(),
                fallbackMass,
                response);
    }

    private static final class Projection {
        private final PhysicalDeckChanceSubgame game;
        private final CfrSolution sampled;
        private final long maximum;
        private final Map<String, Map<String, Double>> strategies = new LinkedHashMap<>();
        private final Set<String> fallbackKeys = new HashSet<>();
        private final Map<String, List<String>> actionSets = new HashMap<>();
        private long visited;

        private Projection(PhysicalDeckChanceSubgame game, CfrSolution sampled, long maximum) {
            this.game = game;
            this.sampled = sampled;
            this.maximum = maximum;
        }

        private void walk(ButtonBigBlindPhysicalDeckGame.State state) {
            if (++visited > maximum)
                throw new IllegalArgumentException("Chance subgame exceeded state budget");
            if (game.isTerminal(state)) return;
            int player = game.currentPlayer(state);
            if (player == -1) {
                for (var outcome : game.chanceOutcomes(state)) walk(outcome.state());
                return;
            }
            List<String> actions = game.legalActions(state);
            String key = player + ":" + game.informationSet(state);
            List<String> previous = actionSets.putIfAbsent(key, List.copyOf(actions));
            if (previous != null && !previous.equals(actions))
                throw new IllegalArgumentException("Inconsistent actions in information set");
            if (!strategies.containsKey(key)) {
                var policy = sampled.at(player, game.informationSet(state));
                if (policy == null) {
                    Map<String, Double> uniform = new LinkedHashMap<>();
                    for (String action : actions) uniform.put(action, 1.0 / actions.size());
                    strategies.put(key, Map.copyOf(uniform));
                    fallbackKeys.add(key);
                } else {
                    validate(policy, actions);
                    strategies.put(key, policy);
                }
            }
            for (String action : actions) walk(game.afterAction(state, action));
        }

        private double fallbackMass(
                ButtonBigBlindPhysicalDeckGame.State state,
                boolean alreadyFallback,
                CfrSolution completed) {
            if (game.isTerminal(state)) return alreadyFallback ? 1 : 0;
            int player = game.currentPlayer(state);
            if (player == -1) {
                double sum = 0;
                for (var outcome : game.chanceOutcomes(state))
                    sum +=
                            outcome.probability()
                                    * fallbackMass(outcome.state(), alreadyFallback, completed);
                return sum;
            }
            String informationSet = game.informationSet(state);
            String key = player + ":" + informationSet;
            boolean nextFallback = alreadyFallback || fallbackKeys.contains(key);
            if (nextFallback) return 1;
            var policy = completed.at(player, informationSet);
            double sum = 0;
            for (String action : game.legalActions(state)) {
                double probability = policy.get(action);
                if (probability > 0)
                    sum +=
                            probability
                                    * fallbackMass(
                                            game.afterAction(state, action), false, completed);
            }
            return sum;
        }

        private static void validate(Map<String, Double> policy, List<String> actions) {
            if (policy.size() != actions.size())
                throw new IllegalArgumentException("Saved action set differs from subgame");
            double sum = 0;
            for (String action : actions) {
                Double probability = policy.get(action);
                if (probability == null || !Double.isFinite(probability) || probability < 0)
                    throw new IllegalArgumentException("Invalid saved action probability");
                sum += probability;
            }
            if (Math.abs(sum - 1) > 1e-9)
                throw new IllegalArgumentException("Saved probabilities must sum to one");
        }
    }
}
