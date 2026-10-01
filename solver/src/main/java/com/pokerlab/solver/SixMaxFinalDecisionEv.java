package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Kind;
import com.pokerlab.solver.SixMaxPreflopBetting.Move;
import com.pokerlab.solver.SixMaxPreflopBetting.State;
import com.pokerlab.solver.SixMaxPreflopBetting.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Conditional chip EV for the final fold/call decision in a six-seat all-in preflop hand. The
 * caller supplies ranges already conditioned on the observed public action history; this class does
 * not infer a strategy or the probability of reaching that history.
 */
public final class SixMaxFinalDecisionEv {
    public static final int MAX_JOINT_DEALS = 64;

    public record Result(
            Seat heroSeat,
            String heroCombo,
            int legalJointDeals,
            double foldEvBb,
            double callEvBb,
            double foldPayoffStandardErrorBoundBb,
            double callPayoffStandardErrorBoundBb) {
        public double callAdvantageBb() {
            return callEvBb - foldEvBb;
        }

        /** A conservative bound from the two branch payoff sampling errors only. */
        public double advantagePayoffStandardErrorBoundBb() {
            return foldPayoffStandardErrorBoundBb + callPayoffStandardErrorBoundBb;
        }
    }

    private record Deal(List<WeightedCombo> hands, double logWeight) {}

    private SixMaxFinalDecisionEv() {}

    public static Result evaluate(
            SixMaxPreflopBetting game,
            State decision,
            WeightedCombo heroHand,
            List<List<WeightedCombo>> actionConditionedRanges,
            CashRakeRule rakeRule,
            MultiwayShowdownOracle showdown) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(heroHand, "heroHand");
        Objects.requireNonNull(rakeRule, "rakeRule");
        Objects.requireNonNull(showdown, "showdown");
        if (decision.status() != Status.DECISION)
            throw new IllegalArgumentException("Expected a preflop decision");

        Move fold = action(decision, Kind.FOLD);
        Move call = action(decision, Kind.CALL);
        if (fold == null || call == null)
            throw new IllegalArgumentException("Decision must offer both fold and call");
        State folded = game.apply(decision, fold);
        State called = game.apply(decision, call);
        if (!isTerminal(folded.status()) || called.status() != Status.ALL_IN_SHOWDOWN)
            throw new IllegalArgumentException(
                    "Both actions must finish preflop, with a called all-in showdown");

        List<List<WeightedCombo>> ranges = validateRanges(actionConditionedRanges);
        Seat heroSeat = decision.actingSeat();
        if (ranges.get(heroSeat.ordinal()).stream()
                .noneMatch(combo -> combo.key().equals(heroHand.key())))
            throw new IllegalArgumentException("Hero hand is absent from the acting seat's range");

        List<Deal> deals = new ArrayList<>();
        WeightedCombo[] hands = new WeightedCombo[Seat.values().length];
        enumerate(0, heroSeat.ordinal(), heroHand, ranges, hands, new HashSet<>(), 0, deals);
        if (deals.isEmpty())
            throw new IllegalArgumentException("Ranges have no unblocked joint deal");

        double maxLogWeight = deals.stream().mapToDouble(Deal::logWeight).max().orElseThrow();
        double mass = 0;
        double callEv = 0;
        double callError = 0;
        for (Deal deal : deals) {
            double weight = Math.exp(deal.logWeight() - maxLogWeight);
            var callPayoff =
                    SixMaxPreflopTerminalPayoff.settle(called, deal.hands(), rakeRule, showdown);
            int hero = heroSeat.ordinal();
            mass += weight;
            callEv += weight * callPayoff.utilitiesBb().get(hero);
            callError += weight * callPayoff.maximumPayoffStandardErrorBb();
        }
        return new Result(
                heroSeat,
                heroHand.key(),
                deals.size(),
                -decision.committedBb(heroSeat),
                callEv / mass,
                0,
                callError / mass);
    }

    private static boolean isTerminal(Status status) {
        return status == Status.UNCONTESTED || status == Status.ALL_IN_SHOWDOWN;
    }

    private static Move action(State decision, Kind kind) {
        return decision.legalActions().stream()
                .filter(move -> move.kind() == kind)
                .findFirst()
                .orElse(null);
    }

    private static List<List<WeightedCombo>> validateRanges(
            List<List<WeightedCombo>> actionConditionedRanges) {
        if (actionConditionedRanges == null
                || actionConditionedRanges.size() != Seat.values().length)
            throw new IllegalArgumentException("Exactly six seat ranges are required");
        List<List<WeightedCombo>> ranges = new ArrayList<>();
        for (List<WeightedCombo> range : actionConditionedRanges) {
            if (range == null || range.isEmpty())
                throw new IllegalArgumentException("Every seat needs a nonempty range");
            Set<String> keys = new HashSet<>();
            for (WeightedCombo combo : range) {
                if (combo == null || !keys.add(combo.key()))
                    throw new IllegalArgumentException("Range has a missing or duplicate combo");
            }
            ranges.add(List.copyOf(range));
        }
        return List.copyOf(ranges);
    }

    private static void enumerate(
            int seat,
            int heroSeat,
            WeightedCombo heroHand,
            List<List<WeightedCombo>> ranges,
            WeightedCombo[] hands,
            Set<Card> used,
            double logWeight,
            List<Deal> deals) {
        if (seat == hands.length) {
            if (deals.size() == MAX_JOINT_DEALS)
                throw new IllegalArgumentException("More than 64 legal joint deals");
            deals.add(new Deal(List.of(hands.clone()), logWeight));
            return;
        }
        List<WeightedCombo> candidates = seat == heroSeat ? List.of(heroHand) : ranges.get(seat);
        for (WeightedCombo combo : candidates) {
            if (used.contains(combo.first()) || used.contains(combo.second())) continue;
            used.add(combo.first());
            used.add(combo.second());
            hands[seat] = combo;
            enumerate(
                    seat + 1,
                    heroSeat,
                    heroHand,
                    ranges,
                    hands,
                    used,
                    logWeight + (seat == heroSeat ? 0 : Math.log(combo.weight())),
                    deals);
            used.remove(combo.first());
            used.remove(combo.second());
        }
    }
}
