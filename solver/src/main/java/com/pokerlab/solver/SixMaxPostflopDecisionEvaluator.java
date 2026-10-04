package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Public replay and conditional action EVs within the connected game's declared chance model. */
public final class SixMaxPostflopDecisionEvaluator {
    public record History(
            List<String> flopActions,
            Card turn,
            List<String> turnActions,
            Card river,
            List<String> riverActions) {
        public History {
            flopActions = List.copyOf(flopActions);
            turnActions = List.copyOf(turnActions);
            riverActions = List.copyOf(riverActions);
            if (turn == null && (!turnActions.isEmpty() || river != null || !riverActions.isEmpty())
                    || river == null && !riverActions.isEmpty())
                throw new IllegalArgumentException("Actions require their public street card");
        }

        public static History flop(List<String> actions) {
            return new History(actions, null, List.of(), null, List.of());
        }
    }

    public record Decision(
            Seat actingSeat,
            String heroCombo,
            String street,
            int compatibleJointDeals,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Decision {
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }

        public double evLossBb(String action) {
            Double value = actionEvBb.get(action);
            if (value == null) throw new IllegalArgumentException("Illegal action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - value);
        }
    }

    private record Reached(SixMaxHeadsUpPostflopGame.State state, double logMass) {}

    private final SixMaxHeadsUpPostflopGame game;
    private final CfrSolution solution;

    public SixMaxPostflopDecisionEvaluator(SixMaxHeadsUpPostflopGame game, CfrSolution solution) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
    }

    public Decision evaluate(History history, String heroCombo) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(heroCombo, "heroCombo");
        var candidates = new ArrayList<Reached>();
        // Replay chance first to find compatible deals. Each card uses its declared probability,
        // including duplicated quantile outcomes, rather than assuming a physical 1/37 turn.
        for (var root : game.chanceOutcomes(game.initialState())) {
            var replayed = replay(root.state(), history, -1);
            if (replayed == null) continue;
            int player = game.currentPlayer(replayed.state());
            if (player == -1) throw new IllegalArgumentException("History ends at chance");
            if (!game.ownHand(replayed.state()).key().equals(heroCombo)) continue;
            var reached = replay(root.state(), history, player);
            double mass = Math.log(root.probability()) + reached.logMass();
            if (Double.isFinite(mass)) candidates.add(new Reached(reached.state(), mass));
        }
        if (candidates.isEmpty())
            throw new IllegalArgumentException("Unknown or zero-reach connected decision");
        double maximum = candidates.stream().mapToDouble(Reached::logMass).max().orElseThrow();
        double total = candidates.stream().mapToDouble(n -> Math.exp(n.logMass() - maximum)).sum();
        var sample = candidates.getFirst().state();
        int player = game.currentPlayer(sample);
        var evs = new LinkedHashMap<String, Double>();
        for (String action : game.legalActions(sample)) {
            double value = 0;
            for (var node : candidates)
                value +=
                        Math.exp(node.logMass() - maximum)
                                / total
                                * (player == 0 ? 1 : -1)
                                * game.continuation(
                                        solution, game.afterAction(node.state(), action));
            evs.put(action, value + game.liveUtilityOffsetBb());
        }
        return new Decision(
                game.seat(player),
                heroCombo,
                sample.turn() == null ? "FLOP" : sample.river() == null ? "TURN" : "RIVER",
                candidates.size(),
                game.strategy(solution, sample),
                evs);
    }

    private Reached replay(SixMaxHeadsUpPostflopGame.State root, History history, int target) {
        var node = new Reached(root, 0);
        node = actions(node, history.flopActions(), target);
        if (history.turn() != null) {
            node = observe(node, history.turn());
            if (node == null) return null;
        }
        node = actions(node, history.turnActions(), target);
        if (history.river() != null) {
            node = observe(node, history.river());
            if (node == null) return null;
        }
        return actions(node, history.riverActions(), target);
    }

    private Reached actions(Reached node, List<String> actions, int target) {
        var state = node.state();
        double mass = node.logMass();
        for (String action : actions) {
            if (!game.legalActions(state).contains(action))
                throw new IllegalArgumentException("Illegal public action");
            if (target >= 0 && game.currentPlayer(state) != target)
                mass += Math.log(game.strategy(solution, state).get(action));
            state = game.afterAction(state, action);
        }
        return new Reached(state, mass);
    }

    private Reached observe(Reached node, Card card) {
        for (var outcome : game.chanceOutcomes(node.state())) {
            var state = outcome.state();
            Card shown = node.state().turn() == null ? state.turn() : state.river();
            if (shown.equals(card))
                return new Reached(state, node.logMass() + Math.log(outcome.probability()));
        }
        return null;
    }
}
