package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline diagnostics of joint support, folded-card removal and reached private beliefs. */
public final class SixMaxPrivateSupportAudit {
    public record BoardSupport(
            String publicHistory,
            List<String> flop,
            int counterfactualJointDeals,
            double compatiblePriorMass,
            double priorPhysicalFlopProbability,
            double policyHistoryReach,
            int reachedJointDeals,
            double reachedPhysicalFlopProbability,
            Map<Seat, Map<String, Double>> counterfactualMarginals,
            Map<Seat, Map<String, Double>> reachedMarginals,
            List<Seat> uncertainFoldedSeats,
            int firstPlayerRootInformationSets,
            int secondPlayerRootInformationSets) {
        public BoardSupport {
            flop = List.copyOf(flop);
            counterfactualMarginals = immutable(counterfactualMarginals);
            reachedMarginals = immutable(reachedMarginals);
            uncertainFoldedSeats = List.copyOf(uncertainFoldedSeats);
        }
    }

    public record Report(
            String interpretation,
            String chanceModel,
            String solutionHash,
            int sourceJointDeals,
            Map<Seat, Map<String, Double>> sourceMarginals,
            List<Seat> uncertainSeats,
            List<BoardSupport> boards) {
        public Report {
            sourceMarginals = immutable(sourceMarginals);
            uncertainSeats = List.copyOf(uncertainSeats);
            boards = List.copyOf(boards);
        }
    }

    private SixMaxPrivateSupportAudit() {}

    public static Report assess(SixMaxConnectedPreflopGame game, CfrSolution policy) {
        var source = game.source();
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(
                                source,
                                SixMaxPreflopContinuationFeedback.preflopPolicy(policy),
                                200_000)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException(
                    "Private support audit requires complete preflop rows");
        var roots = source.chanceOutcomes(source.initialState());
        var prior = new LinkedHashMap<Seat, Map<String, Double>>();
        for (Seat seat : Seat.values()) {
            var weights = new LinkedHashMap<String, Double>();
            for (var root : roots)
                weights.merge(
                        source.dealtHands(root.state()).get(seat.ordinal()).key(),
                        root.probability(),
                        Double::sum);
            prior.put(seat, weights);
        }
        var boards = new ArrayList<BoardSupport>();
        for (var selection : game.selections()) {
            var support =
                    SixMaxPolicyFlopTransition.counterfactualSupport(source, selection.history());
            double historyReach = SixMaxConnectedPreflopAudit.historyReach(game, policy, selection);
            var reached =
                    hasPositiveHistoryReach(source, policy, selection)
                            ? new SixMaxPolicyFlopTransition(source, policy, selection.history())
                            : null;
            for (var board : selection.flops()) {
                var compatible = support.conditionOnFlop(board);
                var counterfactual = marginals(compatible.deals());
                double reachedFlop = reached == null ? 0 : reached.flopProbability(board);
                var posterior = reachedFlop > 0 ? reached.conditionOnFlop(board) : null;
                var firstKeys = new java.util.HashSet<String>();
                var secondKeys = new java.util.HashSet<String>();
                for (var root : roots) {
                    var pre = game.replayPreflop(selection.history(), root.state().dealIndex());
                    for (var outcome : game.chanceOutcomes(pre)) {
                        var state = outcome.state();
                        if (state.otherFlopsCheckdown()
                                || !selection.flops().get(state.flopIndex()).equals(board))
                            continue;
                        firstKeys.add(game.informationSet(state));
                        secondKeys.add(game.informationSet(game.afterAction(state, "check")));
                    }
                }
                if (firstKeys.size() != counterfactual.get(support.firstToAct()).size()
                        || secondKeys.size() != counterfactual.get(support.secondToAct()).size())
                    throw new IllegalStateException(
                            "Postflop root information sets must depend only on own hand and public state");
                var folded = new ArrayList<Seat>();
                for (Seat seat : Seat.values())
                    if (seat != support.firstToAct()
                            && seat != support.secondToAct()
                            && counterfactual.get(seat).size() > 1) folded.add(seat);
                double physicalPrior = support.flopProbability(board);
                boards.add(
                        new BoardSupport(
                                preHistory(game, selection),
                                compatible.board().stream().map(Card::compact).toList(),
                                compatible.deals().size(),
                                physicalPrior * SixMaxPolicyFlopTransition.FLOPS_PER_DEAL,
                                physicalPrior,
                                historyReach,
                                posterior == null ? 0 : posterior.deals().size(),
                                historyReach * reachedFlop,
                                counterfactual,
                                posterior == null ? Map.of() : marginals(posterior.deals()),
                                folded,
                                firstKeys.size(),
                                secondKeys.size()));
            }
        }
        return new Report(
                "Offline private beliefs only; never expose joint hands to a trainer. Marginals are descriptive, not independent ranges. Counterfactual support retains zero-policy-reach deals. Root information-set counts check own-hand/public-state grouping; they do not certify every later information set or solver convergence.",
                source.chanceModel().name(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                roots.size(),
                prior,
                prior.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(Map.Entry::getKey)
                        .toList(),
                boards);
    }

    private static String preHistory(
            SixMaxConnectedPreflopGame game, SixMaxConnectedPreflopGame.Selection selection) {
        return game.replayPreflop(selection.history(), 0).preflop().publicHistory();
    }

    private static boolean hasPositiveHistoryReach(
            SixMaxPreflopCheckdownGame source,
            CfrSolution policy,
            SixMaxConnectedPreflopGame.Selection selection) {
        // A positive product can underflow to zero; the transition normalizes log likelihoods.
        for (var root : source.chanceOutcomes(source.initialState())) {
            var state = root.state();
            boolean positive = true;
            for (var action : selection.history()) {
                if (MultiPlayerStrategyEvaluator.probability(source, policy, state, action.action()) == 0) {
                    positive = false;
                    break;
                }
                state = source.afterAction(state, action.action());
            }
            if (positive) return true;
        }
        return false;
    }

    private static Map<Seat, Map<String, Double>> marginals(
            List<SixMaxPolicyFlopTransition.JointDeal> deals) {
        var result = new LinkedHashMap<Seat, Map<String, Double>>();
        for (Seat seat : Seat.values()) {
            var weights = new LinkedHashMap<String, Double>();
            for (var deal : deals)
                weights.merge(
                        deal.hands().get(seat.ordinal()).key(), deal.probability(), Double::sum);
            result.put(seat, weights);
        }
        return result;
    }

    private static Map<Seat, Map<String, Double>> immutable(Map<Seat, Map<String, Double>> input) {
        var result = new LinkedHashMap<Seat, Map<String, Double>>();
        input.forEach((seat, weights) -> result.put(seat, Map.copyOf(weights)));
        return Map.copyOf(result);
    }
}
