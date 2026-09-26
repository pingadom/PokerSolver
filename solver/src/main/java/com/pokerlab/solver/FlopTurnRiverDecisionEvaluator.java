package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Conditional action EVs at a saved full-deck flop, turn or river decision. */
public final class FlopTurnRiverDecisionEvaluator {
    public record Decision(
            String packHash,
            int actingPlayer,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory,
            String heroCombo,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Decision {
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }

        public double evLossBb(String action) {
            Double selected = actionEvBb.get(action);
            if (selected == null) throw new IllegalArgumentException("Illegal flop-game action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - selected);
        }
    }

    private final FlopTurnRiverSolutionPack pack;
    private final FlopTurnRiverGame game;
    private final String packHash;

    public FlopTurnRiverDecisionEvaluator(FlopTurnRiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        game = pack.spot().game();
        packHash = FlopTurnRiverPackJson.contentHash(pack);
    }

    /** Hero's prior actions are forced; opponent actions and public cards update the posterior. */
    public Decision evaluate(
            int actingPlayer,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory,
            String heroCombo) {
        if (actingPlayer != 0 && actingPlayer != 1)
            throw new IllegalArgumentException("Unknown acting player");
        Objects.requireNonNull(flopHistory, "flopHistory");
        Objects.requireNonNull(turnHistory, "turnHistory");
        Objects.requireNonNull(riverHistory, "riverHistory");
        Objects.requireNonNull(heroCombo, "heroCombo");
        if (turn == null && (!turnHistory.isEmpty() || river != null || !riverHistory.isEmpty()))
            throw new IllegalArgumentException("Later street requires a turn card");
        if (river == null && !riverHistory.isEmpty())
            throw new IllegalArgumentException("River history requires a river card");
        double denominator = 0;
        Map<String, Double> totals = new LinkedHashMap<>();
        FlopTurnRiverGame.State example = null;
        for (ChanceOutcome<FlopTurnRiverGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            FlopTurnRiverGame.State dealt = outcome.state();
            WeightedCombo hero = actingPlayer == 0 ? dealt.first() : dealt.second();
            if (!hero.key().equals(heroCombo)) continue;
            double reach = outcome.probability();
            FlopTurnRiverGame.State node = dealt;
            Replay flop = replay(node, flopHistory, actingPlayer, reach);
            node = flop.state();
            reach = flop.reach();
            if (turn != null) {
                ChanceOutcome<FlopTurnRiverGame.State> card = publicCard(node, turn, true);
                if (card == null) continue;
                node = card.state();
                reach *= card.probability();
                Replay turnReplay = replay(node, turnHistory, actingPlayer, reach);
                node = turnReplay.state();
                reach = turnReplay.reach();
            }
            if (river != null) {
                ChanceOutcome<FlopTurnRiverGame.State> card = publicCard(node, river, false);
                if (card == null) continue;
                node = card.state();
                reach *= card.probability();
                Replay riverReplay = replay(node, riverHistory, actingPlayer, reach);
                node = riverReplay.state();
                reach = riverReplay.reach();
            }
            if (game.isTerminal(node) || game.currentPlayer(node) != actingPlayer)
                throw new IllegalArgumentException("History does not lead to the requested player");
            example = node;
            if (reach == 0) continue;
            denominator += reach;
            for (String action : game.legalActions(node))
                totals.merge(
                        action,
                        reach * continuation(game.afterAction(node, action), actingPlayer),
                        Double::sum);
        }
        if (example == null || denominator <= 0)
            throw new IllegalArgumentException("Unknown or unreachable flop-game decision");
        Map<String, Double> evs = new LinkedHashMap<>();
        for (String action : game.legalActions(example))
            evs.put(action, totals.get(action) / denominator);
        Map<String, Double> strategy =
                pack.solution().at(actingPlayer, game.informationSet(example));
        return new Decision(
                packHash,
                actingPlayer,
                flopHistory,
                turn,
                turnHistory,
                river,
                riverHistory,
                heroCombo,
                strategy,
                evs);
    }

    private record Replay(FlopTurnRiverGame.State state, double reach) {}

    private Replay replay(
            FlopTurnRiverGame.State start, String history, int actingPlayer, double startingReach) {
        FlopTurnRiverGame.State node = start;
        double reach = startingReach;
        for (int index = 0; index < history.length(); index++) {
            String action = String.valueOf(history.charAt(index));
            if (game.isTerminal(node)
                    || game.currentPlayer(node) == -1
                    || !game.legalActions(node).contains(action))
                throw new IllegalArgumentException("Invalid public betting history");
            if (game.currentPlayer(node) != actingPlayer) reach *= probability(node, action);
            node = game.afterAction(node, action);
        }
        return new Replay(node, reach);
    }

    private ChanceOutcome<FlopTurnRiverGame.State> publicCard(
            FlopTurnRiverGame.State state, Card card, boolean isTurn) {
        if (game.isTerminal(state) || game.currentPlayer(state) != -1)
            throw new IllegalArgumentException("Public card requires completed betting");
        for (ChanceOutcome<FlopTurnRiverGame.State> candidate : game.chanceOutcomes(state)) {
            Card dealt = isTurn ? candidate.state().turn() : candidate.state().river();
            if (dealt.equals(card)) return candidate;
        }
        return null; // Public card blocks this private deal.
    }

    private double continuation(FlopTurnRiverGame.State state, int target) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            return target == 0 ? utility : -utility;
        }
        if (game.currentPlayer(state) == -1) {
            double utility = 0;
            for (ChanceOutcome<FlopTurnRiverGame.State> outcome : game.chanceOutcomes(state))
                utility += outcome.probability() * continuation(outcome.state(), target);
            return utility;
        }
        double utility = 0;
        for (String action : game.legalActions(state))
            utility +=
                    probability(state, action)
                            * continuation(game.afterAction(state, action), target);
        return utility;
    }

    private double probability(FlopTurnRiverGame.State state, String action) {
        return pack.solution()
                .at(game.currentPlayer(state), game.informationSet(state))
                .get(action);
    }
}
