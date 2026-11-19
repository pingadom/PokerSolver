package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Reassesses a fixed sampled-payoff policy using complete board enumeration in the same game. */
public final class SixSeatPayoffComparison {
    public record SeatComparison(
            Seat seat,
            double minimumTerminalShiftBb,
            double maximumTerminalShiftBb,
            double deviationShiftBoundBb,
            double sampledProfileEvBb,
            double exactProfileEvBb,
            double sampledBestResponseEvBb,
            double exactBestResponseEvBb,
            double sampledDeviationGainBb,
            double exactDeviationGainBb) {}

    public record Report(
            String sourcePackHash,
            String sampledPayoffPlanHash,
            String exactPayoffPlanHash,
            long comparedTerminalStates,
            long sampledBoards,
            long exactBoards,
            double sampledMaximumTerminalStandardErrorBb,
            double sampledNashConvBb,
            double exactNashConvBb,
            List<SeatComparison> seats) {
        public Report {
            seats = List.copyOf(seats);
        }
    }

    private SixSeatPayoffComparison() {}

    /** Plan reference work before creating the sampled source or enumerating any boards. */
    public static SixSeatPayoffWork.Plan planExactReference(
            SixSeatRangeSource.Plan sampledSource, SixSeatPayoffWork.Budget exactBudget) {
        return planExactReference(
                sampledSource, SixSeatPayoffWork.Configuration.exact(), exactBudget);
    }

    static SixSeatPayoffWork.Plan planExactReference(
            SixSeatRangeSource.Plan sampledSource,
            SixSeatPayoffWork.Configuration reference,
            SixSeatPayoffWork.Budget exactBudget) {
        requireMethod(
                sampledSource.payoffPlan(), SixSeatPayoffWork.Method.SHARED_BOARD_MONTE_CARLO);
        if (!reference.method().isExact())
            throw new IllegalArgumentException("Payoff comparison requires an exact reference");
        return SixSeatPayoffWork.plan(sampledSource.payoffPlan().input(), reference, exactBudget);
    }

    record MatchedGames(SixMaxPreflopCheckdownGame sampled, SixMaxPreflopCheckdownGame exact) {}

    static MatchedGames matchedGames(
            SixSeatRangeSource.Result sampledSource, SixSeatPayoffWork.Result exactPayoffs) {
        Objects.requireNonNull(sampledSource, "sampledSource");
        Objects.requireNonNull(exactPayoffs, "exactPayoffs");
        var sampledPayoffs = sampledSource.payoffs();
        requireMethod(sampledPayoffs.plan(), SixSeatPayoffWork.Method.SHARED_BOARD_MONTE_CARLO);
        if (!exactPayoffs.plan().configuration().method().isExact())
            throw new IllegalArgumentException(
                    "Payoff comparison requires exact reference payoffs");
        if (!sampledPayoffs
                .plan()
                .input()
                .inputHash()
                .equals(exactPayoffs.plan().input().inputHash()))
            throw new IllegalArgumentException("Exact reference must use the same weighted ranges");

        var spot = sampledSource.plan().spot();
        var sampled = spot.game(sampledPayoffs.oracle());
        var exact = spot.game(exactPayoffs.oracle());
        var outcomes = sampled.chanceOutcomes(sampled.initialState());
        if (!outcomes.equals(exact.chanceOutcomes(exact.initialState())))
            throw new IllegalStateException("Payoff comparison requires the same chance outcomes");
        return new MatchedGames(sampled, exact);
    }

    /** Reuse both owned inputs. This comparison executes no board trials or CFR iterations. */
    public static Report assess(
            SixSeatRangeSource.Result sampledSource, SixSeatPayoffWork.Result exactPayoffs) {
        var games = matchedGames(sampledSource, exactPayoffs);
        var sampled = games.sampled();
        var exact = games.exact();
        var sampledPayoffs = sampledSource.payoffs();
        var outcomes = sampled.chanceOutcomes(sampled.initialState());
        var seats = Seat.values();
        double[] minimum = new double[seats.length];
        double[] maximum = new double[seats.length];
        Arrays.fill(minimum, Double.POSITIVE_INFINITY);
        Arrays.fill(maximum, Double.NEGATIVE_INFINITY);
        long terminals = 0;
        for (var outcome : outcomes)
            terminals += compareTerminals(sampled, exact, outcome.state(), minimum, maximum);

        var tree = sampled.treeSummary();
        long expectedTerminals =
                (long) outcomes.size()
                        * (tree.uncontestedTerminals()
                                + tree.allInTerminals()
                                + tree.checkdownTerminals());
        if (terminals == 0 || terminals != expectedTerminals)
            throw new IllegalStateException("Incomplete terminal payoff comparison");

        var before = sampledSource.quality();
        var after =
                MultiPlayerInformationSetBestResponse.assess(
                        exact, sampledSource.pack().solution());
        var comparisons = new ArrayList<SeatComparison>();
        for (Seat seat : seats) {
            int index = seat.ordinal();
            // Every strategy's expected shift is in [min, max], including a best response.
            // Subtracting the profile shift bounds the change in unilateral deviation gain.
            comparisons.add(
                    new SeatComparison(
                            seat,
                            minimum[index],
                            maximum[index],
                            maximum[index] - minimum[index],
                            before.profileUtilitiesBb().get(index),
                            after.profileUtilitiesBb().get(index),
                            before.bestResponseUtilitiesBb().get(index),
                            after.bestResponseUtilitiesBb().get(index),
                            before.deviationGainsBb().get(index),
                            after.deviationGainsBb().get(index)));
        }
        return new Report(
                sampledSource.packHash(),
                sampledPayoffs.plan().contentHash(),
                exactPayoffs.plan().contentHash(),
                terminals,
                sampledPayoffs.boardsFromPerDealEstimates(),
                exactPayoffs.boardsFromEvaluatedDeals(),
                sampled.maximumTerminalPayoffStandardErrorBb(),
                before.nashConvBb(),
                after.nashConvBb(),
                comparisons);
    }

    private static void requireMethod(
            SixSeatPayoffWork.Plan plan, SixSeatPayoffWork.Method method) {
        if (plan.configuration().method() != method)
            throw new IllegalArgumentException("Payoff comparison requires " + method);
    }

    private static long compareTerminals(
            SixMaxPreflopCheckdownGame sampled,
            SixMaxPreflopCheckdownGame exact,
            SixMaxPreflopCheckdownGame.State state,
            double[] minimum,
            double[] maximum) {
        // The two games use the same spot, private-world order and public tree.
        if (sampled.isTerminal(state)) {
            var before = sampled.terminalUtilities(state);
            var after = exact.terminalUtilities(state);
            for (int seat = 0; seat < before.length; seat++) {
                double shift = after[seat] - before[seat];
                minimum[seat] = Math.min(minimum[seat], shift);
                maximum[seat] = Math.max(maximum[seat], shift);
            }
            return 1;
        }
        long count = 0;
        for (String action : sampled.legalActions(state))
            count +=
                    compareTerminals(
                            sampled, exact, sampled.afterAction(state, action), minimum, maximum);
        return count;
    }
}
