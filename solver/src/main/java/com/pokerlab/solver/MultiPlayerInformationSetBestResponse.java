package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact pure best response to a fixed multi-player profile in a finite game with public-depth
 * information sets. Opponent and chance reach is aggregated across hidden deals before choosing
 * each action; a target player's own reach is deliberately excluded. The result measures unilateral
 * deviation value in this specified game, not real-world poker GTO quality.
 */
public final class MultiPlayerInformationSetBestResponse {
    public record Report(
            List<Double> profileUtilitiesBb,
            List<Double> bestResponseUtilitiesBb,
            List<Double> deviationGainsBb,
            double nashConvBb,
            List<Map<String, String>> responseActions) {
        public Report {
            profileUtilitiesBb = List.copyOf(profileUtilitiesBb);
            bestResponseUtilitiesBb = List.copyOf(bestResponseUtilitiesBb);
            deviationGainsBb = List.copyOf(deviationGainsBb);
            responseActions = responseActions.stream().map(Map::copyOf).toList();
        }
    }

    private record Node<S>(S state, double opponentChanceReach) {}

    private static final class InformationSet<S> {
        private final int depth;
        private final List<String> actions;
        private final List<Node<S>> nodes = new ArrayList<>();

        private InformationSet(int depth, List<String> actions) {
            this.depth = depth;
            this.actions = List.copyOf(actions);
        }

        private void add(int candidateDepth, List<String> candidateActions, Node<S> node) {
            if (depth != candidateDepth || !actions.equals(candidateActions))
                throw new IllegalArgumentException(
                        "Information set needs one public depth and consistent legal actions");
            nodes.add(node);
        }
    }

    private MultiPlayerInformationSetBestResponse() {}

    public static <S> Report assess(MultiPlayerCfrGame<S> game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        double[] profile = MultiPlayerStrategyEvaluator.utilities(game, solution);
        List<Double> values = new ArrayList<>();
        List<Double> best = new ArrayList<>();
        List<Double> gains = new ArrayList<>();
        List<Map<String, String>> responses = new ArrayList<>();
        double nashConv = 0;
        for (int target = 0; target < game.playerCount(); target++) {
            Map<String, InformationSet<S>> informationSets = new LinkedHashMap<>();
            collect(game, solution, game.initialState(), target, 0, 1, informationSets);
            List<Map.Entry<String, InformationSet<S>>> bottomUp =
                    new ArrayList<>(informationSets.entrySet());
            bottomUp.sort(
                    Comparator.comparingInt(
                                    (Map.Entry<String, InformationSet<S>> entry) ->
                                            entry.getValue().depth)
                            .reversed());
            Map<String, String> response = new LinkedHashMap<>();
            Map<S, Double> continuationCache = new HashMap<>();
            for (var entry : bottomUp) {
                InformationSet<S> info = entry.getValue();
                double highest = Double.NEGATIVE_INFINITY;
                String selected = null;
                for (String action : info.actions) {
                    double value = 0;
                    for (Node<S> node : info.nodes)
                        value +=
                                node.opponentChanceReach()
                                        * continuation(
                                                game,
                                                solution,
                                                game.afterAction(node.state(), action),
                                                target,
                                                response,
                                                continuationCache);
                    if (selected == null || value > highest) {
                        highest = value;
                        selected = action;
                    }
                }
                response.put(entry.getKey(), selected);
            }
            double responseValue =
                    continuation(
                            game,
                            solution,
                            game.initialState(),
                            target,
                            response,
                            continuationCache);
            double gain = responseValue - profile[target];
            if (gain < -1e-7)
                throw new IllegalStateException("Best response is worse than the source profile");
            gain = Math.max(0, gain);
            values.add(profile[target]);
            best.add(responseValue);
            gains.add(gain);
            responses.add(response);
            nashConv += gain;
        }
        return new Report(values, best, gains, nashConv, responses);
    }

    private static <S> void collect(
            MultiPlayerCfrGame<S> game,
            CfrSolution solution,
            S state,
            int target,
            int depth,
            double opponentChanceReach,
            Map<String, InformationSet<S>> informationSets) {
        if (game.isTerminal(state)) return;
        int actor = game.currentPlayer(state);
        if (actor == -1) {
            for (var outcome : game.chanceOutcomes(state))
                collect(
                        game,
                        solution,
                        outcome.state(),
                        target,
                        depth + 1,
                        opponentChanceReach * outcome.probability(),
                        informationSets);
            return;
        }
        List<String> actions = game.legalActions(state);
        if (actor == target) {
            String key = game.informationSet(state);
            var info =
                    informationSets.computeIfAbsent(
                            key, ignored -> new InformationSet<>(depth, actions));
            info.add(depth, actions, new Node<>(state, opponentChanceReach));
            for (String action : actions)
                collect(
                        game,
                        solution,
                        game.afterAction(state, action),
                        target,
                        depth + 1,
                        opponentChanceReach,
                        informationSets);
        } else {
            for (String action : actions)
                collect(
                        game,
                        solution,
                        game.afterAction(state, action),
                        target,
                        depth + 1,
                        opponentChanceReach
                                * MultiPlayerStrategyEvaluator.probability(
                                        game, solution, state, action),
                        informationSets);
        }
    }

    private static <S> double continuation(
            MultiPlayerCfrGame<S> game,
            CfrSolution solution,
            S state,
            int target,
            Map<String, String> response,
            Map<S, Double> cache) {
        Double cached = cache.get(state);
        if (cached != null) return cached;
        double value;
        if (game.isTerminal(state)) {
            double[] utilities = game.terminalUtilities(state);
            if (utilities == null || utilities.length != game.playerCount())
                throw new IllegalArgumentException("Terminal utility count must match players");
            value = utilities[target];
        } else {
            int actor = game.currentPlayer(state);
            if (actor == -1) {
                value = 0;
                for (var outcome : game.chanceOutcomes(state))
                    value +=
                            outcome.probability()
                                    * continuation(
                                            game,
                                            solution,
                                            outcome.state(),
                                            target,
                                            response,
                                            cache);
            } else if (actor == target) {
                String action = response.get(game.informationSet(state));
                if (action == null || !game.legalActions(state).contains(action))
                    throw new IllegalStateException("Missing downstream best-response action");
                value =
                        continuation(
                                game,
                                solution,
                                game.afterAction(state, action),
                                target,
                                response,
                                cache);
            } else {
                value = 0;
                for (String action : game.legalActions(state))
                    value +=
                            MultiPlayerStrategyEvaluator.probability(game, solution, state, action)
                                    * continuation(
                                            game,
                                            solution,
                                            game.afterAction(state, action),
                                            target,
                                            response,
                                            cache);
            }
        }
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Best-response continuation is not finite");
        cache.put(state, value);
        return value;
    }
}
