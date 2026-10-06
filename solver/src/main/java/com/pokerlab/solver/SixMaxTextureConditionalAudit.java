package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Reached-history/texture diagnostics; conditional gaps are distinct from parent NashConv. */
public final class SixMaxTextureConditionalAudit {
    public record Conditional(
            List<PublicAction> history,
            SixMaxTexturePayoffTable.Texture texture,
            Seat firstToAct,
            Seat secondToAct,
            double historyProbability,
            double textureProbabilityGivenHistory,
            int posteriorPrivateDeals,
            Map<String, Double> firstMarginal,
            Map<String, Double> secondMarginal,
            int firstCombosAtFivePercent,
            int secondCombosAtFivePercent,
            SixMaxConnectedPreflopAudit.Quality quality) {
        public Conditional {
            history = List.copyOf(history);
            firstMarginal = Map.copyOf(firstMarginal);
            secondMarginal = Map.copyOf(secondMarginal);
        }
    }

    private SixMaxTextureConditionalAudit() {}

    public static List<Conditional> assess(SixMaxTextureFlopGame game, CfrSolution complete) {
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game, complete, SixMaxTextureFlopGame.MAX_COMPLETE_STATES)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException(
                    "Conditional audit requires a complete parent policy");
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(complete);
        var table = game.payoffTable();
        var result = new ArrayList<Conditional>();
        for (var selection : game.selections()) {
            if (!hasReach(game.sourceGame(), preflop, selection.history())) continue;
            var transition =
                    new SixMaxPolicyFlopTransition(game.sourceGame(), preflop, selection.history());
            for (int texture = 0; texture < 6; texture++) {
                var states = new ArrayList<ChanceOutcome<SixMaxTextureFlopGame.State>>();
                var marginals =
                        List.of(
                                new LinkedHashMap<String, Double>(),
                                new LinkedHashMap<String, Double>());
                double probability = 0;
                for (var posterior : transition.deals()) {
                    var keys = posterior.hands().stream().map(WeightedCombo::key).toList();
                    int deal = -1;
                    for (int i = 0; i < table.deals().size(); i++)
                        if (table.deals().get(i).hands().equals(keys)) {
                            deal = i;
                            break;
                        }
                    if (deal < 0)
                        throw new IllegalArgumentException(
                                "Conditional table private support differs");
                    long flops = table.deals().get(deal).flopCounts().get(texture);
                    if (flops == 0) continue;
                    double mass = posterior.probability() * flops / 9880.0;
                    if (mass < Double.MIN_NORMAL)
                        throw new IllegalArgumentException(
                                "Conditional posterior underflow requires a log-space audit");
                    var state = game.chanceOutcomes(game.initialState()).get(deal).state();
                    for (var action : selection.history())
                        state = game.afterAction(state, action.action());
                    states.add(
                            new ChanceOutcome<>(
                                    new SixMaxTextureFlopGame.State(state.preflop(), texture, ""),
                                    mass));
                    probability += mass;
                    marginals
                            .get(0)
                            .merge(keys.get(transition.firstToAct().ordinal()), mass, Double::sum);
                    marginals
                            .get(1)
                            .merge(keys.get(transition.secondToAct().ordinal()), mass, Double::sum);
                }
                if (probability == 0) continue;
                double normalizer = probability;
                var normalized =
                        states.stream()
                                .map(
                                        s ->
                                                new ChanceOutcome<>(
                                                        s.state(), s.probability() / normalizer))
                                .toList();
                for (var marginal : marginals)
                    marginal.replaceAll((key, value) -> value / normalizer);
                var quality =
                        SixMaxConnectedPreflopAudit.Quality.of(
                                MultiPlayerInformationSetBestResponse.assess(
                                        new ConditionalGame(game, normalized), complete));
                result.add(
                        new Conditional(
                                selection.history(),
                                SixMaxTexturePayoffTable.Texture.values()[texture],
                                transition.firstToAct(),
                                transition.secondToAct(),
                                transition.reachProbability(),
                                probability,
                                states.size(),
                                marginals.get(0),
                                marginals.get(1),
                                material(marginals.get(0)),
                                material(marginals.get(1)),
                                quality));
            }
        }
        return List.copyOf(result);
    }

    static boolean hasReach(
            SixMaxPreflopCheckdownGame game, CfrSolution preflop, List<PublicAction> history) {
        for (var root : game.chanceOutcomes(game.initialState())) {
            var state = root.state();
            boolean positive = true;
            for (var action : history) {
                positive &=
                        MultiPlayerStrategyEvaluator.probability(
                                        game, preflop, state, action.action())
                                > 0;
                state = game.afterAction(state, action.action());
            }
            if (positive) return true;
        }
        return false;
    }

    private static int material(Map<String, Double> marginal) {
        return (int) marginal.values().stream().filter(mass -> mass >= .05).count();
    }

    private record ConditionalGame(
            SixMaxTextureFlopGame parent, List<ChanceOutcome<SixMaxTextureFlopGame.State>> roots)
            implements MultiPlayerCfrGame<SixMaxTextureFlopGame.State> {
        @Override
        public int playerCount() {
            return 6;
        }

        @Override
        public SixMaxTextureFlopGame.State initialState() {
            return parent.initialState();
        }

        @Override
        public boolean isTerminal(SixMaxTextureFlopGame.State state) {
            return !state.equals(initialState()) && parent.isTerminal(state);
        }

        @Override
        public int currentPlayer(SixMaxTextureFlopGame.State state) {
            return state.equals(initialState()) ? -1 : parent.currentPlayer(state);
        }

        @Override
        public List<String> legalActions(SixMaxTextureFlopGame.State state) {
            return state.equals(initialState()) ? List.of() : parent.legalActions(state);
        }

        @Override
        public String informationSet(SixMaxTextureFlopGame.State state) {
            return parent.informationSet(state);
        }

        @Override
        public SixMaxTextureFlopGame.State afterAction(
                SixMaxTextureFlopGame.State state, String action) {
            return parent.afterAction(state, action);
        }

        @Override
        public List<ChanceOutcome<SixMaxTextureFlopGame.State>> chanceOutcomes(
                SixMaxTextureFlopGame.State state) {
            if (!state.equals(initialState()))
                throw new IllegalArgumentException("Only posterior root is a chance node");
            return roots;
        }

        @Override
        public double[] terminalUtilities(SixMaxTextureFlopGame.State state) {
            return parent.terminalUtilities(state);
        }

        @Override
        public OptionalDouble inactivePlayerUtility(SixMaxTextureFlopGame.State state, int player) {
            return state.equals(initialState())
                    ? OptionalDouble.empty()
                    : parent.inactivePlayerUtility(state, player);
        }
    }
}
