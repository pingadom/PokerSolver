package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-step BTN call/fold best response to a declared BB river betting likelihood. BB's candidate
 * range is updated from its own observed preflop/flop/turn actions and public blockers. This is not
 * a full-game equilibrium or a response to the held-out BB bucket policy.
 */
public final class StrategicRiverCallPolicy implements RiverCallPolicy {
    private final List<WeightedCombo> bigBlindAfterCall;
    private final PostflopActionBelief postflopBelief;
    private final double highCardBetProbability;
    private final double pairBetProbability;
    private final double halfPotBb;
    private final double riverBetBb;
    private final String definition;

    public StrategicRiverCallPolicy(
            List<WeightedCombo> bigBlindPrior,
            PreflopActionBelief preflopBelief,
            PostflopActionBelief postflopBelief,
            double highCardBetProbability,
            double pairBetProbability,
            double halfPotBb,
            double riverBetBb) {
        if (bigBlindPrior == null
                || bigBlindPrior.isEmpty()
                || preflopBelief == null
                || postflopBelief == null
                || !probability(highCardBetProbability)
                || !probability(pairBetProbability)
                || !Double.isFinite(halfPotBb)
                || halfPotBb <= 0
                || !Double.isFinite(riverBetBb)
                || riverBetBb <= 0)
            throw new IllegalArgumentException("Invalid strategic river response model");
        this.bigBlindAfterCall =
                preflopBelief.posteriorWeights(
                        List.copyOf(bigBlindPrior),
                        PreflopActionBelief.ObservedAction.BIG_BLIND_CALL);
        this.postflopBelief = postflopBelief;
        this.highCardBetProbability = highCardBetProbability;
        this.pairBetProbability = pairBetProbability;
        this.halfPotBb = halfPotBb;
        this.riverBetBb = riverBetBb;
        this.definition =
                "river-call-best-response/v1|"
                        + MultiwayCallSpot.sha256(
                                this.bigBlindAfterCall.stream()
                                        .sorted(Comparator.comparing(WeightedCombo::key))
                                        .map(
                                                combo ->
                                                        combo.key()
                                                                + ':'
                                                                + Double.toHexString(
                                                                        combo.weight()))
                                        .collect(Collectors.joining("|")))
                        + '|'
                        + MultiwayCallSpot.sha256(preflopBelief.contentDefinition())
                        + '|'
                        + MultiwayCallSpot.sha256(postflopBelief.contentDefinition())
                        + '|'
                        + Double.toHexString(highCardBetProbability)
                        + '|'
                        + Double.toHexString(pairBetProbability)
                        + '|'
                        + Double.toHexString(halfPotBb)
                        + '|'
                        + Double.toHexString(riverBetBb);
    }

    @Override
    public String definition() {
        return definition;
    }

    /** BTN calls exactly when its conditional call value is nonnegative. */
    @Override
    public double callProbability(
            WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState) {
        return callMinusFoldBb(button, board, publicRiverState) >= 0 ? 1 : 0;
    }

    public double callMinusFoldBb(
            WeightedCombo button, List<Card> board, PublicRiverHistory publicRiverState) {
        if (button == null
                || board == null
                || board.size() != 5
                || publicRiverState == null
                || publicRiverState.river() == null
                || !publicRiverState.riverHistory().isEmpty()
                || !board.equals(publicRiverState.board()))
            throw new IllegalArgumentException("Expected first public river decision");
        Set<Card> cards = new HashSet<>(board);
        cards.add(button.first());
        cards.add(button.second());
        if (cards.size() != 7)
            throw new IllegalArgumentException("BTN combo conflicts with public board");
        int buttonScore = score(button, board);
        double total = 0;
        double weightedCallValue = 0;
        for (WeightedCombo candidate :
                postflopBelief.posteriorWeights(bigBlindAfterCall, publicRiverState, true)) {
            if (candidate.conflictsWith(button)) continue;
            double betLikelihood =
                    PhysicalRiverPairCallResponse.calls(candidate, board)
                            ? pairBetProbability
                            : highCardBetProbability;
            double weight = candidate.weight() * betLikelihood;
            int sign = Integer.compare(buttonScore, score(candidate, board));
            weightedCallValue += weight * (halfPotBb + (halfPotBb + riverBetBb) * sign);
            total += weight;
        }
        if (total == 0) throw new IllegalArgumentException("No legal BB hand after blockers");
        return weightedCallValue / total;
    }

    private static boolean probability(double value) {
        return Double.isFinite(value) && value > 0 && value < 1;
    }

    private static int score(WeightedCombo hand, List<Card> board) {
        return HandEvaluator.evaluateBestScore(
                hand.first(),
                hand.second(),
                board.get(0),
                board.get(1),
                board.get(2),
                board.get(3),
                board.get(4));
    }
}
