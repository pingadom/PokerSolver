package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Carries a solved forced-shove response profile into the six-seat betting rules at BB's final
 * decision. Earlier action likelihoods update each acting seat's range before physical blockers are
 * applied by {@link SixMaxFinalDecisionEv}.
 */
public final class SixMaxForcedShovePolicyBridge {
    private static final List<Seat> SEATS = List.of(Seat.values());
    private static final List<Double> COMMITTED = List.of(100.0, 0.0, 0.0, 0.0, 0.5, 1.0);

    private SixMaxForcedShovePolicyBridge() {}

    /**
     * `priorResponses` has four `c`/`f` actions in HJ, CO, BTN, SB order. The source game's
     * showdown oracle and rake rule are reused for payoff parity.
     */
    public static SixMaxFinalDecisionEv.Result evaluateLastBb(
            MultiwayPreflopCallGame sourceGame,
            CfrSolution solution,
            String priorResponses,
            WeightedCombo heroHand) {
        var ranges = actionConditionedRanges(sourceGame, solution, priorResponses);
        var betting = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        var state =
                betting.apply(
                        betting.initialState(),
                        new SixMaxPreflopBetting.Move(Seat.UTG, Kind.RAISE_TO, 100));
        for (int index = 0; index < priorResponses.length(); index++) {
            Seat seat = SEATS.get(index + 1);
            boolean call = priorResponses.charAt(index) == 'c';
            state =
                    betting.apply(
                            state,
                            new SixMaxPreflopBetting.Move(
                                    seat, call ? Kind.CALL : Kind.FOLD, call ? 100 : 0));
        }
        return SixMaxFinalDecisionEv.evaluate(
                betting,
                state,
                heroHand,
                ranges,
                sourceGame.rakeRule(),
                sourceGame.showdownOracle());
    }

    /** Per-seat weights after the observed prior policy actions, before card-blocker filtering. */
    public static List<List<WeightedCombo>> actionConditionedRanges(
            MultiwayPreflopCallGame sourceGame, CfrSolution solution, String priorResponses) {
        validateSource(sourceGame, priorResponses, true);
        Objects.requireNonNull(solution, "solution");
        List<Set<String>> reachable = new ArrayList<>();
        for (Seat ignored : SEATS) reachable.add(new HashSet<>());
        for (var outcome : sourceGame.chanceOutcomes(sourceGame.initialState())) {
            var hands = sourceGame.dealtCombos(outcome.state());
            for (int seat = 0; seat < SEATS.size(); seat++)
                reachable.get(seat).add(hands.get(seat).key());
        }
        List<List<WeightedCombo>> conditioned = new ArrayList<>();
        for (int seat = 0; seat < SEATS.size(); seat++) {
            List<WeightedCombo> combos = new ArrayList<>();
            List<Double> logWeights = new ArrayList<>();
            for (WeightedCombo combo : sourceGame.ranges().get(seat)) {
                if (!reachable.get(seat).contains(combo.key())) continue;
                double likelihood =
                        seat >= 1 && seat <= 4
                                ? actionLikelihood(
                                        solution,
                                        seat,
                                        combo.key(),
                                        priorResponses.substring(0, seat - 1),
                                        priorResponses.charAt(seat - 1))
                                : 1;
                if (likelihood == 0) continue;
                combos.add(combo);
                logWeights.add(Math.log(combo.weight()) + Math.log(likelihood));
            }
            if (combos.isEmpty())
                throw new IllegalArgumentException(
                        "Observed history has no range for " + SEATS.get(seat));
            double largest =
                    logWeights.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            List<WeightedCombo> updated = new ArrayList<>();
            for (int index = 0; index < combos.size(); index++) {
                WeightedCombo combo = combos.get(index);
                double weight = Math.exp(logWeights.get(index) - largest);
                if (weight > 0)
                    updated.add(new WeightedCombo(combo.first(), combo.second(), weight));
            }
            conditioned.add(List.copyOf(updated));
        }
        return List.copyOf(conditioned);
    }

    static double actionLikelihood(
            CfrSolution solution, int seat, String combo, String publicPrefix, char action) {
        Map<String, Double> strategy = solution.at(seat, combo + ":" + publicPrefix);
        if (strategy == null || strategy.size() != 2)
            throw new IllegalArgumentException(
                    "Missing forced-shove strategy for observed history");
        Double call = strategy.get("c");
        Double fold = strategy.get("f");
        if (call == null
                || fold == null
                || !Double.isFinite(call)
                || !Double.isFinite(fold)
                || call < 0
                || fold < 0
                || call > 1
                || fold > 1
                || Math.abs(call + fold - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid forced-shove strategy probabilities");
        return action == 'c' ? call : fold;
    }

    static void validateSource(
            MultiwayPreflopCallGame sourceGame, String priorResponses, boolean lastBbOnly) {
        Objects.requireNonNull(sourceGame, "sourceGame");
        if (priorResponses == null || !priorResponses.matches(lastBbOnly ? "[cf]{4}" : "[cf]{0,4}"))
            throw new IllegalArgumentException("Expected HJ-to-SB call/fold responses");
        if (!sourceGame.seats().equals(SEATS)
                || !sourceGame.committedBb().equals(COMMITTED)
                || sourceGame.stacksBb().stream().anyMatch(stack -> stack != 100)
                || sourceGame.deadMoneyBb() != 0)
            throw new IllegalArgumentException("Source game is not the six-seat 100bb UTG shove");
    }
}
