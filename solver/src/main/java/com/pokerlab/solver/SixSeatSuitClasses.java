package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Suit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;

/** Global suit permutations preserve showdown shares for every ordered seat and active mask. */
final class SixSeatSuitClasses {
    private static final List<List<Suit>> PERMUTATIONS =
            permutations(List.of(), List.of(Suit.values()));

    private SixSeatSuitClasses() {}

    /** Assign each logical world the first representative index in its suit class. */
    static List<Integer> representatives(SixSeatRangePreflight.Result input) {
        var firstIndex = new HashMap<String, Integer>();
        var result = new ArrayList<Integer>();
        for (var deal : input.deals()) {
            String key = canonicalKey(deal.hands());
            int index = result.size();
            result.add(firstIndex.computeIfAbsent(key, ignored -> index));
        }
        return List.copyOf(result);
    }

    private static List<List<Suit>> permutations(List<Suit> prefix, List<Suit> remaining) {
        if (remaining.isEmpty()) return List.of(List.copyOf(prefix));
        var result = new ArrayList<List<Suit>>();
        for (Suit suit : remaining) {
            var next = new ArrayList<>(prefix);
            next.add(suit);
            var tail = new ArrayList<>(remaining);
            tail.remove(suit);
            result.addAll(permutations(next, tail));
        }
        return List.copyOf(result);
    }

    static String canonicalKey(List<WeightedCombo> hands) {
        String smallest = null;
        for (var permutation : PERMUTATIONS) {
            var candidate = new StringJoiner("|");
            for (var hand : hands) {
                var relabeled =
                        new WeightedCombo(
                                rename(hand.first(), permutation),
                                rename(hand.second(), permutation),
                                1);
                candidate.add(relabeled.key());
            }
            String key = candidate.toString();
            if (smallest == null || key.compareTo(smallest) < 0) smallest = key;
        }
        return smallest;
    }

    private static Card rename(Card card, List<Suit> permutation) {
        return Card.of(card.rank(), permutation.get(card.suit().ordinal()));
    }
}
