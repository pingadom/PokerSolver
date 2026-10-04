package com.pokerlab.solver;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit, budgeted uniform completion for offline exact audits of sparse sampled profiles. */
public final class MultiPlayerStrategyCompletion {
    public record Result(CfrSolution solution, int addedInformationSets, long visitedStates) {}

    private MultiPlayerStrategyCompletion() {}

    public static <S> Result uniformAtUnseen(
            MultiPlayerCfrGame<S> game, CfrSolution sampled, long maximumVisitedStates) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(sampled, "sampled");
        if (maximumVisitedStates < 1 || game.playerCount() < 2 || game.playerCount() > 6)
            throw new IllegalArgumentException("A positive budget and 2–6 players are required");
        var walker = new Walker<>(game, sampled.strategy(), maximumVisitedStates);
        walker.visit(game.initialState());
        if (!walker.encountered.containsAll(sampled.strategy().keySet()))
            throw new IllegalArgumentException(
                    "Saved strategy contains information sets outside game");
        return new Result(
                new CfrSolution(sampled.iterations(), walker.strategies),
                walker.strategies.size() - sampled.strategy().size(),
                walker.visited);
    }

    private static final class Walker<S> {
        private final MultiPlayerCfrGame<S> game;
        private final Map<String, Map<String, Double>> strategies;
        private final HashSet<String> encountered = new HashSet<>();
        private final long maximum;
        private long visited;

        private Walker(
                MultiPlayerCfrGame<S> game,
                Map<String, Map<String, Double>> initial,
                long maximum) {
            this.game = game;
            strategies = new LinkedHashMap<>(initial);
            this.maximum = maximum;
        }

        private void visit(S state) {
            if (++visited > maximum)
                throw new IllegalArgumentException("Profile completion exceeded state budget");
            if (game.isTerminal(state)) return;
            int player = game.currentPlayer(state);
            if (player == -1) {
                var outcomes = game.chanceOutcomes(state);
                if (outcomes.isEmpty()
                        || Math.abs(
                                        outcomes.stream()
                                                        .mapToDouble(ChanceOutcome::probability)
                                                        .sum()
                                                - 1)
                                > 1e-9)
                    throw new IllegalArgumentException("Chance probabilities must sum to one");
                for (var outcome : outcomes) visit(outcome.state());
                return;
            }
            if (player < 0 || player >= game.playerCount())
                throw new IllegalArgumentException("Invalid player index");
            List<String> legal = List.copyOf(game.legalActions(state));
            if (legal.isEmpty()
                    || legal.stream().anyMatch(a -> a == null || a.isBlank())
                    || new HashSet<>(legal).size() != legal.size())
                throw new IllegalArgumentException("Decision node needs distinct named actions");
            String informationSet = game.informationSet(state);
            if (informationSet == null || informationSet.isBlank())
                throw new IllegalArgumentException("Decision node needs an information set");
            String key = player + ":" + informationSet;
            encountered.add(key);
            var existing = strategies.get(key);
            if (existing == null) {
                var uniform = new LinkedHashMap<String, Double>();
                for (String action : legal) uniform.put(action, 1.0 / legal.size());
                strategies.put(key, Map.copyOf(uniform));
            } else {
                if (!existing.keySet().equals(new HashSet<>(legal))
                        || existing.values().stream()
                                .anyMatch(p -> !Double.isFinite(p) || p < 0 || p > 1)
                        || Math.abs(
                                        existing.values().stream()
                                                        .mapToDouble(Double::doubleValue)
                                                        .sum()
                                                - 1)
                                > 1e-9)
                    throw new IllegalArgumentException(
                            "Saved strategy disagrees with legal probabilities");
            }
            for (String action : legal) visit(game.afterAction(state, action));
        }
    }
}
