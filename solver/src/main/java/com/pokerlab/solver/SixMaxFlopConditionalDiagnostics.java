package com.pokerlab.solver;

import static com.pokerlab.solver.SixMaxRankTextureConditionalAudit.GAP_THRESHOLD_BB;
import static com.pokerlab.solver.SixMaxRankTextureConditionalAudit.MARGINAL_THRESHOLD;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import com.pokerlab.solver.SixMaxRankTextureConditionalAudit.ParentWitness;
import com.pokerlab.solver.SixMaxRankTextureConditionalAudit.Summary;
import java.util.*;

/**
 * Same conditional/posterior and unilateral-witness algorithm for separately bound observations.
 */
public final class SixMaxFlopConditionalDiagnostics {
    public record Case(
            int observation,
            String observationKey,
            String status,
            double signalProbabilityGivenHistory,
            int posteriorPrivateDeals,
            Map<String, Double> firstMarginal,
            Map<String, Double> secondMarginal,
            int firstCombosAtFivePercent,
            int secondCombosAtFivePercent,
            SixMaxConnectedPreflopAudit.Quality quality) {
        public Case {
            firstMarginal = Map.copyOf(firstMarginal);
            secondMarginal = Map.copyOf(secondMarginal);
        }
    }

    public record History(
            List<PublicAction> history,
            String status,
            Seat firstToAct,
            Seat secondToAct,
            double historyProbability,
            double signalProbabilitiesSum,
            List<Case> signals) {
        public History {
            history = List.copyOf(history);
            signals = List.copyOf(signals);
        }
    }

    public record Result(List<History> histories, Summary summary, ParentWitness parentWitness) {
        public Result {
            histories = List.copyOf(histories);
        }
    }

    record Posterior(
            double signalProbability,
            List<ChanceOutcome<SixMaxRankTextureFlopGame.State>> roots,
            Map<String, Double> firstMarginal,
            Map<String, Double> secondMarginal) {}

    private SixMaxFlopConditionalDiagnostics() {}

