package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Counterfactual action EVs at a saved turn or river information set. */
public final class TurnRiverDecisionEvaluator {
    public record Decision(
            String packHash,
            int actingPlayer,
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
            if (selected == null) throw new IllegalArgumentException("Illegal turn-river action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - selected);
        }
    }

    private final TurnRiverSolutionPack pack;
    private final TurnRiverGame game;
    private final String packHash;

    public TurnRiverDecisionEvaluator(TurnRiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        game = pack.spot().game();
        packHash = TurnRiverPackJson.contentHash(pack);
    }

    /**
     * A null river requests a turn decision and requires an empty river history. A non-null river
     * requests a river decision after a completed turn history. The hero's prior actions are held
     * fixed; observed opponent actions and public chance update the hidden-card posterior.
     */
    public Decision evaluate(
            int actingPlayer,
            String turnHistory,
            Card river,
            String riverHistory,
            String heroCombo) {
        if (actingPlayer != 0 && actingPlayer != 1)
            throw new IllegalArgumentException("Unknown acting player");
        Objects.requireNonNull(turnHistory, "turnHistory");
        Objects.requireNonNull(riverHistory, "riverHistory");
        Objects.requireNonNull(heroCombo, "heroCombo");
        if (river == null && !riverHistory.isEmpty())
            throw new IllegalArgumentException("River history requires a river card");
        double denominator = 0;
        Map<String, Double> totals = new LinkedHashMap<>();
        TurnRiverGame.State example = null;
        for (ChanceOutcome<TurnRiverGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            TurnRiverGame.State dealt = outcome.state();
            WeightedCombo hero = actingPlayer == 0 ? dealt.first() : dealt.second();
            if (!hero.key().equals(heroCombo)) continue;
            double reach = outcome.probability();
            TurnRiverGame.State node = dealt;
            for (int index = 0; index < turnHistory.length(); index++) {
                String action = String.valueOf(turnHistory.charAt(index));
                if (game.isTerminal(node)
                        || game.currentPlayer(node) == -1
                        || !game.legalActions(node).contains(action))
                    throw new IllegalArgumentException("Invalid public turn history");
                if (game.currentPlayer(node) != actingPlayer) reach *= probability(node, action);
                node = game.afterAction(node, action);
            }
            if (river != null) {
                if (game.isTerminal(node) || game.currentPlayer(node) != -1)
                    throw new IllegalArgumentException("River requires completed turn betting");
                TurnRiverGame.State riverNode = null;
                for (ChanceOutcome<TurnRiverGame.State> candidate : game.chanceOutcomes(node)) {
                    if (candidate.state().river().equals(river)) {
                        riverNode = candidate.state();
                        reach *= candidate.probability();
                        break;
                    }
                }
                if (riverNode == null) continue; // The public river blocks this private deal.
                node = riverNode;
                for (int index = 0; index < riverHistory.length(); index++) {
                    String action = String.valueOf(riverHistory.charAt(index));
                    if (game.isTerminal(node) || !game.legalActions(node).contains(action))
                        throw new IllegalArgumentException("Invalid public river history");
                    if (game.currentPlayer(node) != actingPlayer)
                        reach *= probability(node, action);
                    node = game.afterAction(node, action);
                }
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
            throw new IllegalArgumentException("Unknown or unreachable turn-river decision");
        Map<String, Double> evs = new LinkedHashMap<>();
        for (String action : game.legalActions(example))
            evs.put(action, totals.get(action) / denominator);
        Map<String, Double> strategy =
                pack.solution().at(actingPlayer, game.informationSet(example));
        return new Decision(
                packHash, actingPlayer, turnHistory, river, riverHistory, heroCombo, strategy, evs);
    }

    private double continuation(TurnRiverGame.State state, int target) {
        if (game.isTerminal(state))
            return target == 0 ? game.terminalUtility(state) : -game.terminalUtility(state);
        if (game.currentPlayer(state) == -1) {
            double utility = 0;
            for (ChanceOutcome<TurnRiverGame.State> outcome : game.chanceOutcomes(state))
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

    private double probability(TurnRiverGame.State state, String action) {
        return pack.solution()
                .at(game.currentPlayer(state), game.informationSet(state))
                .get(action);
    }
}
