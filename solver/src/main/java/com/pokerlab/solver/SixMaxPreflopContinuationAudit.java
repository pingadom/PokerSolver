package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Measures fixed-policy terminal reach and prepares bounded heads-up continuation examples. */
public final class SixMaxPreflopContinuationAudit {
    public record Example(
            List<PublicAction> history,
            double reachProbability,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            double remainingStackBb,
            int posteriorJointDeals,
            List<String> sampledFlop,
            double flopProbability,
            int flopPosteriorJointDeals,
            SixMaxPolicyFlopTransition.Checkdown exactFlopCheckdown) {}

    public record Report(
            String chanceModel,
            Map<String, Double> terminalProbability,
            Map<Integer, Double> checkdownProbabilityByLiveSeats,
            double headsUpContinuationProbability,
            int reachedHeadsUpHistories,
            int flopsPerSixHandDeal,
            long flopSeed,
            List<Example> examples) {
        public Report {
            terminalProbability = Map.copyOf(terminalProbability);
            checkdownProbabilityByLiveSeats = Map.copyOf(checkdownProbabilityByLiveSeats);
            examples = List.copyOf(examples);
        }
    }

    private record Leaf(List<PublicAction> history, double probability) {}

    private SixMaxPreflopContinuationAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame game, CfrSolution solution, long seed, int exampleLimit) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        if (game.rakeRule().fraction() > 0 && game.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Postflop rake is not supported by this audit");
        if (exampleLimit < 1 || exampleLimit > 20)
            throw new IllegalArgumentException("Example limit must be 1–20");
        Map<String, Double> terminals = new LinkedHashMap<>();
        Map<Integer, Double> checkdowns = new LinkedHashMap<>();
        Map<String, Leaf> headsUp = new LinkedHashMap<>();
        for (var outcome : game.chanceOutcomes(game.initialState()))
            walk(
                    game,
                    solution,
                    outcome.state(),
                    outcome.probability(),
                    List.of(),
                    terminals,
                    checkdowns,
                    headsUp);
        double total = terminals.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1) > 1e-8)
            throw new IllegalArgumentException("Terminal policy reach must sum to one");
        List<Example> examples = new ArrayList<>();
        for (var entry :
                headsUp.entrySet().stream()
                        .sorted(
                                Comparator.<Map.Entry<String, Leaf>>comparingDouble(
                                                e -> e.getValue().probability())
                                        .reversed()
                                        .thenComparing(Map.Entry::getKey))
                        .limit(exampleLimit)
                        .toList()) {
            var transition =
                    new SixMaxPolicyFlopTransition(game, solution, entry.getValue().history());
            if (Math.abs(transition.reachProbability() - entry.getValue().probability()) > 1e-10)
                throw new IllegalStateException("Posterior reach disagrees with tree traversal");
            var flop = transition.sampleFlop(seed + examples.size());
            examples.add(
                    new Example(
                            transition.history(),
                            transition.reachProbability(),
                            transition.firstToAct(),
                            transition.secondToAct(),
                            transition.potBb(),
                            transition.remainingStackBb(),
                            transition.deals().size(),
                            flop.board().stream().map(card -> card.compact()).toList(),
                            flop.probability(),
                            flop.deals().size(),
                            flop.exactCheckdown()));
        }
        return new Report(
                game.chanceModel().name(),
                terminals,
                checkdowns,
                checkdowns.getOrDefault(2, 0.0),
                headsUp.size(),
                SixMaxPolicyFlopTransition.FLOPS_PER_DEAL,
                seed,
                examples);
    }

    private static void walk(
            SixMaxPreflopCheckdownGame game,
            CfrSolution solution,
            SixMaxPreflopCheckdownGame.State state,
            double probability,
            List<PublicAction> history,
            Map<String, Double> terminals,
            Map<Integer, Double> checkdowns,
            Map<String, Leaf> headsUp) {
        if (probability == 0) return;
        if (game.isTerminal(state)) {
            var publicState = game.publicBettingState(state);
            terminals.merge(publicState.status().name(), probability, Double::sum);
            if (publicState.status()
                    == SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED) {
                int live = publicState.liveSeats().size();
                checkdowns.merge(live, probability, Double::sum);
                if (live == 2)
                    headsUp.merge(
                            state.publicHistory(),
                            new Leaf(history, probability),
                            (a, b) -> new Leaf(a.history(), a.probability() + b.probability()));
            }
            return;
        }
        for (String action : game.legalActions(state)) {
            double weight = MultiPlayerStrategyEvaluator.probability(game, solution, state, action);
            if (weight == 0) continue;
            var next = new ArrayList<>(history);
            next.add(new PublicAction(Seat.values()[game.currentPlayer(state)], action));
            walk(
                    game,
                    solution,
                    game.afterAction(state, action),
                    probability * weight,
                    List.copyOf(next),
                    terminals,
                    checkdowns,
                    headsUp);
        }
    }
}
