package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Same-budget six-player comparison for a sparse, explicitly declared continuation game. */
public final class SixMaxConnectedPreflopAudit {
    public record Quality(
            List<Double> profileUtilitiesBb,
            List<Double> bestResponseUtilitiesBb,
            List<Double> deviationGainsBb,
            double nashConvBb) {
        public Quality {
            profileUtilitiesBb = List.copyOf(profileUtilitiesBb);
            bestResponseUtilitiesBb = List.copyOf(bestResponseUtilitiesBb);
            deviationGainsBb = List.copyOf(deviationGainsBb);
        }

        static Quality of(MultiPlayerInformationSetBestResponse.Report report) {
            return new Quality(
                    report.profileUtilitiesBb(),
                    report.bestResponseUtilitiesBb(),
                    report.deviationGainsBb(),
                    report.nashConvBb());
        }
    }

    public record Solve(
            int iterations,
            int informationSets,
            String solutionHash,
            Quality quality,
            double bettingContinuationProbability,
            Map<String, Map<String, Double>> openingStrategies) {
        public Solve {
            var copy = new LinkedHashMap<String, Map<String, Double>>();
            openingStrategies.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
            openingStrategies = Map.copyOf(copy);
        }
    }

    public record ConditionalPostflop(
            List<String> flop,
            double preflopReachProbability,
            double flopProbabilityGivenHistory,
            int posteriorJointDeals,
            HeadsUpBestResponse.Report bestResponse,
            List<SixMaxPostflopDecisionEvaluator.Decision> firstPlayerDecisions) {
        public ConditionalPostflop {
            flop = List.copyOf(flop);
            firstPlayerDecisions = List.copyOf(firstPlayerDecisions);
        }
    }

    public record Report(
            String continuationModel,
            String privateChanceModel,
            long seed,
            List<SixMaxConnectedPreflopGame.Coverage> coverage,
            int sourceIterations,
            double sourceBettingContinuationProbability,
            Solve sameBudgetCheckdown,
            Quality checkdownProfileInConnectedGame,
            List<Double> forcedCheckdownRecoveryErrorBb,
            Solve jointlySolved,
            double maximumPreflopActionFrequencyChange,
            List<Double> jointMinusCheckdownUtilitiesBb,
            List<ConditionalPostflop> jointlySolvedConditionalPostflop) {
        public Report {
            coverage = List.copyOf(coverage);
            forcedCheckdownRecoveryErrorBb = List.copyOf(forcedCheckdownRecoveryErrorBb);
            jointMinusCheckdownUtilitiesBb = List.copyOf(jointMinusCheckdownUtilitiesBb);
            jointlySolvedConditionalPostflop = List.copyOf(jointlySolvedConditionalPostflop);
        }
    }

    private SixMaxConnectedPreflopAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            long seed,
            int iterations,
            double betPotFraction) {
        if (iterations < 1 || iterations > 500)
            throw new IllegalArgumentException("Joint audit requires 1–500 iterations");
        if (!Double.isFinite(betPotFraction) || betPotFraction <= 0 || betPotFraction > 2)
            throw new IllegalArgumentException("Bet fraction must be in (0, 2]");
        var example =
                SixMaxPreflopContinuationAudit.assess(base, source, seed, 1).examples().stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Source has no reached heads-up continuation"));
        double flopBet = Math.min(example.potBb() * betPotFraction, example.remainingStackBb());
        double turnBet = (example.potBb() + 2 * flopBet) * betPotFraction;
        double riverBet =
                (example.potBb()
                                + 2 * flopBet
                                + 2
                                        * Math.min(
                                                turnBet,
                                                Math.max(0, example.remainingStackBb() - flopBet)))
                        * betPotFraction;
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(
                                new SixMaxConnectedPreflopGame.Selection(
                                        example.history(),
                                        List.of(
                                                example.sampledFlop().stream()
                                                        .map(Card::parse)
                                                        .toList()),
                                        flopBet,
                                        turnBet,
                                        riverBet)));
        var baseline =
                new MultiPlayerCfrSolver<>(base, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        var lifted = liftCheckdown(game, baseline);
        var baselineQuality = MultiPlayerInformationSetBestResponse.assess(base, baseline);
        double[] liftedValues = MultiPlayerStrategyEvaluator.utilities(game, lifted);
        List<Double> recovery = new ArrayList<>();
        for (int seat = 0; seat < 6; seat++) {
            double error = liftedValues[seat] - baselineQuality.profileUtilitiesBb().get(seat);
            if (Math.abs(error) > 1e-8)
                throw new IllegalStateException("Forced checkdown failed to recover source payoff");
            recovery.add(error);
        }
        var liftedQuality = MultiPlayerInformationSetBestResponse.assess(game, lifted);
        var joint = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        var jointQuality = MultiPlayerInformationSetBestResponse.assess(game, joint);
        List<Double> changes = new ArrayList<>();
        for (int seat = 0; seat < 6; seat++)
            changes.add(
                    jointQuality.profileUtilitiesBb().get(seat)
                            - baselineQuality.profileUtilitiesBb().get(seat));
        double maximum = 0;
        for (var row : baseline.strategy().entrySet()) {
            var updated = joint.strategy().get(row.getKey());
            if (updated == null)
                throw new IllegalStateException("Joint solve lost a preflop information set");
            for (var action : row.getValue().entrySet())
                maximum =
                        Math.max(
                                maximum,
                                Math.abs(action.getValue() - updated.get(action.getKey())));
        }
        return new Report(
                "SPARSE_PHYSICAL_FLOPS_CONNECTED_PREFLOP_FLOP_TURN_RIVER",
                base.chanceModel().name(),
                seed,
                game.coverage(),
                source.iterations(),
                bettingProbability(game, source),
                solve(base, baseline, baselineQuality, bettingProbability(game, baseline)),
                Quality.of(liftedQuality),
                recovery,
                solve(game, joint, jointQuality, bettingProbability(game, joint)),
                maximum,
                changes,
                conditionalPostflop(game, joint));
    }

    /**
     * Measure rare branches independently: the full-game gap can hide conditional postflop error.
     */
    public static List<ConditionalPostflop> conditionalPostflop(
            SixMaxConnectedPreflopGame game, CfrSolution joint) {
        var results = new ArrayList<ConditionalPostflop>();
        for (var selection : game.selections()) {
            if (historyReach(game, joint, selection) == 0) continue;
            var transition =
                    new SixMaxPolicyFlopTransition(game.source(), joint, selection.history());
            for (var board : selection.flops()) {
                if (transition.flopProbability(board) == 0) continue;
                var flop = transition.conditionOnFlop(board);
                var post =
                        new SixMaxHeadsUpPostflopGame(
                                flop,
                                selection.flopBetBb(),
                                selection.turnBetBb(),
                                selection.riverBetBb());
                var policy = postflopPolicy(post, joint);
                var evaluator = new SixMaxPostflopDecisionEvaluator(post, policy);
                var decisions =
                        post.chanceOutcomes(post.initialState()).stream()
                                .map(o -> post.ownHand(o.state()).key())
                                .distinct()
                                .sorted()
                                .map(
                                        combo ->
                                                evaluator.evaluate(
                                                        SixMaxPostflopDecisionEvaluator.History
                                                                .flop(List.of()),
                                                        combo))
                                .toList();
                results.add(
                        new ConditionalPostflop(
                                flop.board().stream().map(Card::compact).toList(),
                                transition.reachProbability(),
                                flop.probability(),
                                flop.deals().size(),
                                HeadsUpBestResponse.assess(post, policy),
                                decisions));
            }
        }
        return List.copyOf(results);
    }

    static double historyReach(
            SixMaxConnectedPreflopGame game,
            CfrSolution joint,
            SixMaxConnectedPreflopGame.Selection selection) {
        double reach = 0;
        for (var root : game.source().chanceOutcomes(game.source().initialState())) {
            double mass = root.probability();
            var state = root.state();
            for (var action : selection.history()) {
                mass *=
                        MultiPlayerStrategyEvaluator.probability(
                                game.source(), joint, state, action.action());
                state = game.source().afterAction(state, action.action());
            }
            reach += mass;
        }
        return reach;
    }

    static CfrSolution postflopPolicy(SixMaxHeadsUpPostflopGame post, CfrSolution joint) {
        var translated = new LinkedHashMap<String, Map<String, Double>>();
        String informationPrefix = post.informationSetPrefix();
        for (int player = 0; player < 2; player++) {
            String prefix = post.seat(player).ordinal() + ":postflop:";
            for (var row : joint.strategy().entrySet())
                if (row.getKey().startsWith(prefix)
                        && row.getKey().startsWith(informationPrefix, prefix.length()))
                    translated.put(
                            player + ":" + row.getKey().substring(prefix.length()), row.getValue());
        }
        return new CfrSolution(joint.iterations(), translated);
    }

    /** Extend every postflop information set, even off-policy ones, with forced checks/calls. */
    public static CfrSolution liftCheckdown(SixMaxConnectedPreflopGame game, CfrSolution preflop) {
        Map<String, Map<String, Double>> strategy = new LinkedHashMap<>();
        collectCheckdown(game, game.initialState(), preflop, strategy);
        return new CfrSolution(preflop.iterations(), strategy);
    }

    private static void collectCheckdown(
            SixMaxConnectedPreflopGame game,
            SixMaxConnectedPreflopGame.State state,
            CfrSolution preflop,
            Map<String, Map<String, Double>> strategy) {
        if (game.isTerminal(state)) return;
        if (game.currentPlayer(state) == -1) {
            for (var outcome : game.chanceOutcomes(state))
                collectCheckdown(game, outcome.state(), preflop, strategy);
            return;
        }
        var weights = new LinkedHashMap<String, Double>();
        for (String action : game.legalActions(state))
            weights.put(
                    action,
                    state.postflop() == null
                            ? MultiPlayerStrategyEvaluator.probability(
                                    game.source(), preflop, state.preflop(), action)
                            : action.equals("check") || action.equals("call") ? 1.0 : 0.0);
        strategy.put(game.currentPlayer(state) + ":" + game.informationSet(state), weights);
        for (String action : game.legalActions(state))
            collectCheckdown(game, game.afterAction(state, action), preflop, strategy);
    }

    /** Unconditional probability of reaching one of the betting-enabled physical flops. */
    public static double bettingProbability(SixMaxConnectedPreflopGame game, CfrSolution policy) {
        Map<String, List<Double>> coverage = new LinkedHashMap<>();
        for (var selected : game.coverage())
            coverage.put(selected.publicHistory(), selected.bettingFlopProbabilityByDeal());
        return reach(game.source(), policy, game.source().initialState(), coverage);
    }

    private static double reach(
            SixMaxPreflopCheckdownGame game,
            CfrSolution policy,
            SixMaxPreflopCheckdownGame.State state,
            Map<String, List<Double>> coverage) {
        if (game.isTerminal(state))
            return coverage.containsKey(state.publicHistory())
                    ? coverage.get(state.publicHistory()).get(state.dealIndex())
                    : 0;
        if (game.currentPlayer(state) == -1)
            return game.chanceOutcomes(state).stream()
                    .mapToDouble(o -> o.probability() * reach(game, policy, o.state(), coverage))
                    .sum();
        double result = 0;
        for (String action : game.legalActions(state))
            result +=
                    MultiPlayerStrategyEvaluator.probability(game, policy, state, action)
                            * reach(game, policy, game.afterAction(state, action), coverage);
        return result;
    }

    private static <S> Solve solve(
            MultiPlayerCfrGame<S> game,
            CfrSolution solution,
            MultiPlayerInformationSetBestResponse.Report quality,
            double bettingProbability) {
        var openings = new LinkedHashMap<String, Map<String, Double>>();
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            String key = game.currentPlayer(state) + ":" + game.informationSet(state);
            openings.put(key, solution.strategy().get(key));
        }
        return new Solve(
                solution.iterations(),
                solution.strategy().size(),
                SixMaxConnectedPostflopAudit.solutionHash(solution),
                Quality.of(quality),
                bettingProbability,
                Map.copyOf(openings));
    }
}
