package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Rank;
import com.pokerlab.core.card.Suit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Counts exact-board work before solving a candidate two-player range pair. */
public final class PreflopPayoffCost {
    public record Report(
            int candidateMatchups,
            int blockedMatchups,
            int legalMatchups,
            int suitEquivalentClasses,
            long boardRunoutsWithoutReuse,
            long boardRunoutsWithReuse) {}

    private PreflopPayoffCost() {}

    /** Quick offline estimate; no boards are evaluated and no solution pack is written. */
    public static void main(String[] arguments) {
        if (arguments.length != 1)
            throw new IllegalArgumentException(
                    "Usage: PreflopPayoffCost <baseline|diverse|suit-probe>");
        PreflopAllInSpot spot =
                switch (arguments[0]) {
                    case "baseline" -> ValidationSpot.create();
                    case "diverse" -> DiverseValidationSpot.create();
                    case "suit-probe" -> suitExpandedProbe();
                    default -> throw new IllegalArgumentException("Unknown validation spot");
                };
        Report cost = assess(spot);
        System.out.printf(
                "%s: %d candidates, %d blocked, %d legal, %d suit classes; %,d board runouts without reuse versus %,d with reuse%n",
                spot.id(),
                cost.candidateMatchups(),
                cost.blockedMatchups(),
                cost.legalMatchups(),
                cost.suitEquivalentClasses(),
                cost.boardRunoutsWithoutReuse(),
                cost.boardRunoutsWithReuse());
    }

    public static Report assess(PreflopAllInSpot spot) {
        Objects.requireNonNull(spot, "spot");
        int candidate = Math.multiplyExact(spot.firstRange().size(), spot.secondRange().size());
        int legal = 0;
        Set<Integer> classes = new HashSet<>();
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (!first.conflictsWith(second)) {
                    legal++;
                    classes.add(ExactPreflopEquityOracle.canonicalMatchupKey(first, second));
                }
            }
        }
        return new Report(
                candidate,
                candidate - legal,
                legal,
                classes.size(),
                (long) legal * ExactPreflopEquityOracle.BOARD_RUNOUTS,
                (long) classes.size() * ExactPreflopEquityOracle.BOARD_RUNOUTS);
    }

    /** All physical AA versus KK pairings; a cost probe, not poker training content. */
    static PreflopAllInSpot suitExpandedProbe() {
        PreflopAllInSpot baseline = ValidationSpot.create();
        return new PreflopAllInSpot(
                "suit-equivalence-cost-probe",
                baseline.effectiveStackBb(),
                baseline.smallBlindBb(),
                baseline.firstSeat(),
                baseline.secondSeat(),
                baseline.priorActions(),
                pairs(Rank.ACE),
                pairs(Rank.KING));
    }

    private static List<WeightedCombo> pairs(Rank rank) {
        List<WeightedCombo> combos = new ArrayList<>(6);
        Suit[] suits = Suit.values();
        for (int first = 0; first < suits.length; first++) {
            for (int second = first + 1; second < suits.length; second++) {
                combos.add(
                        new WeightedCombo(
                                Card.of(rank, suits[first]), Card.of(rank, suits[second]), 1));
            }
        }
        return List.copyOf(combos);
    }
}
