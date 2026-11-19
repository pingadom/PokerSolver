package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopCheckdownGame.State;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import com.pokerlab.solver.SixSeatTrainerActionAudit.Action;
import com.pokerlab.solver.SixSeatTrainerActionAudit.Prompt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Audits all positively reached prompts in one bounded traversal of paired payoff games. */
public final class SixSeatTrainerCoverageAudit {
    private static final int MAX_REPORTED_FAILURES = 32;

    public record Options(
            double minimumDecisionReach, double acceptableLossBb, double maximumLossDriftBb) {
        public Options {
            if (!Double.isFinite(minimumDecisionReach)
                    || minimumDecisionReach < 0
                    || minimumDecisionReach > 1)
                throw new IllegalArgumentException("Minimum decision reach must be within [0, 1]");
            if (!Double.isFinite(acceptableLossBb)
                    || acceptableLossBb < 0
                    || !Double.isFinite(maximumLossDriftBb)
                    || maximumLossDriftBb < 0)
                throw new IllegalArgumentException(
                        "Action loss limits must be finite and nonnegative");
        }
    }

    public record Failure(
            Prompt prompt,
            double decisionReach,
            List<Action> actions,
            List<String> sampledAcceptableActions,
            List<String> exactAcceptableActions,
            double maximumLossDriftBb) {}

    public record Summary(
            int reachedPrompts,
            int auditedPrompts,
            int stablePrompts,
            int changedAcceptableSets,
            double maximumObservedDriftBb) {}

    public record SeatSummary(Seat seat, Summary summary) {}

    public record Report(
            String sourcePackHash,
            String sampledPayoffPlanHash,
            String exactPayoffPlanHash,
            Options options,
            Summary totals,
            List<SeatSummary> seats,
            List<Failure> highestReachFailures) {
        public Report {
            seats = List.copyOf(seats);
            highestReachFailures = List.copyOf(highestReachFailures);
        }
    }

    private record Utility(double[] sampled, double[] exact) {
        private Utility() {
            this(new double[Seat.values().length], new double[Seat.values().length]);
        }

        private void add(double probability, Utility child) {
            for (int seat = 0; seat < sampled.length; seat++) {
                sampled[seat] += probability * child.sampled[seat];
                exact[seat] += probability * child.exact[seat];
            }
        }
    }

    private static final class PromptValues {
        private final double[] weightedSampled;
        private final double[] weightedExact;
        private double reach;

        private PromptValues(int actions) {
            weightedSampled = new double[actions];
            weightedExact = new double[actions];
        }

        private List<Action> losses(List<String> actions) {
            double bestSampled = Arrays.stream(weightedSampled).max().orElseThrow() / reach;
            double bestExact = Arrays.stream(weightedExact).max().orElseThrow() / reach;
            var losses = new ArrayList<Action>(actions.size());
            for (int i = 0; i < actions.size(); i++) {
                losses.add(
                        new Action(
                                actions.get(i),
                                bestSampled - weightedSampled[i] / reach,
                                bestExact - weightedExact[i] / reach));
            }
            return List.copyOf(losses);
        }
    }

    private static final class Counts {
        private int reached;
        private int audited;
        private int stable;
        private int changed;
        private double maximumDrift;

        private void record(SixSeatTrainerActionAudit.Verdict verdict) {
            audited++;
            if (verdict.stable()) stable++;
            if (!verdict.sampledAcceptableActions().equals(verdict.exactAcceptableActions()))
                changed++;
            maximumDrift = Math.max(maximumDrift, verdict.maximumLossDriftBb());
        }

        private Summary summary() {
            return new Summary(reached, audited, stable, changed, maximumDrift);
        }
    }

    private SixSeatTrainerCoverageAudit() {}

    public static Report assess(
            SixSeatRangeSource.Result source,
            SixSeatPayoffWork.Result exactPayoffs,
            Options options) {
        Objects.requireNonNull(options, "options");
        var games = SixSeatPayoffComparison.matchedGames(source, exactPayoffs);
        var policy = source.pack().solution();
        var prompts = new LinkedHashMap<Prompt, PromptValues>();
        var history = new ArrayList<PublicAction>();
        for (var deal : games.sampled().chanceOutcomes(games.sampled().initialState())) {
            evaluate(games, policy, deal.state(), deal.probability(), history, prompts);
        }

        var totals = new Counts();
        var seatCounts = Arrays.stream(Seat.values()).map(seat -> new Counts()).toList();
        var failures =
                new java.util.PriorityQueue<Failure>(
                        Comparator.comparingDouble(Failure::decisionReach));
        for (var entry : prompts.entrySet()) {
            var prompt = entry.getKey();
            var values = entry.getValue();
            var seat = seatCounts.get(prompt.seat().ordinal());
            totals.reached++;
            seat.reached++;
            if (values.reach < options.minimumDecisionReach()) continue;
            var actions = values.losses(prompt.legalActions());
            var verdict =
                    SixSeatTrainerActionAudit.verdict(
                            actions, options.acceptableLossBb(), options.maximumLossDriftBb());
            totals.record(verdict);
            seat.record(verdict);
            if (verdict.stable()) continue;
            if (failures.size() == MAX_REPORTED_FAILURES) {
                if (values.reach <= failures.element().decisionReach()) continue;
                failures.remove();
            }
            failures.add(
                    new Failure(
                            prompt,
                            values.reach,
                            actions,
                            verdict.sampledAcceptableActions(),
                            verdict.exactAcceptableActions(),
                            verdict.maximumLossDriftBb()));
        }
        var seats =
                Arrays.stream(Seat.values())
                        .map(
                                seat ->
                                        new SeatSummary(
                                                seat, seatCounts.get(seat.ordinal()).summary()))
                        .toList();
        var highestReach =
                failures.stream()
                        .sorted(Comparator.comparingDouble(Failure::decisionReach).reversed())
                        .toList();
        return new Report(
                source.packHash(),
                source.payoffs().plan().contentHash(),
                exactPayoffs.plan().contentHash(),
                options,
                totals.summary(),
                seats,
                highestReach);
    }

    private static Utility evaluate(
            SixSeatPayoffComparison.MatchedGames games,
            CfrSolution policy,
            State state,
            double reach,
            List<PublicAction> history,
            Map<Prompt, PromptValues> prompts) {
        var sampled = games.sampled();
        if (sampled.isTerminal(state)) {
            return new Utility(
                    sampled.terminalUtilities(state), games.exact().terminalUtilities(state));
        }
        int actor = sampled.currentPlayer(state);
        Seat seat = Seat.values()[actor];
        var actions = sampled.legalActions(state);
        PromptValues values = null;
        if (reach > 0) {
            var prompt =
                    new Prompt(seat, sampled.dealtHands(state).get(actor).key(), history, actions);
            values = prompts.computeIfAbsent(prompt, ignored -> new PromptValues(actions.size()));
            values.reach += reach;
        }
        var value = new Utility();
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.get(i);
            double probability =
                    MultiPlayerStrategyEvaluator.probability(sampled, policy, state, action);
            history.add(new PublicAction(seat, action));
            // Off-policy actions still supply this decision's counterfactual continuation.
            var child =
                    evaluate(
                            games,
                            policy,
                            sampled.afterAction(state, action),
                            reach * probability,
                            history,
                            prompts);
            history.removeLast();
            value.add(probability, child);
            if (values != null) {
                values.weightedSampled[i] += reach * child.sampled[actor];
                values.weightedExact[i] += reach * child.exact[actor];
            }
        }
        return value;
    }
}
