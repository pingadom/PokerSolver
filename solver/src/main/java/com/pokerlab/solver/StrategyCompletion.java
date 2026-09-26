package com.pokerlab.solver;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Completes a sparse chance-sampled profile with uniform actions at unseen information sets so a
 * bounded game can still receive an exact-game best-response audit. Full enumeration is deliberate
 * and guarded; it is not a scalable substitute for solving a physical full-deck game.
 */
public final class StrategyCompletion {
    public record Result(CfrSolution solution, int addedInformationSets, long visitedStates) {}

    private StrategyCompletion() {}

    public static <S> Result uniformAtUnseen(
            CfrGame<S> game, CfrSolution sampled, long maximumVisitedStates) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(sampled, "sampled");
        if (maximumVisitedStates < 1)
            throw new IllegalArgumentException("A positive enumeration budget is required");
        Walker<S> walker = new Walker<>(game, sampled.strategy(), maximumVisitedStates);
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
        private final CfrGame<S> game;
        private final Map<String, Map<String, Double>> strategies;
        private final HashSet<String> encountered = new HashSet<>();
        private final long maximum;
        private long visited;

        private Walker(CfrGame<S> game, Map<String, Map<String, Double>> initial, long maximum) {
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
                for (var outcome : game.chanceOutcomes(state)) visit(outcome.state());
                return;
            }
            if (player != 0 && player != 1)
                throw new IllegalArgumentException("Expected player 0, player 1 or chance");
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
            Map<String, Double> existing = strategies.get(key);
            if (existing == null) {
                Map<String, Double> uniform = new LinkedHashMap<>();
                for (String action : legal) uniform.put(action, 1.0 / legal.size());
                strategies.put(key, Map.copyOf(uniform));
            } else if (!existing.keySet().equals(new HashSet<>(legal))) {
                throw new IllegalArgumentException("Saved strategy disagrees with legal actions");
            }
            for (String action : legal) visit(game.afterAction(state, action));
        }
    }
}
