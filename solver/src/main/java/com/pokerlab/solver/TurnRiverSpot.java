package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
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

/** A small, exact, no-rake turn-to-river endgame with one bet size on each street. */
public record TurnRiverSpot(
        List<Card> turnBoard,
        double potBb,
        double remainingStackBb,
        double turnBetBb,
        double riverBetBb,
        List<WeightedCombo> firstRange,
        List<WeightedCombo> secondRange) {
    public TurnRiverSpot {
        if (turnBoard == null
                || turnBoard.size() != 4
                || turnBoard.stream().anyMatch(Objects::isNull)
                || new HashSet<>(turnBoard).size() != 4)
            throw new IllegalArgumentException("Turn board needs four distinct cards");
        if (!Double.isFinite(potBb)
                || !Double.isFinite(remainingStackBb)
                || !Double.isFinite(turnBetBb)
                || !Double.isFinite(riverBetBb)
                || potBb <= 0
                || remainingStackBb <= 0
                || turnBetBb <= 0
                || riverBetBb <= 0
                || turnBetBb + riverBetBb > remainingStackBb)
            throw new IllegalArgumentException("Invalid pot, stack, or bet sizes");
        turnBoard = turnBoard.stream().sorted(Comparator.comparing(Card::compact)).toList();
        firstRange = canonicalRange(firstRange, turnBoard);
        secondRange = canonicalRange(secondRange, turnBoard);
        for (WeightedCombo first : firstRange)
            if (secondRange.stream().noneMatch(second -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("First-player combo has no legal opponent");
        for (WeightedCombo second : secondRange)
            if (firstRange.stream().noneMatch(first -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("Second-player combo has no legal opponent");
    }

    public TurnRiverGame game() {
        return new TurnRiverGame(this);
    }

    /** Binds board, chip rules, and both exact weighted ranges to a stable identity. */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder("turn-river-single-bet/v1|no-rake|");
        append(canonical, Double.toHexString(potBb));
        append(canonical, Double.toHexString(remainingStackBb));
        append(canonical, Double.toHexString(turnBetBb));
        append(canonical, Double.toHexString(riverBetBb));
        for (Card card : turnBoard) append(canonical, card.compact());
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
                throw new IllegalArgumentException("Range combo conflicts with the board");
            sorted.add(combo);
        }
        sorted.sort(Comparator.comparing(WeightedCombo::key));
        return List.copyOf(sorted);
    }
}
