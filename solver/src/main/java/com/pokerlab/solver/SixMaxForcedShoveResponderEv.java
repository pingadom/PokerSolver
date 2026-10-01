package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Kind;
import com.pokerlab.solver.SixMaxPreflopBetting.State;
import com.pokerlab.solver.SixMaxPreflopBetting.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conditional call/fold chip EV for any responder in the solved six-seat forced-shove game. Prior
 * actions condition the physical deal, while later responders follow the supplied policy through
 * the real six-seat betting rules. This is restricted research payoff evaluation, not a general
 * preflop strategy solver.
 */
public final class SixMaxForcedShoveResponderEv {
    public static final int MAX_REACHABLE_DEALS = 64;

    public record Result(
            Seat heroSeat,
            String heroCombo,
            int reachableJointDeals,
            double foldEvBb,
            double callEvBb,
            double callPayoffStandardErrorBoundBb) {
        public double callAdvantageBb() {
            return callEvBb - foldEvBb;
        }
    }

    private record EligibleDeal(List<WeightedCombo> hands, double weight) {}

    private record Continuation(double valueBb, double errorBoundBb) {}

    private SixMaxForcedShoveResponderEv() {}

    /** `priorResponses` contains the observed actions before hero, in HJ-to-SB order. */
    public static Result evaluate(
            MultiwayPreflopCallGame sourceGame,
            CfrSolution solution,
            String priorResponses,
            WeightedCombo heroHand) {
        SixMaxForcedShovePolicyBridge.validateSource(sourceGame, priorResponses, false);
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(heroHand, "heroHand");
        int hero = priorResponses.length() + 1;
        Seat heroSeat = Seat.values()[hero];
        if (sourceGame.ranges().get(hero).stream()
                .noneMatch(combo -> combo.key().equals(heroHand.key())))
            throw new IllegalArgumentException(
                    "Hero hand is absent from the responding seat's range");

        List<EligibleDeal> deals = new ArrayList<>();
        for (var outcome : sourceGame.chanceOutcomes(sourceGame.initialState())) {
            List<WeightedCombo> hands = sourceGame.dealtCombos(outcome.state());
            if (!hands.get(hero).key().equals(heroHand.key())) continue;
            double reach = outcome.probability();
            for (int index = 0; index < priorResponses.length(); index++)
                reach *=
                        SixMaxForcedShovePolicyBridge.actionLikelihood(
                                solution,
                                index + 1,
                                hands.get(index + 1).key(),
                                priorResponses.substring(0, index),
                                priorResponses.charAt(index));
            if (reach == 0) continue;
            if (deals.size() == MAX_REACHABLE_DEALS)
                throw new IllegalArgumentException("More than 64 reachable physical deals");
            deals.add(new EligibleDeal(hands, reach));
        }
        if (deals.isEmpty())
            throw new IllegalArgumentException("Hero hand and history have no reachable deal");

        var betting = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        State state =
                betting.apply(
                        betting.initialState(),
                        new SixMaxPreflopBetting.Move(Seat.UTG, Kind.RAISE_TO, 100));
        for (int index = 0; index < priorResponses.length(); index++)
            state =
                    applyResponse(
                            betting, state, Seat.values()[index + 1], priorResponses.charAt(index));
        if (state.actingSeat() != heroSeat)
            throw new IllegalStateException("Policy history disagrees with betting action order");
        State afterCall = applyResponse(betting, state, heroSeat, 'c');

        double mass = 0;
        double call = 0;
        double error = 0;
        for (EligibleDeal deal : deals) {
            Continuation continuation =
                    continueProfile(
                            sourceGame,
                            solution,
                            betting,
                            afterCall,
                            deal.hands(),
                            priorResponses + "c",
                            hero);
            mass += deal.weight();
            call += deal.weight() * continuation.valueBb();
            error += deal.weight() * continuation.errorBoundBb();
        }
        return new Result(
                heroSeat,
                heroHand.key(),
                deals.size(),
                state.committedBb(heroSeat) == 0 ? 0 : -state.committedBb(heroSeat),
                call / mass,
                error / mass);
    }

    private static State applyResponse(
            SixMaxPreflopBetting betting, State state, Seat seat, char response) {
        boolean call = response == 'c';
        return betting.apply(
                state,
                new SixMaxPreflopBetting.Move(seat, call ? Kind.CALL : Kind.FOLD, call ? 100 : 0));
    }

    private static Continuation continueProfile(
            MultiwayPreflopCallGame sourceGame,
            CfrSolution solution,
            SixMaxPreflopBetting betting,
            State state,
            List<WeightedCombo> hands,
            String history,
            int hero) {
        if (state.status() != Status.DECISION) {
            if (state.status() != Status.UNCONTESTED && state.status() != Status.ALL_IN_SHOWDOWN)
                throw new IllegalStateException("Forced-shove continuation reached postflop play");
            var payoff =
                    SixMaxPreflopTerminalPayoff.settle(
                            state, hands, sourceGame.rakeRule(), sourceGame.showdownOracle());
            return new Continuation(
                    payoff.utilitiesBb().get(hero), payoff.maximumPayoffStandardErrorBb());
        }
        int actor = state.actingSeat().ordinal();
        if (actor <= hero || actor != history.length() + 1)
            throw new IllegalStateException("Unexpected responder in forced-shove continuation");
        double callProbability =
                SixMaxForcedShovePolicyBridge.actionLikelihood(
                        solution, actor, hands.get(actor).key(), history, 'c');
        if (callProbability == 0)
            return continueProfile(
                    sourceGame,
                    solution,
                    betting,
                    applyResponse(betting, state, Seat.values()[actor], 'f'),
                    hands,
                    history + "f",
                    hero);
        if (callProbability == 1)
            return continueProfile(
                    sourceGame,
                    solution,
                    betting,
                    applyResponse(betting, state, Seat.values()[actor], 'c'),
                    hands,
                    history + "c",
                    hero);
        var called =
                continueProfile(
                        sourceGame,
                        solution,
                        betting,
                        applyResponse(betting, state, Seat.values()[actor], 'c'),
                        hands,
                        history + "c",
                        hero);
        var folded =
                continueProfile(
                        sourceGame,
                        solution,
                        betting,
                        applyResponse(betting, state, Seat.values()[actor], 'f'),
                        hands,
                        history + "f",
                        hero);
        return new Continuation(
                callProbability * called.valueBb() + (1 - callProbability) * folded.valueBb(),
                callProbability * called.errorBoundBb()
                        + (1 - callProbability) * folded.errorBoundBb());
    }
}
