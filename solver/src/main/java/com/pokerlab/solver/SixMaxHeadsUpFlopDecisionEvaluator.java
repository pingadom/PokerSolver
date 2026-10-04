package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact conditional action EVs; own prior moves are forced and opponent moves update belief. */
public final class SixMaxHeadsUpFlopDecisionEvaluator {
    public record Decision(
            Seat actingSeat,
            String heroCombo,
            List<String> history,
            int compatibleJointDeals,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Decision {
            history = List.copyOf(history);
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }

        public double evLossBb(String action) {
            Double selected = actionEvBb.get(action);
            if (selected == null) throw new IllegalArgumentException("Illegal flop action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - selected);
        }
    }

    private record Reached(SixMaxHeadsUpFlopGame.State state, double logMass) {}

    private final SixMaxHeadsUpFlopGame game;
    private final CfrSolution solution;

    public SixMaxHeadsUpFlopDecisionEvaluator(SixMaxHeadsUpFlopGame game, CfrSolution solution) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
    }

    public Decision evaluate(List<String> history, String heroCombo) {
        history = List.copyOf(Objects.requireNonNull(history, "history"));
        Objects.requireNonNull(heroCombo, "heroCombo");
        var sample =
                game.replay(game.chanceOutcomes(game.initialState()).getFirst().state(), history);
        int player = game.currentPlayer(sample);
        List<Reached> reached = new ArrayList<>();
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            double logMass = Math.log(outcome.probability());
            for (String action : history) {
                if (game.currentPlayer(state) != player)
                    logMass += Math.log(strategy(state).get(action));
                state = game.afterAction(state, action);
            }
            if (game.ownHand(state).key().equals(heroCombo) && Double.isFinite(logMass))
                reached.add(new Reached(state, logMass));
        }
        if (reached.isEmpty())
            throw new IllegalArgumentException("Unknown or zero-reach flop decision");
        double maximum = reached.stream().mapToDouble(Reached::logMass).max().orElseThrow();
        double total =
                reached.stream().mapToDouble(node -> Math.exp(node.logMass() - maximum)).sum();
        Map<String, Double> evs = new LinkedHashMap<>();
        for (String action : game.legalActions(sample)) {
            double value = 0;
            for (var node : reached)
                value +=
                        Math.exp(node.logMass() - maximum)
                                / total
                                * continuation(game.afterAction(node.state(), action), player);
            evs.put(action, value + game.liveUtilityOffsetBb());
        }
        return new Decision(
                game.seat(player),
                heroCombo,
                history,
                reached.size(),
                strategy(reached.getFirst().state()),
                evs);
    }

    private double continuation(SixMaxHeadsUpFlopGame.State state, int player) {
        if (game.isTerminal(state)) return (player == 0 ? 1 : -1) * game.terminalUtility(state);
        double value = 0;
        for (var entry : strategy(state).entrySet())
            if (entry.getValue() > 0)
                value +=
                        entry.getValue()
                                * continuation(game.afterAction(state, entry.getKey()), player);
        return value;
    }

    private Map<String, Double> strategy(SixMaxHeadsUpFlopGame.State state) {
        return game.strategy(solution, state);
    }
}
