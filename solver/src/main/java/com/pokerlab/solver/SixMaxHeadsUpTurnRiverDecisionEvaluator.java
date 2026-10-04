package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Conditional turn/river EVs with own actions forced and public cards/opponent actions observed.
 */
public final class SixMaxHeadsUpTurnRiverDecisionEvaluator {
    public record Decision(
            Seat actingSeat,
            String heroCombo,
            List<String> turnHistory,
            String river,
            List<String> riverHistory,
            int compatibleJointDeals,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Decision {
            turnHistory = List.copyOf(turnHistory);
            riverHistory = List.copyOf(riverHistory);
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }

        public double evLossBb(String action) {
            Double chosen = actionEvBb.get(action);
            if (chosen == null) throw new IllegalArgumentException("Illegal action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - chosen);
        }
    }

    private record Reached(SixMaxHeadsUpTurnRiverGame.State state, double logMass) {}

    private final SixMaxHeadsUpTurnRiverGame game;
    private final CfrSolution solution;

    public SixMaxHeadsUpTurnRiverDecisionEvaluator(
            SixMaxHeadsUpTurnRiverGame game, CfrSolution solution) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
    }

    public Decision evaluate(
            List<String> turnHistory, Card river, List<String> riverHistory, String heroCombo) {
        turnHistory = List.copyOf(turnHistory);
        riverHistory = List.copyOf(riverHistory);
        Objects.requireNonNull(heroCombo, "heroCombo");
        var roots = game.chanceOutcomes(game.initialState());
        List<Reached> reached = new ArrayList<>();
        for (var outcome : roots) {
            if (river != null
                    && !game.turn().undealtCards(outcome.state().dealIndex()).contains(river))
                continue;
            var target = game.replay(outcome.state(), turnHistory, river, riverHistory);
            int player = game.currentPlayer(target);
            if (player == -1) throw new IllegalArgumentException("History ends at a chance node");
            if (!game.ownHand(target).key().equals(heroCombo)) continue;
            var state = outcome.state();
            double logMass = Math.log(outcome.probability());
            for (String action : turnHistory) {
                if (game.currentPlayer(state) != player)
                    logMass += Math.log(game.strategy(solution, state).get(action));
                state = game.afterAction(state, action);
            }
            if (river != null) {
                // All six hands remain dealt: every compatible public river has probability 1/36.
                logMass -= Math.log(36);
                state = game.replay(outcome.state(), turnHistory, river, List.of());
            }
            for (String action : riverHistory) {
                if (game.currentPlayer(state) != player)
                    logMass += Math.log(game.strategy(solution, state).get(action));
                state = game.afterAction(state, action);
            }
            if (Double.isFinite(logMass)) reached.add(new Reached(state, logMass));
        }
        if (reached.isEmpty())
            throw new IllegalArgumentException("Unknown or zero-reach turn/river decision");
        double maximum = reached.stream().mapToDouble(Reached::logMass).max().orElseThrow();
        double total = reached.stream().mapToDouble(n -> Math.exp(n.logMass() - maximum)).sum();
        var sample = reached.getFirst().state();
        int player = game.currentPlayer(sample);
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
                turnHistory,
                river == null ? null : river.compact(),
                riverHistory,
                reached.size(),
                game.strategy(solution, sample),
                evs);
    }

    private double continuation(SixMaxHeadsUpTurnRiverGame.State state, int player) {
        if (game.isTerminal(state)) return (player == 0 ? 1 : -1) * game.terminalUtility(state);
        double value = 0;
        if (game.currentPlayer(state) == -1) {
            for (var outcome : game.chanceOutcomes(state))
                value += outcome.probability() * continuation(outcome.state(), player);
        } else {
            for (var entry : game.strategy(solution, state).entrySet())
                if (entry.getValue() > 0)
                    value +=
                            entry.getValue()
                                    * continuation(game.afterAction(state, entry.getKey()), player);
        }
        return value;
    }
}
