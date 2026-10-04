package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Solves bounded conditional flop games without changing the source six-seat preflop policy. */
public final class SixMaxFlopContinuationAudit {
    public record Example(
            List<PublicAction> preflopHistory,
            double preflopReachProbability,
            List<String> board,
            double flopProbability,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            double remainingStackBb,
            double betBb,
            int posteriorJointDeals,
            long exactRunouts,
            int informationSets,
            HeadsUpBestResponse.Report bestResponse,
            Map<Seat, Double> checkdownUtilitiesBb,
            Map<Seat, Double> solvedUtilitiesBb,
            Map<Seat, Double> changeFromCheckdownBb,
            List<SixMaxHeadsUpFlopDecisionEvaluator.Decision> firstPlayerDecisions,
            CfrSolution solution) {
        public Example {
            preflopHistory = List.copyOf(preflopHistory);
            board = List.copyOf(board);
            checkdownUtilitiesBb = Map.copyOf(checkdownUtilitiesBb);
            solvedUtilitiesBb = Map.copyOf(solvedUtilitiesBb);
            changeFromCheckdownBb = Map.copyOf(changeFromCheckdownBb);
            firstPlayerDecisions = List.copyOf(firstPlayerDecisions);
        }
    }

    public record Report(
            String chanceModel,
            String continuationModel,
            long flopSeed,
            int iterations,
            double requestedBetPotFraction,
            double sourceHeadsUpContinuationProbability,
            int sourceReachedHeadsUpHistories,
            double maximumConditionalBestResponseGapBb,
            List<Example> examples) {
        public Report {
            examples = List.copyOf(examples);
        }
    }

    private SixMaxFlopContinuationAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame game,
            CfrSolution source,
            long seed,
            int exampleLimit,
            int iterations,
            double betPotFraction) {
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be 1–100000");
        if (!Double.isFinite(betPotFraction) || betPotFraction <= 0 || betPotFraction > 2)
            throw new IllegalArgumentException("Requested flop bet must be in (0, 2] pots");
        var reach = SixMaxPreflopContinuationAudit.assess(game, source, seed, exampleLimit);
        if (reach.examples().isEmpty())
            throw new IllegalArgumentException("No reached heads-up flop histories to solve");
        List<Example> examples = new ArrayList<>();
        double maximumGap = 0;
        for (var selected : reach.examples()) {
            var handoff = new SixMaxPolicyFlopTransition(game, source, selected.history());
            var flop =
                    handoff.conditionOnFlop(
                            selected.sampledFlop().stream()
                                    .map(com.pokerlab.core.card.Card::parse)
                                    .toList());
            double bet = Math.min(handoff.remainingStackBb(), handoff.potBb() * betPotFraction);
            var continuation = new SixMaxHeadsUpFlopGame(flop, bet);
            var solution =
                    new CfrSolver<>(continuation, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            var quality = HeadsUpBestResponse.assess(continuation, solution);
            maximumGap = Math.max(maximumGap, quality.gap());
            var utilities = continuation.profileUtilitiesBb(solution);
            Map<Seat, Double> changes = new LinkedHashMap<>();
            for (Seat seat : Seat.values())
                changes.put(
                        seat,
                        utilities.get(seat)
                                - selected.exactFlopCheckdown().utilitiesBb().get(seat));
            var evaluator = new SixMaxHeadsUpFlopDecisionEvaluator(continuation, solution);
            List<SixMaxHeadsUpFlopDecisionEvaluator.Decision> decisions =
                    continuation.chanceOutcomes(continuation.initialState()).stream()
                            .map(outcome -> continuation.ownHand(outcome.state()).key())
                            .distinct()
                            .sorted()
                            .map(combo -> evaluator.evaluate(List.of(), combo))
                            .toList();
            examples.add(
                    new Example(
                            selected.history(),
                            selected.reachProbability(),
                            selected.sampledFlop(),
                            selected.flopProbability(),
                            handoff.firstToAct(),
                            handoff.secondToAct(),
                            handoff.potBb(),
                            handoff.remainingStackBb(),
                            bet,
                            flop.deals().size(),
                            selected.exactFlopCheckdown().runouts(),
                            solution.strategy().size(),
                            quality,
                            selected.exactFlopCheckdown().utilitiesBb(),
                            utilities,
                            changes,
                            decisions,
                            solution));
        }
        return new Report(
                reach.chanceModel(),
                "ONE_FLOP_BET_THEN_MANDATORY_CHECKDOWN",
                seed,
                iterations,
                betPotFraction,
                reach.headsUpContinuationProbability(),
                reach.reachedHeadsUpHistories(),
                maximumGap,
                examples);
    }
}
