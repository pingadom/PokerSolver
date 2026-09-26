package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A bounded three-street, no-rake heads-up game with a declared public turn deck. */
public record FlopTurnRiverSpot(
        List<Card> flop,
        double potBb,
        double remainingStackBb,
        double flopBetBb,
        double turnBetBb,
        double riverBetBb,
        List<WeightedCombo> firstRange,
        List<WeightedCombo> secondRange,
        List<Card> turnCandidates) {
    public FlopTurnRiverSpot {
        if (flop == null
                || flop.size() != 3
                || flop.stream().anyMatch(Objects::isNull)
                || new HashSet<>(flop).size() != 3)
            throw new IllegalArgumentException("Flop needs three distinct cards");
        if (!Double.isFinite(potBb)
                || !Double.isFinite(remainingStackBb)
                || !Double.isFinite(flopBetBb)
                || !Double.isFinite(turnBetBb)
                || !Double.isFinite(riverBetBb)
                || potBb <= 0
                || remainingStackBb <= 0
                || flopBetBb <= 0
                || turnBetBb <= 0
                || riverBetBb <= 0
                || flopBetBb + turnBetBb + riverBetBb > remainingStackBb)
            throw new IllegalArgumentException("Invalid pot, stack, or bet sizes");
        flop = flop.stream().sorted(Comparator.comparing(Card::compact)).toList();
        firstRange = canonicalRange(firstRange, flop);
        secondRange = canonicalRange(secondRange, flop);
        if (turnCandidates == null
                || turnCandidates.isEmpty()
                || turnCandidates.stream().anyMatch(Objects::isNull)
                || new HashSet<>(turnCandidates).size() != turnCandidates.size()
                || turnCandidates.stream().anyMatch(flop::contains))
            throw new IllegalArgumentException(
                    "Turn candidates must be distinct and outside the flop");
        turnCandidates =
                turnCandidates.stream().sorted(Comparator.comparing(Card::compact)).toList();
        for (WeightedCombo first : firstRange)
            if (secondRange.stream().noneMatch(second -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("First-player combo has no legal opponent");
        for (WeightedCombo second : secondRange)
            if (firstRange.stream().noneMatch(first -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("Second-player combo has no legal opponent");
        for (WeightedCombo first : firstRange)
            for (WeightedCombo second : secondRange)
                if (!first.conflictsWith(second)
                        && turnCandidates.stream().noneMatch(card -> !blocked(card, first, second)))
                    throw new IllegalArgumentException("A joint deal has no legal turn candidate");
    }

    public FlopTurnRiverGame game() {
        return new FlopTurnRiverGame(this);
    }

    /** Replaces the restricted turn deck with all 49 cards outside the public flop. */
    public FlopTurnRiverSpot withFullTurnDeck() {
        Deck deck = new Deck();
        flop.forEach(deck::remove);
        return new FlopTurnRiverSpot(
                flop,
                potBb,
                remainingStackBb,
                flopBetBb,
                turnBetBb,
                riverBetBb,
                firstRange,
                secondRange,
                deck.cards());
    }

    /** The candidate list is part of the game, including its approximation. */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder("flop-turn-river-restricted-turn/v1|no-rake|");
        append(canonical, Double.toHexString(potBb));
        append(canonical, Double.toHexString(remainingStackBb));
        append(canonical, Double.toHexString(flopBetBb));
        append(canonical, Double.toHexString(turnBetBb));
        append(canonical, Double.toHexString(riverBetBb));
        flop.forEach(card -> append(canonical, card.compact()));
        append(canonical, "turn-candidates");
        turnCandidates.forEach(card -> append(canonical, card.compact()));
        append(canonical, "first-range");
        for (WeightedCombo combo : firstRange) {
            append(canonical, combo.key());
            append(canonical, Double.toHexString(combo.weight()));
        }
        append(canonical, "second-range");
        for (WeightedCombo combo : secondRange) {
            append(canonical, combo.key());
            append(canonical, Double.toHexString(combo.weight()));
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    static boolean blocked(Card card, WeightedCombo first, WeightedCombo second) {
        return card.equals(first.first())
                || card.equals(first.second())
                || card.equals(second.first())
                || card.equals(second.second());
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private static List<WeightedCombo> canonicalRange(List<WeightedCombo> range, List<Card> board) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each player needs a nonempty range");
        Set<String> seen = new HashSet<>();
        List<WeightedCombo> sorted = new ArrayList<>();
        for (WeightedCombo combo : range) {
            if (combo == null || !seen.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate combo in range");
            if (board.contains(combo.first()) || board.contains(combo.second()))
                throw new IllegalArgumentException("Range combo conflicts with the flop");
            sorted.add(combo);
        }
        sorted.sort(Comparator.comparing(WeightedCombo::key));
        return List.copyOf(sorted);
    }
}
