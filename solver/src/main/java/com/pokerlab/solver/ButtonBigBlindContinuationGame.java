package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A connected, no-rake BTN-versus-BB research game. Preflop action probabilities are learned in the
 * same CFR tree as a bounded flop/turn/river continuation, rather than supplied as independent
 * range likelihoods. Its declared flop and turn candidate lists are a chance abstraction.
 */
public final class ButtonBigBlindContinuationGame
        implements CfrGame<ButtonBigBlindContinuationGame.State> {
    private static final long MAX_DEAL_FLOP_TURN_COMBINATIONS = 128;

    public record State(
            WeightedCombo bigBlind,
            WeightedCombo button,
            String preflopHistory,
            int flopIndex,
            FlopTurnRiverGame.State streetState) {}

    private final List<ChanceOutcome<State>> initialDeals;
    private final List<FlopTurnRiverSpot> flopSpots;
    private final List<FlopTurnRiverGame> streetGames;
    private final double buttonFoldUtility;
    private final double bigBlindFoldUtility;
    private final String contentHash;

    public ButtonBigBlindContinuationGame(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            List<List<Card>> flopCandidates,
            List<Card> turnCandidates,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb) {
        validateRange(buttonRange);
        validateRange(bigBlindRange);
        if (flopCandidates == null || flopCandidates.isEmpty())
            throw new IllegalArgumentException("At least one public flop is required");
        Objects.requireNonNull(turnCandidates, "turnCandidates");
        long budget =
                (long) buttonRange.size()
                        * bigBlindRange.size()
                        * flopCandidates.size()
                        * turnCandidates.size();
        if (turnCandidates.isEmpty() || budget > MAX_DEAL_FLOP_TURN_COMBINATIONS)
            throw new IllegalArgumentException("Connected research chance tree exceeds its budget");
        SixMaxPreflopBetting betting =
                new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        SixMaxPreflopBetting.State beforeButton = betting.initialState();
        for (Seat seat : List.of(Seat.UTG, Seat.HJ, Seat.CO))
            beforeButton = betting.apply(beforeButton, move(seat, Kind.FOLD, 0));
        SixMaxPreflopBetting.State buttonFolds =
                betting.apply(beforeButton, move(Seat.BTN, Kind.FOLD, 0));
        buttonFolds = betting.apply(buttonFolds, move(Seat.SB, Kind.FOLD, 0));
        SixMaxPreflopBetting.State buttonOpens =
                betting.apply(beforeButton, move(Seat.BTN, Kind.RAISE_TO, 3));
        buttonOpens = betting.apply(buttonOpens, move(Seat.SB, Kind.FOLD, 0));
        SixMaxPreflopBetting.State bigBlindFolds =
                betting.apply(buttonOpens, move(Seat.BB, Kind.FOLD, 0));
        SixMaxPreflopBetting.State bigBlindCalls =
                betting.apply(buttonOpens, move(Seat.BB, Kind.CALL, 3));
        double deadMoneyBb = bigBlindCalls.potBb() - 2 * bigBlindCalls.committedBb(Seat.BB);
        if (deadMoneyBb < 0) throw new IllegalStateException("Invalid dead-money accounting");
        // The three-street game uses the centered, zero-sum utility. The small blind's
        // dead money contributes the same +dead/2 offset to each active player's real EV.
        buttonFoldUtility = buttonFolds.uncontestedProfitBb() - deadMoneyBb / 2;
        bigBlindFoldUtility = -bigBlindFolds.committedBb(Seat.BB) - deadMoneyBb / 2;

        PreflopFlopTransition transition =
                new PreflopFlopTransition(
                        betting,
                        bigBlindCalls,
                        unconditioned(Seat.BB, move(Seat.BB, Kind.CALL, 3), bigBlindRange),
                        unconditioned(Seat.BTN, move(Seat.BTN, Kind.RAISE_TO, 3), buttonRange));
        List<FlopTurnRiverSpot> spots = new ArrayList<>();
        List<FlopTurnRiverGame> games = new ArrayList<>();
        Set<String> seenFlops = new HashSet<>();
        for (List<Card> flop : flopCandidates) {
            var projection = transition.project(flop, flopBetBb, turnBetBb, riverBetBb);
            String key =
                    projection.spot().flop().stream()
                            .map(Card::compact)
                            .sorted()
                            .reduce("", (left, right) -> left + right);
            if (!seenFlops.add(key))
                throw new IllegalArgumentException("Duplicate unordered flop candidate");
            FlopTurnRiverSpot full = projection.spot();
            FlopTurnRiverSpot restricted =
                    new FlopTurnRiverSpot(
                            full.flop(),
                            full.potBb(),
                            full.remainingStackBb(),
                            full.flopBetBb(),
                            full.turnBetBb(),
                            full.riverBetBb(),
                            full.firstRange(),
                            full.secondRange(),
                            turnCandidates);
            spots.add(restricted);
            games.add(restricted.game());
        }
        flopSpots = List.copyOf(spots);
        streetGames = List.copyOf(games);

        List<ChanceOutcome<State>> deals = new ArrayList<>();
        double total = 0;
        for (WeightedCombo button : buttonRange)
            for (WeightedCombo bigBlind : bigBlindRange)
                if (!button.conflictsWith(bigBlind)) {
                    if (legalFlops(bigBlind, button).isEmpty())
                        throw new IllegalArgumentException(
                                "Every legal joint deal needs a public flop candidate");
                    double weight = button.weight() * bigBlind.weight();
                    deals.add(
                            new ChanceOutcome<>(new State(bigBlind, button, "", -1, null), weight));
                    total += weight;
                }
        if (deals.isEmpty() || !Double.isFinite(total) || total <= 0)
            throw new IllegalArgumentException("Ranges have no finite legal joint weight");
        double normalization = total;
        initialDeals =
                deals.stream()
                        .map(
                                deal ->
                                        new ChanceOutcome<>(
                                                deal.state(), deal.probability() / normalization))
                        .toList();
        contentHash = hashDefinition(buttonRange, bigBlindRange, flopSpots);
    }

    /** Stable identity for the declared abstract game, independent of input list order. */
    public String contentHash() {
        return contentHash;
    }

    public List<FlopTurnRiverSpot> flopSpots() {
        return flopSpots;
    }

    @Override
    public State initialState() {
        return new State(null, null, "", -1, null);
    }

    @Override
    public boolean isTerminal(State state) {
        if (state.preflopHistory().equals("f") || state.preflopHistory().equals("of")) return true;
        return state.flopIndex() >= 0
                && streetGames.get(state.flopIndex()).isTerminal(state.streetState());
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        return switch (state.preflopHistory()) {
            case "f" -> buttonFoldUtility;
            case "of" -> bigBlindFoldUtility;
            case "oc" -> streetGames.get(state.flopIndex()).terminalUtility(state.streetState());
            default -> throw new IllegalArgumentException("Invalid preflop history");
        };
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        if (state.bigBlind() == null) return -1;
        return switch (state.preflopHistory()) {
            case "" -> 1; // BTN opens or folds.
            case "o" -> 0; // BB calls or folds.
            case "oc" ->
                    state.flopIndex() < 0
                            ? -1
                            : streetGames.get(state.flopIndex()).currentPlayer(state.streetState());
            default -> throw new IllegalArgumentException("Invalid preflop history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance node has no legal player actions");
        return switch (state.preflopHistory()) {
            case "" -> List.of("open3", "fold");
            case "o" -> List.of("call", "fold");
            case "oc" -> streetGames.get(state.flopIndex()).legalActions(state.streetState());
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public String informationSet(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance node has no information set");
        return switch (state.preflopHistory()) {
            case "" -> "P:BTN:" + state.button().key();
            case "o" -> "P:BB:open3:" + state.bigBlind().key();
            case "oc" ->
                    "F:"
                            + flopKey(flopSpots.get(state.flopIndex()).flop())
                            + ":"
                            + streetGames
                                    .get(state.flopIndex())
                                    .informationSet(state.streetState());
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal connected-game action");
        return switch (state.preflopHistory()) {
            case "" ->
                    new State(
                            state.bigBlind(),
                            state.button(),
                            action.equals("fold") ? "f" : "o",
                            -1,
                            null);
            case "o" ->
                    new State(
                            state.bigBlind(),
                            state.button(),
                            action.equals("fold") ? "of" : "oc",
                            -1,
                            null);
            case "oc" ->
                    new State(
                            state.bigBlind(),
                            state.button(),
                            "oc",
                            state.flopIndex(),
                            streetGames
                                    .get(state.flopIndex())
                                    .afterAction(state.streetState(), action));
            default -> throw new IllegalArgumentException("Not a decision state");
        };
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.bigBlind() == null) return initialDeals;
        if (state.preflopHistory().equals("oc") && state.flopIndex() < 0) {
            List<Integer> legal = legalFlops(state.bigBlind(), state.button());
            List<ChanceOutcome<State>> outcomes = new ArrayList<>();
            for (int index : legal)
                outcomes.add(
                        new ChanceOutcome<>(
                                new State(
                                        state.bigBlind(),
                                        state.button(),
                                        "oc",
                                        index,
                                        new FlopTurnRiverGame.State(
                                                state.bigBlind(),
                                                state.button(),
                                                "",
                                                null,
                                                "",
                                                null,
                                                "")),
                                1.0 / legal.size()));
            return List.copyOf(outcomes);
        }
        if (state.preflopHistory().equals("oc") && state.flopIndex() >= 0) {
            List<ChanceOutcome<State>> outcomes = new ArrayList<>();
            for (var outcome :
                    streetGames.get(state.flopIndex()).chanceOutcomes(state.streetState()))
                outcomes.add(
                        new ChanceOutcome<>(
                                new State(
                                        state.bigBlind(),
                                        state.button(),
                                        "oc",
                                        state.flopIndex(),
                                        outcome.state()),
                                outcome.probability()));
            return List.copyOf(outcomes);
        }
        throw new IllegalArgumentException("Not a chance node");
    }

    private List<Integer> legalFlops(WeightedCombo bigBlind, WeightedCombo button) {
        List<Integer> legal = new ArrayList<>();
        for (int index = 0; index < flopSpots.size(); index++) {
            List<Card> flop = flopSpots.get(index).flop();
            if (!blocked(flop, bigBlind) && !blocked(flop, button)) legal.add(index);
        }
        return legal;
    }

    private static boolean blocked(List<Card> board, WeightedCombo combo) {
        return board.contains(combo.first()) || board.contains(combo.second());
    }

    private static String flopKey(List<Card> flop) {
        return flop.stream()
                .map(Card::compact)
                .sorted(Comparator.naturalOrder())
                .reduce("", (left, right) -> left + right);
    }

    private static PreflopFlopTransition.ActionConditioning unconditioned(
            Seat seat, SixMaxPreflopBetting.Move observed, List<WeightedCombo> prior) {
        Map<String, Double> neutral = new LinkedHashMap<>();
        for (WeightedCombo combo : prior) neutral.put(combo.key(), 1.0);
        return new PreflopFlopTransition.ActionConditioning(
                seat,
                "connected CFR tree; action reach is endogenous",
                List.of(observed),
                prior,
                neutral);
    }

    private static SixMaxPreflopBetting.Move move(Seat seat, Kind kind, double bb) {
        return new SixMaxPreflopBetting.Move(seat, kind, bb);
    }

    private static void validateRange(List<WeightedCombo> range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each seat needs a nonempty prior range");
        Set<String> keys = new HashSet<>();
        for (WeightedCombo combo : range)
            if (combo == null || !keys.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate combo in prior range");
    }

    private static String hashDefinition(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            List<FlopTurnRiverSpot> spots) {
        StringBuilder canonical =
                new StringBuilder(
                        "connected-btn-bb/v1|100bb|sb=.5|bb=1|open=3|no-rake|fixed-folds|");
        appendRange(canonical, "button", buttonRange);
        appendRange(canonical, "big-blind", bigBlindRange);
        spots.stream()
                .map(FlopTurnRiverSpot::contentHash)
                .sorted()
                .forEach(hash -> append(canonical, hash));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void appendRange(
            StringBuilder canonical, String label, List<WeightedCombo> range) {
        append(canonical, label);
        range.stream()
                .sorted(Comparator.comparing(WeightedCombo::key))
                .forEach(
                        combo -> {
                            append(canonical, combo.key());
                            append(canonical, Double.toHexString(combo.weight()));
                        });
    }

    private static void append(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }
}
