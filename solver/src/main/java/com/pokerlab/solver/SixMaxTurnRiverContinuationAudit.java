package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independently solved turn/river subgames reached through a frozen flop policy. */
public final class SixMaxTurnRiverContinuationAudit {
    public record Example(
            List<PublicAction> preflopHistory,
            double preflopReachProbability,
            List<String> board,
            double flopProbability,
            double flopBetBb,
            CfrSolution sourceFlopSolution,
            List<String> flopHistory,
            double flopHistoryProbability,
            double turnProbability,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            double remainingStackBb,
            double turnBetBb,
            double requestedRiverBetBb,
            int posteriorJointDeals,
            long exactRivers,
            int informationSets,
            HeadsUpBestResponse.Report bestResponse,
            Map<Seat, Double> checkdownUtilitiesBb,
            Map<Seat, Double> solvedUtilitiesBb,
            Map<Seat, Double> changeFromCheckdownBb,
            List<SixMaxHeadsUpTurnRiverDecisionEvaluator.Decision> firstPlayerDecisions,
            CfrSolution solution) {
        public Example {
            preflopHistory = List.copyOf(preflopHistory);
            board = List.copyOf(board);
            flopHistory = List.copyOf(flopHistory);
            checkdownUtilitiesBb = Map.copyOf(checkdownUtilitiesBb);
            solvedUtilitiesBb = Map.copyOf(solvedUtilitiesBb);
            changeFromCheckdownBb = Map.copyOf(changeFromCheckdownBb);
            firstPlayerDecisions = List.copyOf(firstPlayerDecisions);
        }
    }

    public record Report(
            String chanceModel,
            String continuationModel,
            long seed,
            int flopIterations,
            int turnRiverIterations,
            double requestedBetPotFraction,
            double maximumConditionalBestResponseGapBb,
            List<Example> examples) {
        public Report {
            examples = List.copyOf(examples);
        }
    }

    private SixMaxTurnRiverContinuationAudit() {}

    public static Report assess(
            SixMaxPreflopCheckdownGame sourceGame,
            CfrSolution source,
            long seed,
            int exampleLimit,
            int flopIterations,
            int turnRiverIterations,
            double fraction) {
        if (exampleLimit < 1 || exampleLimit > 10)
            throw new IllegalArgumentException("Turn/river audit requires 1–10 examples");
        if (turnRiverIterations < 1 || turnRiverIterations > 100_000)
            throw new IllegalArgumentException("Turn/river iterations must be 1–100000");
        var flops =
                SixMaxFlopContinuationAudit.assess(
                        sourceGame, source, seed, exampleLimit, flopIterations, fraction);
        var examples = new ArrayList<Example>();
        double maximumGap = 0;
        for (int index = 0; index < flops.examples().size(); index++) {
            var selected = flops.examples().get(index);
            var handoff =
                    new SixMaxPolicyFlopTransition(sourceGame, source, selected.preflopHistory());
            var flop =
                    new SixMaxHeadsUpFlopGame(
                            handoff.conditionOnFlop(
                                    selected.board().stream().map(Card::parse).toList()),
                            selected.betBb());
            var transition = selectTransition(flop, selected.solution());
            var turn = transition.sampleTurn(seed + index);
            double turnBet = Math.min(transition.remainingStackBb(), transition.potBb() * fraction);
            // Fixed menu per street. The river request uses the pot after a called turn bet;
            // the game caps it separately for each public turn branch's remaining stack.
            double riverBet = (transition.potBb() + 2 * turnBet) * fraction;
            var game = new SixMaxHeadsUpTurnRiverGame(turn, turnBet, riverBet);
            var solved =
                    new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(turnRiverIterations);
            var quality = HeadsUpBestResponse.assess(game, solved);
            maximumGap = Math.max(maximumGap, quality.gap());
            var baseline = game.exactCheckdownUtilitiesBb();
            var utilities = game.profileUtilitiesBb(solved);
            var changes = new LinkedHashMap<Seat, Double>();
            for (Seat seat : Seat.values())
                changes.put(seat, utilities.get(seat) - baseline.get(seat));
            var evaluator = new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, solved);
            var decisions =
                    game.chanceOutcomes(game.initialState()).stream()
                            .map(o -> game.ownHand(o.state()).key())
                            .distinct()
                            .sorted()
                            .map(combo -> evaluator.evaluate(List.of(), null, List.of(), combo))
                            .toList();
            examples.add(
                    new Example(
                            selected.preflopHistory(),
                            selected.preflopReachProbability(),
                            turn.board().stream().map(Card::compact).toList(),
                            selected.flopProbability(),
                            selected.betBb(),
                            selected.solution(),
                            transition.history(),
                            transition.historyProbability(),
                            turn.probability(),
                            game.seat(0),
                            game.seat(1),
                            transition.potBb(),
                            transition.remainingStackBb(),
                            game.turnBetBb(),
                            riverBet,
                            turn.deals().size(),
                            36L * turn.deals().size(),
                            solved.strategy().size(),
                            quality,
                            baseline,
                            utilities,
                            changes,
                            decisions,
                            solved));
        }
        return new Report(
                flops.chanceModel(),
                "ONE_TURN_BET_ONE_RIVER_BET_EXACT_RIVER",
                seed,
                flopIterations,
                turnRiverIterations,
                fraction,
                maximumGap,
                examples);
    }

    private static SixMaxPolicyTurnTransition selectTransition(
            SixMaxHeadsUpFlopGame game, CfrSolution solution) {
        var candidates = new ArrayList<SixMaxPolicyTurnTransition>();
        for (var history :
                List.of(
                        List.of("check", "check"),
                        List.of("bet", "call"),
                        List.of("check", "bet", "call"))) {
            if (history.contains("call")
                    && game.betBb() == game.flop().handoff().remainingStackBb()) continue;
            double reach = 0;
            for (var outcome : game.chanceOutcomes(game.initialState())) {
                double mass = outcome.probability();
                var state = outcome.state();
                for (String action : history) {
                    mass *= game.strategy(solution, state).get(action);
                    state = game.afterAction(state, action);
                }
                reach += mass;
            }
            if (reach > 0) candidates.add(new SixMaxPolicyTurnTransition(game, solution, history));
        }
        return candidates.stream()
                .sorted(
                        Comparator.comparingDouble(SixMaxPolicyTurnTransition::historyProbability)
                                .reversed()
                                .thenComparing(t -> String.join(";", t.history())))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No reached non-all-in turn betting histories"));
    }
}
