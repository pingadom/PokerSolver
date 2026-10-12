package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.*;

/** Exact fixed-flop checkdown with all twelve physical hole cards retained as blockers. */
public final class ExactDeadCardFlopShowdown {
    public static final String ALGORITHM = "EXACT_TWO_ACTIVE_SIX_DEALT_FIXED_FLOP_RUNOUTS/v1";
    public static final long RUNOUTS = 666; // C(37,2).
    public static final int FLOPS_PER_DEAL = 9880; // C(40,3).

    public record Counts(
            int firstSeat,
            int secondSeat,
            long firstWins,
            long secondWins,
            long ties,
            long runouts,
            long handEvaluations) {
        public Counts {
            if (firstSeat < 0
                    || firstSeat >= secondSeat
                    || secondSeat >= 6
                    || firstWins < 0
                    || secondWins < 0
                    || ties < 0
                    || runouts != RUNOUTS
                    || firstWins > runouts
                    || secondWins > runouts
                    || ties > runouts
                    || firstWins + secondWins + ties != runouts
                    || handEvaluations != 2 * runouts)
                throw new IllegalArgumentException("Invalid fixed-flop physical counts");
        }

        public MultiwayShowdownEstimate estimate() {
            return shares(firstSeat, secondSeat, firstWins, secondWins, ties, runouts);
        }
    }

    /** Counts over board/flop partitions, derived by subtraction; these are not new evaluations. */
    public record ResidualCounts(
            int firstSeat,
            int secondSeat,
            long firstWins,
            long secondWins,
            long ties,
            long boardFlopPartitions) {
        public ResidualCounts {
            if (firstSeat < 0
                    || firstSeat >= secondSeat
                    || secondSeat >= 6
                    || firstWins < 0
                    || secondWins < 0
                    || ties < 0
                    || boardFlopPartitions
                            != 10 * ExactDeadCardHeadsUpShowdown.BOARDS_PER_DEAL - RUNOUTS
                    || firstWins > boardFlopPartitions
                    || secondWins > boardFlopPartitions
                    || ties > boardFlopPartitions
                    || firstWins + secondWins + ties != boardFlopPartitions)
                throw new IllegalArgumentException("Invalid residual flop-partition counts");
        }

        public MultiwayShowdownEstimate estimate() {
            return shares(firstSeat, secondSeat, firstWins, secondWins, ties, boardFlopPartitions);
        }
    }

    private ExactDeadCardFlopShowdown() {}

    static List<String> canonicalFlop(List<String> flop) {
        if (flop == null
                || flop.size() != 3
                || flop.stream().anyMatch(Objects::isNull)
                || flop.stream().anyMatch(c -> !c.matches("[2-9TJQKA][cdhs]"))
                || flop.stream().distinct().count() != 3)
            throw new IllegalArgumentException("Three distinct compact flop cards required");
        return flop.stream().sorted().toList();
    }

    static boolean compatible(List<WeightedCombo> hands, List<String> flop) {
        return hands.stream()
                .noneMatch(
                        h ->
                                flop.contains(h.first().compact())
                                        || flop.contains(h.second().compact()));
    }

    public static Counts enumerate(List<WeightedCombo> hands, int activeMask, List<String> flop) {
        flop = canonicalFlop(flop);
        if (hands == null
                || hands.size() != 6
                || (activeMask & ~63) != 0
                || Integer.bitCount(activeMask) != 2)
            throw new IllegalArgumentException("Six dealt hands and two active seats required");
        var remaining = new Deck();
        for (var hand : hands)
            if (hand == null || !remaining.remove(hand.first()) || !remaining.remove(hand.second()))
                throw new IllegalArgumentException("Twelve distinct dealt cards required");
        for (String text : flop)
            if (!remaining.remove(Card.parse(text)))
                throw new IllegalArgumentException("Flop collides with a dealt card");
        int first = Integer.numberOfTrailingZeros(activeMask);
        int second = Integer.numberOfTrailingZeros(activeMask & (activeMask - 1));
        Card[] a = new Card[7], b = new Card[7];
        a[0] = hands.get(first).first();
        a[1] = hands.get(first).second();
        b[0] = hands.get(second).first();
        b[1] = hands.get(second).second();
        for (int i = 0; i < 3; i++) a[i + 2] = b[i + 2] = Card.parse(flop.get(i));
        var deck = remaining.cards();
        long wins = 0, losses = 0, ties = 0;
        for (int i = 0; i < deck.size(); i++)
            for (int j = i + 1; j < deck.size(); j++) {
                a[5] = b[5] = deck.get(i);
                a[6] = b[6] = deck.get(j);
                int x = HandEvaluator.evaluateBestScore(a), y = HandEvaluator.evaluateBestScore(b);
                if (x > y) wins++;
                else if (x < y) losses++;
                else ties++;
            }
        return new Counts(first, second, wins, losses, ties, RUNOUTS, 2 * RUNOUTS);
    }

    public static ResidualCounts residual(
            ExactDeadCardHeadsUpShowdown.Counts full, Counts selected) {
        Objects.requireNonNull(full);
        Objects.requireNonNull(selected);
        if (full.firstSeat() != selected.firstSeat() || full.secondSeat() != selected.secondSeat())
            throw new IllegalArgumentException("Flop and full-board actors differ");
        // Each unordered five-card board has ten possible unordered flop partitions.
        return new ResidualCounts(
                full.firstSeat(),
                full.secondSeat(),
                10 * full.firstWins() - selected.firstWins(),
                10 * full.secondWins() - selected.secondWins(),
                10 * full.ties() - selected.ties(),
                10 * full.boards() - RUNOUTS);
    }

    private static MultiwayShowdownEstimate shares(
            int first, int second, long wins, long losses, long ties, long count) {
        double[] shares = new double[6];
        shares[first] = (wins + .5 * ties) / count;
        shares[second] = (losses + .5 * ties) / count;
        return new MultiwayShowdownEstimate(shares, new double[6], count);
    }
}
