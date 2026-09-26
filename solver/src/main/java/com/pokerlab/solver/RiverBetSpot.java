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

/** A fixed-board, no-rake heads-up river endgame with one permitted bet size. */
public record RiverBetSpot(
        String id,
        PreflopAllInSpot.Seat firstSeat,
        PreflopAllInSpot.Seat secondSeat,
        List<Card> board,
        double potBb,
        double remainingStackBb,
        double betBb,
        List<WeightedCombo> firstRange,
        List<WeightedCombo> secondRange) {
    public RiverBetSpot {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Spot id must be lowercase and hyphenated");
        Objects.requireNonNull(firstSeat, "firstSeat");
        Objects.requireNonNull(secondSeat, "secondSeat");
        if (firstSeat == secondSeat) throw new IllegalArgumentException("Seats must differ");
        if (!Double.isFinite(potBb)
                || !Double.isFinite(remainingStackBb)
                || !Double.isFinite(betBb)
                || potBb <= 0
                || remainingStackBb <= 0
                || betBb <= 0
                || betBb > remainingStackBb)
            throw new IllegalArgumentException("Invalid pot, stack, or bet size");
        if (board == null || board.size() != 5 || board.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A river spot needs five board cards");
        if (new HashSet<>(board).size() != 5)
            throw new IllegalArgumentException("Board cards must be distinct");
        board = board.stream().sorted(Comparator.comparing(Card::compact)).toList();
        firstRange = canonicalRange(firstRange, board);
        secondRange = canonicalRange(secondRange, board);
        for (WeightedCombo first : firstRange)
            if (secondRange.stream().noneMatch(second -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("First-player combo has no legal opponent");
        for (WeightedCombo second : secondRange)
            if (firstRange.stream().noneMatch(first -> !first.conflictsWith(second)))
                throw new IllegalArgumentException("Second-player combo has no legal opponent");
    }

    public RiverBetGame game() {
        return new RiverBetGame(this);
    }

    /** Hashes all public rules and weighted private ranges; list order is canonical. */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder("river-single-bet/v1|no-rake|");
        append(canonical, id);
        append(canonical, firstSeat.name());
        append(canonical, secondSeat.name());
        append(canonical, Double.toHexString(potBb));
        append(canonical, Double.toHexString(remainingStackBb));
        append(canonical, Double.toHexString(betBb));
        for (Card card : board) append(canonical, card.compact());
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

    private static List<WeightedCombo> canonicalRange(List<WeightedCombo> range, List<Card> board) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each player needs a nonempty range");
        Set<String> seen = new HashSet<>();
        List<WeightedCombo> sorted = new ArrayList<>(range.size());
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

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }
}