    static Result assess(SixMaxOneBetFlopGame game, CfrSolution policy) throws Exception {
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game, policy, SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException("Conditional audit requires complete parent policy");
        var view = game.payoffView();
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(policy);
        var responses = new ArrayList<Map<String, String>>();
        for (int player = 0; player < 6; player++) responses.add(new LinkedHashMap<>());
        double[] weighted = new double[6];
        var histories = new ArrayList<History>();
        int audited = 0, zero = 0, unsupported = 0, above = 0, diverse = 0;
        double largest = 0, diverseLargest = 0;
        for (var selection : game.selections()) {
            if (!SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), preflop, selection.history())) {
                zero++;
                histories.add(
                        new History(
                                selection.history(),
                                "ZERO_POLICY_REACH",
                                null,
                                null,
                                0,
                                0,
                                List.of()));
                continue;
            }
            var transition =
                    new SixMaxPolicyFlopTransition(game.sourceGame(), preflop, selection.history());
            requireNormal(transition.reachProbability());
            for (var deal : transition.deals()) requireNormal(deal.probability());
            var signals = new ArrayList<Case>();
            double probabilitySum = 0;
            for (int signal = 0; signal < view.counts(0).size(); signal++) {
                var posterior = posterior(game, transition, signal);
                double probability = posterior.signalProbability();
                probabilitySum += probability;
                if (posterior.roots().isEmpty()) {
                    unsupported++;
                    signals.add(
                            new Case(
                                    signal,
                                    view.key(signal),
                                    "NO_REACHED_PRIVATE_SUPPORT",
                                    0,
                                    0,
                                    Map.of(),
                                    Map.of(),
                                    0,
                                    0,
                                    null));
                    continue;
                }
                var response =
                        MultiPlayerInformationSetBestResponse.assess(
                                new SixMaxRankTextureConditionalAudit.ConditionalGame(
                                        game, posterior.roots()),
                                policy);
                double weight = transition.reachProbability() * probability;
                requireNormal(weight);
                for (int player = 0; player < 6; player++) {
                    weighted[player] += weight * response.deviationGainsBb().get(player);
                    for (var action : response.responseActions().get(player).entrySet()) {
                        String key = player + ":" + action.getKey();
                        // No preflop action may change, and cases must have disjoint information
                        // sets.
                        if (!key.contains(":postflop:" + view.namespace() + ":")
                                || responses.get(player).putIfAbsent(key, action.getValue())
                                        != null)
                            throw new IllegalStateException(
                                    "Conditional responses overlap or change preflop");
                    }
                }
                var quality = SixMaxConnectedPreflopAudit.Quality.of(response);
                int first = material(posterior.firstMarginal());
                int second = material(posterior.secondMarginal());
                audited++;
                if (quality.nashConvBb() > GAP_THRESHOLD_BB) above++;
                largest = Math.max(largest, quality.nashConvBb());
                if (first >= 2 && second >= 2) {
                    diverse++;
                    diverseLargest = Math.max(diverseLargest, quality.nashConvBb());
                }
                signals.add(
                        new Case(
                                signal,
                                view.key(signal),
                                "AUDITED",
                                probability,
                                posterior.roots().size(),
                                posterior.firstMarginal(),
                                posterior.secondMarginal(),
                                first,
                                second,
                                quality));
            }
            if (Math.abs(probabilitySum - 1) > 1e-12)
                throw new IllegalStateException(
                        "Conditional signals do not partition reached chance");
            histories.add(
                    new History(
                            selection.history(),
                            "AUDITED",
                            transition.firstToAct(),
                            transition.secondToAct(),
                            transition.reachProbability(),
                            probabilitySum,
                            signals));
        }
        var witness = embed(game, policy, weighted, responses);
        return new Result(
                histories,
                new Summary(audited, zero, unsupported, above, diverse, largest, diverseLargest),
                witness);
    }

    private static ParentWitness embed(
            SixMaxOneBetFlopGame game,
            CfrSolution policy,
            double[] weighted,
            List<Map<String, String>> responses)
            throws Exception {
        var parent = MultiPlayerInformationSetBestResponse.assess(game, policy);
        var weightedGains = new ArrayList<Double>();
        var utilities = new ArrayList<Double>();
        var gains = new ArrayList<Double>();
        var errors = new ArrayList<Double>();
        var counts = new ArrayList<Integer>();
        var hashes = new ArrayList<String>();
        double total = 0;
        for (int player = 0; player < 6; player++) {
            var response = responses.get(player);
            var rows = new LinkedHashMap<>(policy.strategy());
            for (var action : response.entrySet()) {
                var original = rows.get(action.getKey());
                if (original == null || !original.containsKey(action.getValue()))
                    throw new IllegalStateException(
                            "Conditional response has a foreign or illegal action");
                var pure = new LinkedHashMap<String, Double>();
                original.forEach(
                        (key, value) -> pure.put(key, key.equals(action.getValue()) ? 1.0 : 0.0));
                rows.put(action.getKey(), pure);
            }
            double value =
                    response.isEmpty()
                            ? parent.profileUtilitiesBb().get(player)
                            : MultiPlayerStrategyEvaluator.utilities(
                                    game, new CfrSolution(policy.iterations(), rows))[player];
            double gain = value - parent.profileUtilitiesBb().get(player);
            double error = gain - weighted[player];
            if (Math.abs(error) > 1e-9
                    || gain < -1e-9
                    || gain > parent.deviationGainsBb().get(player) + 1e-9)
                throw new IllegalStateException(
                        "Embedded local responses disagree with parent bounds");
            weightedGains.add(weighted[player]);
            total += weighted[player];
            utilities.add(value);
            gains.add(gain);
            errors.add(error);
            counts.add(response.size());
            hashes.add(
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    SixMaxTexturePayoffTable.mapper()
                                                            .writeValueAsBytes(response))));
        }
        return new ParentWitness(
                SixMaxConnectedPreflopAudit.Quality.of(parent),
                weightedGains,
                total,
                utilities,
                gains,
                errors,
                counts,
                hashes);
    }

    static Posterior posterior(
            SixMaxOneBetFlopGame game, SixMaxPolicyFlopTransition transition, int signal) {
        var view = game.payoffView();
        var roots = game.chanceOutcomes(game.initialState());
        var states = new ArrayList<ChanceOutcome<SixMaxRankTextureFlopGame.State>>();
        var first = new LinkedHashMap<String, Double>();
        var second = new LinkedHashMap<String, Double>();
        double probability = 0;
        for (var posterior : transition.deals()) {
            var keys = posterior.hands().stream().map(WeightedCombo::key).toList();
            int deal = -1;
            for (int i = 0; i < view.dealCount(); i++)
                if (view.hands(i).equals(keys)) {
                    deal = i;
                    break;
                }
            if (deal < 0)
                throw new IllegalArgumentException("Conditional table private support differs");
            long flops = view.counts(deal).get(signal);
            if (flops == 0) continue;
            double mass =
                    posterior.probability() * flops / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL;
            requireNormal(mass);
            var state = roots.get(deal).state();
            for (var action : transition.history())
                state = game.afterAction(state, action.action());
            states.add(
                    new ChanceOutcome<>(
                            new SixMaxRankTextureFlopGame.State(state.preflop(), signal, ""),
                            mass));
            probability += mass;
            first.merge(keys.get(transition.firstToAct().ordinal()), mass, Double::sum);
            second.merge(keys.get(transition.secondToAct().ordinal()), mass, Double::sum);
        }
        if (probability == 0) return new Posterior(0, List.of(), Map.of(), Map.of());
        requireNormal(probability);
        double normalizer = probability;
        var normalized =
                states.stream()
                        .map(s -> new ChanceOutcome<>(s.state(), s.probability() / normalizer))
                        .toList();
        first.replaceAll((key, value) -> value / normalizer);
        second.replaceAll((key, value) -> value / normalizer);
        return new Posterior(probability, normalized, first, second);
    }

    private static void requireNormal(double probability) {
        if (!Double.isFinite(probability) || probability < Double.MIN_NORMAL)
            throw new IllegalArgumentException(
                    "Conditional reach underflow requires a log-space audit");
    }

    private static int material(Map<String, Double> marginal) {
        return (int) marginal.values().stream().filter(mass -> mass >= MARGINAL_THRESHOLD).count();
    }
}
