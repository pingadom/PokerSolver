package com.pokerlab.solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Conditional action EVs at a saved river information set, with opponent cards hidden. */
public final class RiverDecisionEvaluator {
    public record Decision(
            String packHash,
            int actingPlayer,
            String publicHistory,
            String heroCombo,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Decision {
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }

        public double evLossBb(String action) {
            Double selected = actionEvBb.get(action);
            if (selected == null) throw new IllegalArgumentException("Illegal river action");
            return Math.max(
                    0,
                    actionEvBb.values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .max()
                                    .orElseThrow()
                            - selected);
        }
    }

    private final RiverSolutionPack pack;
    private final RiverBetGame game;
    private final String packHash;

    public RiverDecisionEvaluator(RiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        game = pack.spot().game();
        packHash = RiverPackJson.contentHash(pack);
    }

    public Decision evaluate(int actingPlayer, String publicHistory, String heroCombo) {
        if (actingPlayer != 0 && actingPlayer != 1)
            throw new IllegalArgumentException("Unknown acting player");
        Objects.requireNonNull(publicHistory, "publicHistory");
        Objects.requireNonNull(heroCombo, "heroCombo");
        double denominator = 0;
        Map<String, Double> totals = new LinkedHashMap<>();
        RiverBetGame.State example = null;
        for (ChanceOutcome<RiverBetGame.State> outcome : game.chanceOutcomes(game.initialState())) {
            RiverBetGame.State dealt = outcome.state();
            WeightedCombo hero = actingPlayer == 0 ? dealt.first() : dealt.second();
            if (!hero.key().equals(heroCombo)) continue;
            RiverBetGame.State node = dealt;
            double reach = outcome.probability();
            for (int index = 0; index < publicHistory.length(); index++) {
                String action = String.valueOf(publicHistory.charAt(index));
                if (!game.legalActions(node).contains(action))
                    throw new IllegalArgumentException("Invalid public river history");
                int player = game.currentPlayer(node);
                if (player != actingPlayer) reach *= probability(player, node, action);
                node = game.afterAction(node, action);
            }
            if (game.isTerminal(node) || game.currentPlayer(node) != actingPlayer)
                throw new IllegalArgumentException("History does not lead to the requested player");
            example = node;
            if (reach == 0) continue;
            denominator += reach;
            for (String action : game.legalActions(node)) {
                double value = continuation(game.afterAction(node, action), actingPlayer);
                totals.merge(action, reach * value, Double::sum);
            }
        }
        if (example == null || denominator <= 0)
            throw new IllegalArgumentException("Unknown or unreachable river decision");
        Map<String, Double> evs = new LinkedHashMap<>();
        for (String action : game.legalActions(example))
            evs.put(action, totals.get(action) / denominator);
        Map<String, Double> strategy =
                pack.solution().at(actingPlayer, game.informationSet(example));
        return new Decision(packHash, actingPlayer, publicHistory, heroCombo, strategy, evs);
    }

    private double continuation(RiverBetGame.State state, int target) {
        if (game.isTerminal(state))
            return target == 0 ? game.terminalUtility(state) : -game.terminalUtility(state);
        int player = game.currentPlayer(state);
        double utility = 0;
        for (String action : game.legalActions(state)) {
            utility +=
                    probability(player, state, action)
                            * continuation(game.afterAction(state, action), target);
        }
        return utility;
    }

    private double probability(int player, RiverBetGame.State state, String action) {
        return pack.solution().at(player, game.informationSet(state)).get(action);
    }
}
