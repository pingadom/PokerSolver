package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;

/** Exact two-active-player checkdown with all six physical hands removed from the board deck. */
public final class ExactDeadCardHeadsUpShowdown {
    public static final String ALGORITHM = "EXACT_TWO_ACTIVE_SIX_DEALT_BOARD_ENUMERATION/v1";
    public static final long BOARDS_PER_DEAL = 658008; // C(40,5), including eight folded cards.

    public record Counts(
            int firstSeat,
            int secondSeat,
            long firstWins,
            long secondWins,
            long ties,
            long boards,
            long handEvaluations) {
        public Counts {
            if (firstSeat < 0
                    || firstSeat >= secondSeat
                    || secondSeat >= 6
                    || firstWins < 0
                    || secondWins < 0
                    || ties < 0
                    || boards != BOARDS_PER_DEAL
                    || firstWins > boards
                    || secondWins > boards
                    || ties > boards
                    || firstWins + secondWins + ties != boards
                    || handEvaluations != 2 * boards)
                throw new IllegalArgumentException("Invalid exact showdown counts");
        }

        public MultiwayShowdownEstimate estimate() {
            double[] shares = new double[6];
            shares[firstSeat] = (firstWins + .5 * ties) / boards;
            shares[secondSeat] = (secondWins + .5 * ties) / boards;
            return new MultiwayShowdownEstimate(shares, new double[6], boards);
        }
    }

    private ExactDeadCardHeadsUpShowdown() {}

    public static Counts enumerate(List<WeightedCombo> dealt, int activeMask) {
        if (dealt == null
                || dealt.size() != 6
                || (activeMask & ~63) != 0
                || Integer.bitCount(activeMask) != 2)
            throw new IllegalArgumentException(
                    "Exactly six dealt hands and two active seats required");
        var remaining = new Deck();
        for (var hand : dealt)
            if (hand == null || !remaining.remove(hand.first()) || !remaining.remove(hand.second()))
                throw new IllegalArgumentException("All twelve dealt cards must be distinct");
        int first = Integer.numberOfTrailingZeros(activeMask);
        int second = Integer.numberOfTrailingZeros(activeMask & (activeMask - 1));
        Card[] deck = remaining.cards().toArray(Card[]::new);
        Card[] a = new Card[7], b = new Card[7];
        a[0] = dealt.get(first).first();
        a[1] = dealt.get(first).second();
        b[0] = dealt.get(second).first();
        b[1] = dealt.get(second).second();
        long wins = 0, losses = 0, ties = 0, boards = 0;
        for (int i = 0; i < deck.length - 4; i++)
            for (int j = i + 1; j < deck.length - 3; j++)
                for (int k = j + 1; k < deck.length - 2; k++)
                    for (int l = k + 1; l < deck.length - 1; l++)
                        for (int m = l + 1; m < deck.length; m++) {
                            a[2] = b[2] = deck[i];
                            a[3] = b[3] = deck[j];
                            a[4] = b[4] = deck[k];
                            a[5] = b[5] = deck[l];
                            a[6] = b[6] = deck[m];
                            int scoreA = HandEvaluator.evaluateBestScore(a);
                            int scoreB = HandEvaluator.evaluateBestScore(b);
                            if (scoreA > scoreB) wins++;
                            else if (scoreB > scoreA) losses++;
                            else ties++;
                            boards++;
                        }
        return new Counts(first, second, wins, losses, ties, boards, 2 * boards);
    }
}
