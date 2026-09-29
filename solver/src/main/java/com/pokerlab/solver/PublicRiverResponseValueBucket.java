package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Research-only BB river observation based on bet-minus-check EV under a declared fixed BTN call
 * response. Encoding the sign makes in-model action separation mechanical, not strategic proof.
 */
public final class PublicRiverResponseValueBucket {
    private PublicRiverResponseValueBucket() {}

    public static String key(
            List<Card> board,
            WeightedCombo bigBlind,
            List<WeightedCombo> buttonPosterior,
            double halfPotBb,
            double riverBetBb,
            PhysicalActionBeliefAudit.ResponseModel assumedResponse) {
        return PublicBoardBucket.coarseKey(board, bigBlind)
                + "v"
                + band(
                        betIncrement(
                                board,
                                bigBlind,
                                buttonPosterior,
                                halfPotBb,
                                riverBetBb,
                                assumedResponse));
    }

    /** Exact weighted bet-minus-check value on the public river under the stated response. */
    public static double betIncrement(
            List<Card> board,
            WeightedCombo bigBlind,
            List<WeightedCombo> buttonPosterior,
            double halfPotBb,
            double riverBetBb,
            PhysicalActionBeliefAudit.ResponseModel assumedResponse) {
        if (board == null
                || board.size() != 5
                || bigBlind == null
                || buttonPosterior == null
                || assumedResponse == null
                || !Double.isFinite(halfPotBb)
                || halfPotBb <= 0
                || !Double.isFinite(riverBetBb)
                || riverBetBb <= 0)
            throw new IllegalArgumentException("Invalid river response-value observation");
        Set<Card> distinct = new HashSet<>(board);
        distinct.add(bigBlind.first());
        distinct.add(bigBlind.second());
        if (distinct.size() != 7)
            throw new IllegalArgumentException("Board conflicts with BB hand");
        int bbScore = score(bigBlind, board);
        double legalWeight = 0;
        double weightedIncrement = 0;
        for (WeightedCombo button : buttonPosterior) {
            if (button == null)
                throw new IllegalArgumentException("BTN posterior contains a null combo");
            if (button.conflictsWith(bigBlind)
                    || board.contains(button.first())
                    || board.contains(button.second())) continue;
            int sign = Integer.compare(bbScore, score(button, board));
            double increment =
                    assumedResponse == PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL
                                    || PhysicalRiverPairCallResponse.calls(button, board)
                            ? riverBetBb * sign
                            : halfPotBb * (1 - sign);
            legalWeight += button.weight();
            weightedIncrement += button.weight() * increment;
        }
        if (legalWeight == 0)
            throw new IllegalArgumentException("No legal BTN combo remains on river board");
        return weightedIncrement / legalWeight;
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

    private static int band(double increment) {
        if (increment <= -2) return 0;
        if (increment < -1e-12) return 1;
        if (increment <= 1e-12) return 2;
        if (increment < 2) return 3;
        return 4;
    }
}
