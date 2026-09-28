package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deliberately coarse, observable board/own-hand features for an offline abstraction experiment. A
 * bucket is not an equity estimate or a claim that its constituent boards are interchangeable.
 */
public final class PublicBoardBucket {
    private PublicBoardBucket() {}

    public static String key(List<Card> board, WeightedCombo own) {
        Features features = features(board, own);
        return "m"
                + features.madeCategory()
                + "p"
                + (features.boardPaired() ? 1 : 0)
                + "s"
                + (features.suitMaximum() >= 3 ? 1 : 0)
                + "f"
                + (features.ownFlushDraw() ? 1 : 0)
                + "d"
                + (features.straightDraw() ? 1 : 0)
                + "h"
                + (features.topRank() >= 12 ? 1 : 0);
    }

    public static String coarseKey(List<Card> board, WeightedCombo own) {
        Features features = features(board, own);
        return "m" + features.madeCategory() + "p" + (features.boardPaired() ? 1 : 0);
    }

    private record Features(
            int madeCategory,
            boolean boardPaired,
            int suitMaximum,
            boolean ownFlushDraw,
            boolean straightDraw,
            int topRank) {}

    private static Features features(List<Card> board, WeightedCombo own) {
        if (board == null || board.size() < 3 || board.size() > 5 || own == null)
            throw new IllegalArgumentException("Expected three to five public cards and one hand");
        Set<Card> distinct = new HashSet<>(board);
        distinct.add(own.first());
        distinct.add(own.second());
        if (distinct.size() != board.size() + 2)
            throw new IllegalArgumentException("Board conflicts with the observer's hand");
        Card[] cards = new Card[board.size() + 2];
        cards[0] = own.first();
        cards[1] = own.second();
        for (int index = 0; index < board.size(); index++) cards[index + 2] = board.get(index);
        int madeCategory =
                Math.min(4, HandEvaluator.evaluateBest(cards).rank().category().strength());
        int[] boardSuits = new int[4];
        int boardRanks = 0;
        int topRank = 0;
        for (Card card : board) {
            boardSuits[card.suit().ordinal()]++;
            boardRanks |= 1 << card.rank().value();
            topRank = Math.max(topRank, card.rank().value());
        }
        int suitMaximum = 0;
        for (int count : boardSuits) suitMaximum = Math.max(suitMaximum, count);
        boolean boardPaired = Integer.bitCount(boardRanks) < board.size();
        boolean ownFlushDraw =
                boardSuits[own.first().suit().ordinal()]
                                        + (own.first().suit() == own.second().suit() ? 2 : 1)
                                >= 4
                        || boardSuits[own.second().suit().ordinal()]
                                        + (own.first().suit() == own.second().suit() ? 2 : 1)
                                >= 4;
        int allRanks =
                boardRanks | 1 << own.first().rank().value() | 1 << own.second().rank().value();
        boolean straightDraw = false;
        for (int high = 5; high <= 14; high++) {
            int window = 0;
            for (int rank = high - 4; rank <= high; rank++) window |= 1 << (rank == 1 ? 14 : rank);
            if (Integer.bitCount(allRanks & window) >= 4) {
                straightDraw = true;
                break;
            }
        }
        return new Features(
                madeCategory, boardPaired, suitMaximum, ownFlushDraw, straightDraw, topRank);
    }
}
