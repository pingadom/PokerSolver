package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Selects reached heads-up histories, without removing counterfactual private support. */
public final class SixMaxReachedContinuationStudy {
    public record Reach(
            Map<String, Double> terminalProbability,
            Map<Integer, Double> checkdownProbabilityByLiveSeats,
            int reachedHeadsUpHistories,
            double headsUpProbability,
            double selectedHistoryProbability,
            double unselectedHeadsUpProbability,
            double fractionOfHeadsUpProbabilitySelected,
            double selectedPhysicalFlopProbability,
            double selectedHistoryOtherFlopsProbability) {
        public Reach {
            terminalProbability = Map.copyOf(terminalProbability);
            checkdownProbabilityByLiveSeats = Map.copyOf(checkdownProbabilityByLiveSeats);
        }
    }

    public record SelectedHistory(
            int sourceReachRank,
            double sourceReachProbability,
            int sourcePosteriorJointDeals,
            SixMaxConnectedPreflopGame.Coverage coverage) {}

    public record Plan(
            SixMaxConnectedPreflopGame game,
            long flopSelectionSeed,
            int compatibleDealFlops,
            Reach sourceReach,
            List<SelectedHistory> selectedHistories) {
        public Plan {
            selectedHistories = List.copyOf(selectedHistories);
        }
    }

    private SixMaxReachedContinuationStudy() {}

    public static Plan select(
            SixMaxPreflopCheckdownGame base,
            CfrSolution source,
            int maximumHistories,
            long flopSeed) {
        if (maximumHistories < 1 || maximumHistories > 4)
            throw new IllegalArgumentException("Select 1–4 reached histories");
        if (base.chanceOutcomes(base.initialState()).size() > 4)
            throw new IllegalArgumentException(
                    "Connected study supports at most four private deals");
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(base, source, 200_000)
                        .addedInformationSets()
                != 0)
            throw new IllegalArgumentException(
                    "History selection requires a complete source policy");
        var audit = SixMaxPreflopContinuationAudit.assess(base, source, flopSeed, maximumHistories);
        if (audit.examples().isEmpty())
            throw new IllegalArgumentException("Source has no reached heads-up continuation");
        var selections = new ArrayList<SixMaxConnectedPreflopGame.Selection>();
        int pairs = 0;
        for (var example : audit.examples()) {
            var support = SixMaxPolicyFlopTransition.counterfactualSupport(base, example.history());
            var board = example.sampledFlop().stream().map(Card::parse).toList();
            pairs += support.conditionOnFlop(board).deals().size();
            if (pairs > 8)
                throw new IllegalArgumentException("Study supports at most eight deal/flop pairs");
            double flopBet = Math.min(support.potBb() * .5, support.remainingStackBb());
            double turnBet = (support.potBb() + 2 * flopBet) * .5;
            double riverBet =
                    (support.potBb()
                                    + 2 * flopBet
                                    + 2 * Math.min(turnBet, support.remainingStackBb() - flopBet))
                            * .5;
            selections.add(
                    new SixMaxConnectedPreflopGame.Selection(
                            example.history(), List.of(board), flopBet, turnBet, riverBet));
        }
        var game = new SixMaxConnectedPreflopGame(base, selections);
        var selected = new ArrayList<SelectedHistory>();
        for (int rank = 0; rank < audit.examples().size(); rank++) {
            var example = audit.examples().get(rank);
            var coverage =
                    game.coverage().stream()
                            .filter(c -> c.actions().equals(example.history()))
                            .findFirst()
                            .orElseThrow();
            selected.add(
                    new SelectedHistory(
                            rank + 1,
                            example.reachProbability(),
                            example.posteriorJointDeals(),
                            coverage));
        }
        return new Plan(game, flopSeed, pairs, reach(game, source, audit), selected);
    }

    public static Reach reach(SixMaxConnectedPreflopGame game, CfrSolution policy) {
        return reach(
                game, policy, SixMaxPreflopContinuationAudit.assess(game.source(), policy, 0, 1));
    }

    private static Reach reach(
            SixMaxConnectedPreflopGame game,
            CfrSolution policy,
            SixMaxPreflopContinuationAudit.Report audit) {
        double selected =
                game.selections().stream()
                        .mapToDouble(s -> SixMaxConnectedPreflopAudit.historyReach(game, policy, s))
                        .sum();
        double physical = SixMaxConnectedPreflopAudit.bettingProbability(game, policy);
        double headsUp = audit.headsUpContinuationProbability();
        if (selected > headsUp + 1e-10 || physical > selected + 1e-10)
            throw new IllegalStateException("Selected reach exceeds its parent reach");
        return new Reach(
                audit.terminalProbability(),
                audit.checkdownProbabilityByLiveSeats(),
                audit.reachedHeadsUpHistories(),
                headsUp,
                selected,
                Math.max(0, headsUp - selected),
                headsUp == 0 ? 0 : selected / headsUp,
                physical,
                Math.max(0, selected - physical));
    }
}
