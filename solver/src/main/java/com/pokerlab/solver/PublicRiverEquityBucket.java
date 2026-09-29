package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * River observation from public cards, the observer's exact hand and a declared static opponent
 * prior. Action-conditioned posteriors are not inferred; this is a research abstraction only.
 */
public final class PublicRiverEquityBucket {
    private PublicRiverEquityBucket() {}

    public static String key(
            List<Card> board, WeightedCombo own, List<WeightedCombo> opponentRange) {
        return PublicBoardBucket.coarseKey(board, own)
                + "e"
                + band(showdownMargin(board, own, opponentRange));
    }

    /** Conditional win probability minus loss probability, with ties worth zero. */
    public static double showdownMargin(
            List<Card> board, WeightedCombo own, List<WeightedCombo> opponentRange) {
        if (board == null || board.size() != 5 || own == null || opponentRange == null)
            throw new IllegalArgumentException(
                    "Expected a river board, own hand and opponent prior");
        Set<Card> distinct = new HashSet<>(board);
        distinct.add(own.first());
        distinct.add(own.second());
        if (distinct.size() != 7)
            throw new IllegalArgumentException("Board conflicts with observer's hand");
        int ownScore = score(own, board);
        double weightedMargin = 0;
        double legalWeight = 0;
        for (WeightedCombo opponent : opponentRange) {
            if (opponent == null)
                throw new IllegalArgumentException("Opponent prior contains a null hand");
            if (opponent.conflictsWith(own)
                    || board.contains(opponent.first())
                    || board.contains(opponent.second())) continue;
            legalWeight += opponent.weight();
            weightedMargin += opponent.weight() * Integer.compare(ownScore, score(opponent, board));
        }
        if (legalWeight == 0)
            throw new IllegalArgumentException("No legal opponent remains on river board");
        return weightedMargin / legalWeight;
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

    private static int band(double margin) {
        if (margin <= -0.5) return 0;
        if (margin < -1e-12) return 1;
        if (margin <= 1e-12) return 2;
        if (margin < 0.5) return 3;
        return 4;
    }
}
