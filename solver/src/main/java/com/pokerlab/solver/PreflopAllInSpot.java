package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A no-rake, six-seat cash preflop spot with a bounded shove/fold then call/fold continuation. */
public record PreflopAllInSpot(
        String id,
        double effectiveStackBb,
        double smallBlindBb,
        Seat firstSeat,
        Seat secondSeat,
        List<Action> priorActions,
        List<WeightedCombo> firstRange,
        List<WeightedCombo> secondRange) {
    public enum Seat {
        UTG,
        HJ,
        CO,
        BTN,
        SB,
        BB
    }

    public enum ActionKind {
        POST_SMALL_BLIND,
        POST_BIG_BLIND,
        RAISE_TO,
        FOLD
    }

    public record Action(Seat seat, ActionKind kind, double amountBb) {
        public Action {
            Objects.requireNonNull(seat, "seat");
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(amountBb)
                    || (kind == ActionKind.FOLD ? amountBb != 0 : amountBb <= 0))
                throw new IllegalArgumentException("Invalid action amount");
        }
    }

    public PreflopAllInSpot {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Spot id must be lowercase and hyphenated");
        if (!Double.isFinite(effectiveStackBb)
                || !Double.isFinite(smallBlindBb)
                || effectiveStackBb <= 1
                || smallBlindBb <= 0
                || smallBlindBb >= 1)
            throw new IllegalArgumentException("Invalid stack or small blind");
        Objects.requireNonNull(firstSeat, "firstSeat");
        Objects.requireNonNull(secondSeat, "secondSeat");
        if (firstSeat == secondSeat)
            throw new IllegalArgumentException("Players need distinct seats");
        priorActions = List.copyOf(priorActions);
        firstRange = canonicalRange(firstRange);
        secondRange = canonicalRange(secondRange);
        validateHistory(priorActions, firstSeat, secondSeat, smallBlindBb, effectiveStackBb);
        for (WeightedCombo first : firstRange) {
            if (secondRange.stream().noneMatch(second -> !first.conflictsWith(second)))
                throw new IllegalArgumentException(
                        "Every range combo needs a possible opposing combo");
        }
        for (WeightedCombo second : secondRange) {
            if (firstRange.stream().noneMatch(first -> !first.conflictsWith(second)))
                throw new IllegalArgumentException(
                        "Every range combo needs a possible opposing combo");
        }
    }

    public double firstCommittedBb() {
        return commitments().get(firstSeat);
    }

    public double secondCommittedBb() {
        return commitments().get(secondSeat);
    }

    public double deadMoneyBb() {
        Map<Seat, Double> committed = commitments();
        return committed.entrySet().stream()
                .filter(entry -> entry.getKey() != firstSeat && entry.getKey() != secondSeat)
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    public PreflopAllInGame game(PreflopEquityOracle oracle) {
        return new PreflopAllInGame(
                firstRange,
                secondRange,
                firstCommittedBb(),
                secondCommittedBb(),
                effectiveStackBb,
                deadMoneyBb(),
                oracle);
    }

    /** SHA-256 over a versioned, order-independent representation of the spot's inputs. */
    public String contentHash() {
        StringBuilder canonical =
                new StringBuilder("preflop-all-in-spot/v1|6-max|preflop|no-rake|");
        append(canonical, id);
        append(canonical, Double.toHexString(effectiveStackBb));
        append(canonical, Double.toHexString(smallBlindBb));
        append(canonical, firstSeat.name());
        append(canonical, secondSeat.name());
        for (Action action : priorActions) {
            append(canonical, action.seat().name());
            append(canonical, action.kind().name());
            append(canonical, Double.toHexString(action.amountBb()));
        }
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
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private Map<Seat, Double> commitments() {
        Map<Seat, Double> committed = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) committed.put(seat, 0.0);
        for (Action action : priorActions) {
            if (action.kind() != ActionKind.FOLD) committed.put(action.seat(), action.amountBb());
        }
        return committed;
    }

    private static List<WeightedCombo> canonicalRange(List<WeightedCombo> range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each player needs a nonempty range");
        Set<String> seen = new HashSet<>();
        List<WeightedCombo> sorted = new ArrayList<>(range.size());
        for (WeightedCombo combo : range) {
            if (combo == null || !seen.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate combo in range");
            sorted.add(combo);
        }
        sorted.sort(Comparator.comparing(WeightedCombo::key));
        return List.copyOf(sorted);
    }

    private static void validateHistory(
            List<Action> actions,
            Seat firstSeat,
            Seat secondSeat,
            double smallBlind,
            double stack) {
        if (actions.size() < 8
                || Objects.requireNonNull(actions.get(0), "small blind post").seat() != Seat.SB
                || actions.get(0).kind() != ActionKind.POST_SMALL_BLIND
                || actions.get(0).amountBb() != smallBlind
                || Objects.requireNonNull(actions.get(1), "big blind post").seat() != Seat.BB
                || actions.get(1).kind() != ActionKind.POST_BIG_BLIND
                || actions.get(1).amountBb() != 1)
            throw new IllegalArgumentException("History must start with the specified blind posts");
        Map<Seat, Double> committed = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) committed.put(seat, 0.0);
        Set<Seat> folded = EnumSet.noneOf(Seat.class);
        double currentBet = 1;
        for (int index = 0; index < actions.size(); index++) {
            Action action = Objects.requireNonNull(actions.get(index), "action");
            if (folded.contains(action.seat()))
                throw new IllegalArgumentException("Folded seat cannot act again");
            switch (action.kind()) {
                case POST_SMALL_BLIND, POST_BIG_BLIND -> {
                    if (index > 1) throw new IllegalArgumentException("Blinds must post first");
                    committed.put(action.seat(), action.amountBb());
                }
                case RAISE_TO -> {
                    if (action.amountBb() <= currentBet || action.amountBb() > stack)
                        throw new IllegalArgumentException("Invalid raise-to commitment");
                    committed.put(action.seat(), action.amountBb());
                    currentBet = action.amountBb();
                }
                case FOLD -> folded.add(action.seat());
            }
        }
        if (folded.contains(firstSeat)
                || folded.contains(secondSeat)
                || folded.size() != 4
                || actions.get(actions.size() - 1).seat() != secondSeat
                || actions.get(actions.size() - 1).kind() != ActionKind.RAISE_TO
                || committed.get(firstSeat) >= committed.get(secondSeat)
                || committed.get(secondSeat) >= stack)
            throw new IllegalArgumentException(
                    "History must end with two live seats facing a raise");
        var replayed = SixMaxPreflopBetting.forHistory(stack, smallBlind, actions).replay(actions);
        if (replayed.status() != SixMaxPreflopBetting.Status.DECISION
                || replayed.actingSeat() != firstSeat
                || replayed.liveSeats().size() != 2
                || !replayed.liveSeats().contains(secondSeat))
            throw new IllegalArgumentException(
                    "History must leave the first player facing the second player's raise");
    }
}
