package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Research-only BTN open / BB call tree with every physical flop, turn and river. Seeded chance
 * sampling makes one traversal affordable; its sparse strategy is not a complete solution pack.
 */
public final class ButtonBigBlindPhysicalDeckGame
        implements CfrGame<ButtonBigBlindPhysicalDeckGame.State> {
    public enum InformationMode {
        EXACT_PUBLIC_CARDS,
        BOARD_BUCKETS,
        TEXTURE_BOARD_BUCKETS,
        COARSE_BOARD_BUCKETS,
        RANGE_EQUITY_RIVER_BUCKETS,
        PREFLOP_ACTION_RIVER_BUCKETS,
        POSTFLOP_ACTION_RIVER_BUCKETS
    }

    private static final int FLOPS_PER_DEAL = 17_296; // C(48, 3)
    private static final int MAX_JOINT_DEALS = 64;

    public record State(
            WeightedCombo bigBlind,
            WeightedCombo button,
            String preflopHistory,
            List<Card> flop,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory) {
        public State {
            if (flop != null) flop = List.copyOf(flop);
        }
    }

    private final List<WeightedCombo> buttonRange;
    private final List<WeightedCombo> bigBlindRange;
    private final List<ChanceOutcome<State>> initialDeals;
    private final double potBb;
    private final double buttonFoldUtility;
    private final double bigBlindFoldUtility;
    private final double flopBetBb;
    private final double turnBetBb;
    private final double riverBetBb;
    private final String contentHash;
    private final InformationMode informationMode;
    private final PreflopActionBelief actionBelief;
    private final PostflopActionBelief postflopBelief;

    public ButtonBigBlindPhysicalDeckGame(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb) {
        this(
                buttonRange,
                bigBlindRange,
                flopBetBb,
                turnBetBb,
                riverBetBb,
                InformationMode.EXACT_PUBLIC_CARDS);
    }

    public ButtonBigBlindPhysicalDeckGame(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            InformationMode informationMode) {
        this(buttonRange, bigBlindRange, flopBetBb, turnBetBb, riverBetBb, informationMode, null);
    }

    public ButtonBigBlindPhysicalDeckGame(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            InformationMode informationMode,
            PreflopActionBelief actionBelief) {
        this(
                buttonRange,
                bigBlindRange,
                flopBetBb,
                turnBetBb,
                riverBetBb,
                informationMode,
                actionBelief,
                null);
    }

    public ButtonBigBlindPhysicalDeckGame(
            List<WeightedCombo> buttonRange,
            List<WeightedCombo> bigBlindRange,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            InformationMode informationMode,
            PreflopActionBelief actionBelief,
            PostflopActionBelief postflopBelief) {
        this.informationMode = java.util.Objects.requireNonNull(informationMode, "informationMode");
        this.buttonRange = canonicalRange(buttonRange);
        this.bigBlindRange = canonicalRange(bigBlindRange);
        boolean validBeliefs =
                switch (informationMode) {
                    case PREFLOP_ACTION_RIVER_BUCKETS ->
                            actionBelief != null && postflopBelief == null;
                    case POSTFLOP_ACTION_RIVER_BUCKETS ->
                            actionBelief != null && postflopBelief != null;
                    default -> actionBelief == null && postflopBelief == null;
                };
        if (!validBeliefs)
            throw new IllegalArgumentException("Action beliefs require their matching mode");
        this.actionBelief = actionBelief;
        this.postflopBelief = postflopBelief;
        if (actionBelief != null) actionBelief.validateRanges(this.buttonRange, this.bigBlindRange);
        if (!Double.isFinite(flopBetBb)
                || !Double.isFinite(turnBetBb)
                || !Double.isFinite(riverBetBb)
                || flopBetBb <= 0
                || turnBetBb <= 0
                || riverBetBb <= 0
                || flopBetBb + turnBetBb + riverBetBb > 97)
            throw new IllegalArgumentException("Invalid postflop bet sizes");
        this.flopBetBb = flopBetBb;
        this.turnBetBb = turnBetBb;
        this.riverBetBb = riverBetBb;

        SixMaxPreflopBetting betting =
                new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        var beforeButton = betting.initialState();
        for (Seat seat : List.of(Seat.UTG, Seat.HJ, Seat.CO))
            beforeButton = betting.apply(beforeButton, move(seat, Kind.FOLD, 0));
        var buttonFolds = betting.apply(beforeButton, move(Seat.BTN, Kind.FOLD, 0));
        buttonFolds = betting.apply(buttonFolds, move(Seat.SB, Kind.FOLD, 0));
        var buttonOpens = betting.apply(beforeButton, move(Seat.BTN, Kind.RAISE_TO, 3));
        buttonOpens = betting.apply(buttonOpens, move(Seat.SB, Kind.FOLD, 0));
        var bigBlindFolds = betting.apply(buttonOpens, move(Seat.BB, Kind.FOLD, 0));
        var bigBlindCalls = betting.apply(buttonOpens, move(Seat.BB, Kind.CALL, 3));
        potBb = bigBlindCalls.potBb();
        double deadMoneyBb = potBb - 2 * bigBlindCalls.committedBb(Seat.BB);
        buttonFoldUtility = buttonFolds.uncontestedProfitBb() - deadMoneyBb / 2;
        bigBlindFoldUtility = -bigBlindFolds.committedBb(Seat.BB) - deadMoneyBb / 2;

        List<ChanceOutcome<State>> deals = new ArrayList<>();
        double totalWeight = 0;
        for (WeightedCombo button : this.buttonRange)
            for (WeightedCombo bigBlind : this.bigBlindRange)
                if (!button.conflictsWith(bigBlind)) {
                    double weight = button.weight() * bigBlind.weight();
                    deals.add(new ChanceOutcome<>(deal(bigBlind, button), weight));
                    totalWeight += weight;
                }
        if (deals.isEmpty() || deals.size() > MAX_JOINT_DEALS || !Double.isFinite(totalWeight))
            throw new IllegalArgumentException("Physical-deck research range exceeds deal budget");
        for (WeightedCombo combo : this.buttonRange)
            if (this.bigBlindRange.stream().noneMatch(other -> !combo.conflictsWith(other)))
                throw new IllegalArgumentException("Button combo has no legal opponent");
        for (WeightedCombo combo : this.bigBlindRange)
            if (this.buttonRange.stream().noneMatch(other -> !combo.conflictsWith(other)))
                throw new IllegalArgumentException("Big-blind combo has no legal opponent");
        double normalization = totalWeight;
        initialDeals =
                deals.stream()
                        .map(
                                outcome ->
                                        new ChanceOutcome<>(
                                                outcome.state(),
                                                outcome.probability() / normalization))
                        .toList();
        StringBuilder definition =
                new StringBuilder(
                        "connected-btn-bb-physical/v1|100bb|sb=.5|bb=1|open=3|no-rake|fixed-folds|");
        for (double bet : List.of(flopBetBb, turnBetBb, riverBetBb))
            definition.append(Double.toHexString(bet)).append('|');
        definition.append("button:").append(this.buttonRange.size()).append('|');
        appendRange(definition, this.buttonRange);
        definition.append("big-blind:").append(this.bigBlindRange.size()).append('|');
        appendRange(definition, this.bigBlindRange);
        if (informationMode == InformationMode.BOARD_BUCKETS)
            definition.append("information-mode:board-buckets/v1|");
        if (informationMode == InformationMode.COARSE_BOARD_BUCKETS)
            definition.append("information-mode:coarse-board-buckets/v1|");
        if (informationMode == InformationMode.TEXTURE_BOARD_BUCKETS)
            definition.append("information-mode:texture-board-buckets/v1|");
        if (informationMode == InformationMode.RANGE_EQUITY_RIVER_BUCKETS)
            definition.append("information-mode:range-equity-river-buckets/v1|");
        if (informationMode == InformationMode.PREFLOP_ACTION_RIVER_BUCKETS)
            definition
                    .append("information-mode:preflop-action-river-buckets/v1|")
                    .append(actionBelief.contentDefinition())
                    .append('|');
        if (informationMode == InformationMode.POSTFLOP_ACTION_RIVER_BUCKETS)
            definition
                    .append("information-mode:postflop-action-river-buckets/v1|")
                    .append(actionBelief.contentDefinition())
                    .append('|')
                    .append(postflopBelief.contentDefinition())
                    .append('|');
        contentHash = MultiwayCallSpot.sha256(definition.toString());
    }

    public String contentHash() {
        return contentHash;
    }

    public double potBb() {
        return potBb;
    }

    /** Conservative absolute bound for every terminal payoff in this fixed-size betting tree. */
    public double maximumAbsoluteTerminalUtilityBb() {
        return Math.max(
                Math.max(Math.abs(buttonFoldUtility), Math.abs(bigBlindFoldUtility)),
                potBb / 2 + flopBetBb + turnBetBb + riverBetBb);
    }

    public InformationMode informationMode() {
        return informationMode;
    }

    @Override
    public State initialState() {
        return new State(null, null, "", null, "", null, "", null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return state.preflopHistory().equals("f")
                || state.preflopHistory().equals("of")
                || folded(state.flopHistory())
                || state.turn() != null && folded(state.turnHistory())
                || state.river() != null
                        && (folded(state.riverHistory()) || complete(state.riverHistory()));
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        if (state.preflopHistory().equals("f")) return buttonFoldUtility;
        if (state.preflopHistory().equals("of")) return bigBlindFoldUtility;
        double halfPot = potBb / 2;
        if (folded(state.flopHistory())) return foldSign(state.flopHistory()) * halfPot;
        halfPot += contribution(state.flopHistory(), flopBetBb);
        if (folded(state.turnHistory())) return foldSign(state.turnHistory()) * halfPot;
        halfPot += contribution(state.turnHistory(), turnBetBb);
        if (folded(state.riverHistory())) return foldSign(state.riverHistory()) * halfPot;
        halfPot += contribution(state.riverHistory(), riverBetBb);
        List<Card> flop = state.flop();
        int first =
                HandEvaluator.evaluateBestScore(
                        state.bigBlind().first(),
                        state.bigBlind().second(),
                        flop.get(0),
                        flop.get(1),
                        flop.get(2),
                        state.turn(),
                        state.river());
        int second =
                HandEvaluator.evaluateBestScore(
                        state.button().first(),
                        state.button().second(),
                        flop.get(0),
                        flop.get(1),
                        flop.get(2),
                        state.turn(),
                        state.river());
        return Integer.compare(first, second) * halfPot;
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        if (state.bigBlind() == null) return -1;
        if (state.preflopHistory().isEmpty()) return 1;
        if (state.preflopHistory().equals("o")) return 0;
        if (!state.preflopHistory().equals("oc"))
            throw new IllegalArgumentException("Invalid preflop history");
        if (state.flop() == null
                || complete(state.flopHistory()) && state.turn() == null
                || complete(state.turnHistory()) && state.river() == null) return -1;
        return switch (activeHistory(state)) {
            case "", "kb" -> 0;
            case "k", "b" -> 1;
            default -> throw new IllegalArgumentException("Invalid street history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance node has no player actions");
        if (state.preflopHistory().isEmpty()) return List.of("open3", "fold");
        if (state.preflopHistory().equals("o")) return List.of("call", "fold");
        return switch (activeHistory(state)) {
            case "", "k" -> List.of("k", "b");
            case "b", "kb" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Invalid street history");
        };
    }

    @Override
    public String informationSet(State state) {
        int player = currentPlayer(state);
        if (player == -1) throw new IllegalArgumentException("Chance node has no information set");
        if (state.preflopHistory().isEmpty()) return "P:BTN:" + state.button().key();
        if (state.preflopHistory().equals("o")) return "P:BB:open3:" + state.bigBlind().key();
        WeightedCombo own = player == 0 ? state.bigBlind() : state.button();
        List<WeightedCombo> opponents = player == 0 ? buttonRange : bigBlindRange;
        if (informationMode != InformationMode.EXACT_PUBLIC_CARDS) {
            String prefix =
                    "B:F:"
                            + bucketKey(state.flop(), own, opponents)
                            + ":"
                            + own.key()
                            + "|F:"
                            + state.flopHistory();
            if (state.turn() == null) return prefix;
            prefix +=
                    "|T:"
                            + bucketKey(withCard(state.flop(), state.turn()), own, opponents)
                            + ":"
                            + state.turnHistory();
            if (state.river() == null) return prefix;
            List<WeightedCombo> riverOpponents = opponents;
            if (informationMode == InformationMode.PREFLOP_ACTION_RIVER_BUCKETS
                    || informationMode == InformationMode.POSTFLOP_ACTION_RIVER_BUCKETS)
                riverOpponents =
                        actionBelief.posteriorWeights(
                                opponents,
                                player == 0
                                        ? PreflopActionBelief.ObservedAction.BUTTON_OPEN
                                        : PreflopActionBelief.ObservedAction.BIG_BLIND_CALL);
            if (informationMode == InformationMode.POSTFLOP_ACTION_RIVER_BUCKETS)
                riverOpponents =
                        postflopBelief.posteriorWeights(riverOpponents, state, player == 1);
            return prefix
                    + "|R:"
                    + bucketKey(
                            withCard(withCard(state.flop(), state.turn()), state.river()),
                            own,
                            riverOpponents)
                    + ":"
                    + state.riverHistory();
        }
        String prefix =
                "F:" + flopKey(state.flop()) + ":" + own.key() + "|F:" + state.flopHistory();
        if (state.turn() == null) return prefix;
        prefix += "|T:" + state.turn().compact() + ":" + state.turnHistory();
        if (state.river() == null) return prefix;
        return prefix + "|R:" + state.river().compact() + ":" + state.riverHistory();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal connected-game action");
        if (state.preflopHistory().isEmpty())
            return new State(
                    state.bigBlind(),
                    state.button(),
                    action.equals("fold") ? "f" : "o",
                    null,
                    "",
                    null,
                    "",
                    null,
                    "");
        if (state.preflopHistory().equals("o"))
            return new State(
                    state.bigBlind(),
                    state.button(),
                    action.equals("fold") ? "of" : "oc",
                    null,
                    "",
                    null,
                    "",
                    null,
                    "");
        if (state.turn() == null)
            return new State(
                    state.bigBlind(),
                    state.button(),
                    "oc",
                    state.flop(),
                    state.flopHistory() + action,
                    null,
                    "",
                    null,
                    "");
        if (state.river() == null)
            return new State(
                    state.bigBlind(),
                    state.button(),
                    "oc",
                    state.flop(),
                    state.flopHistory(),
                    state.turn(),
                    state.turnHistory() + action,
                    null,
                    "");
        return new State(
                state.bigBlind(),
                state.button(),
                "oc",
                state.flop(),
                state.flopHistory(),
                state.turn(),
                state.turnHistory(),
                state.river(),
                state.riverHistory() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.bigBlind() == null) return initialDeals;
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        List<Card> remaining = remaining(state);
        List<ChanceOutcome<State>> outcomes = new ArrayList<>();
        if (state.flop() == null) {
            for (int first = 0; first < remaining.size() - 2; first++)
                for (int second = first + 1; second < remaining.size() - 1; second++)
                    for (int third = second + 1; third < remaining.size(); third++)
                        outcomes.add(
                                new ChanceOutcome<>(
                                        withFlop(
                                                state,
                                                List.of(
                                                        remaining.get(first),
                                                        remaining.get(second),
                                                        remaining.get(third))),
                                        1.0 / FLOPS_PER_DEAL));
        } else {
            for (Card card : remaining)
                outcomes.add(
                        new ChanceOutcome<>(
                                state.turn() == null
                                        ? withTurn(state, card)
                                        : withRiver(state, card),
                                1.0 / remaining.size()));
        }
        return List.copyOf(outcomes);
    }

    @Override
    public ChanceOutcome<State> sampleChanceOutcome(State state, double quantile) {
        if (!Double.isFinite(quantile) || quantile < 0 || quantile >= 1)
            throw new IllegalArgumentException("Chance quantile must be in [0, 1)");
        if (state.bigBlind() == null) return CfrGame.super.sampleChanceOutcome(state, quantile);
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        List<Card> remaining = remaining(state);
        if (state.flop() == null)
            return new ChanceOutcome<>(
                    withFlop(state, unrankFlop(remaining, (int) (quantile * FLOPS_PER_DEAL))),
                    1.0 / FLOPS_PER_DEAL);
        Card card = remaining.get((int) (quantile * remaining.size()));
        return new ChanceOutcome<>(
                state.turn() == null ? withTurn(state, card) : withRiver(state, card),
                1.0 / remaining.size());
    }

    private static List<Card> unrankFlop(List<Card> cards, int index) {
        int size = cards.size();
        int first = 0;
        while (index >= chooseTwo(size - first - 1)) {
            index -= chooseTwo(size - first - 1);
            first++;
        }
        int second = first + 1;
        while (index >= size - second - 1) {
            index -= size - second - 1;
            second++;
        }
        return List.of(cards.get(first), cards.get(second), cards.get(second + 1 + index));
    }

    private static int chooseTwo(int count) {
        return count * (count - 1) / 2;
    }

    private static List<Card> remaining(State state) {
        Deck deck = new Deck();
        deck.remove(state.bigBlind().first());
        deck.remove(state.bigBlind().second());
        deck.remove(state.button().first());
        deck.remove(state.button().second());
        if (state.flop() != null) state.flop().forEach(deck::remove);
        if (state.turn() != null) deck.remove(state.turn());
        return deck.cards();
    }

    private static State deal(WeightedCombo bigBlind, WeightedCombo button) {
        return new State(bigBlind, button, "", null, "", null, "", null, "");
    }

    private static State withFlop(State state, List<Card> flop) {
        return new State(state.bigBlind(), state.button(), "oc", flop, "", null, "", null, "");
    }

    private static State withTurn(State state, Card turn) {
        return new State(
                state.bigBlind(),
                state.button(),
                "oc",
                state.flop(),
                state.flopHistory(),
                turn,
                "",
                null,
                "");
    }

    private static State withRiver(State state, Card river) {
        return new State(
                state.bigBlind(),
                state.button(),
                "oc",
                state.flop(),
                state.flopHistory(),
                state.turn(),
                state.turnHistory(),
                river,
                "");
    }

    private static List<WeightedCombo> canonicalRange(List<WeightedCombo> range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Each seat needs a nonempty prior range");
        Set<String> seen = new HashSet<>();
        List<WeightedCombo> sorted = new ArrayList<>();
        for (WeightedCombo combo : range) {
            if (combo == null || !seen.add(combo.key()))
                throw new IllegalArgumentException("Null or duplicate prior combo");
            sorted.add(combo);
        }
        sorted.sort(Comparator.comparing(WeightedCombo::key));
        return List.copyOf(sorted);
    }

    private static void appendRange(StringBuilder definition, List<WeightedCombo> range) {
        for (WeightedCombo combo : range)
            definition
                    .append(combo.key())
                    .append(':')
                    .append(Double.toHexString(combo.weight()))
                    .append('|');
    }

    private static String flopKey(List<Card> flop) {
        return flop.stream().map(Card::compact).sorted().reduce("", String::concat);
    }

    private String bucketKey(List<Card> board, WeightedCombo own, List<WeightedCombo> opponents) {
        return switch (informationMode) {
            case BOARD_BUCKETS -> PublicBoardBucket.key(board, own);
            case TEXTURE_BOARD_BUCKETS -> PublicBoardBucket.textureKey(board, own);
            case COARSE_BOARD_BUCKETS -> PublicBoardBucket.coarseKey(board, own);
            case RANGE_EQUITY_RIVER_BUCKETS ->
                    board.size() == 5
                            ? PublicRiverEquityBucket.key(board, own, opponents)
                            : PublicBoardBucket.coarseKey(board, own);
            case PREFLOP_ACTION_RIVER_BUCKETS ->
                    board.size() == 5
                            ? PublicRiverEquityBucket.key(board, own, opponents)
                            : PublicBoardBucket.coarseKey(board, own);
            case POSTFLOP_ACTION_RIVER_BUCKETS ->
                    board.size() == 5
                            ? PublicRiverEquityBucket.key(board, own, opponents)
                            : PublicBoardBucket.coarseKey(board, own);
            case EXACT_PUBLIC_CARDS ->
                    throw new IllegalStateException("Exact cards have no bucket key");
        };
    }

    private static List<Card> withCard(List<Card> board, Card card) {
        List<Card> extended = new ArrayList<>(board);
        extended.add(card);
        return extended;
    }

    private static String activeHistory(State state) {
        return state.turn() == null
                ? state.flopHistory()
                : state.river() == null ? state.turnHistory() : state.riverHistory();
    }

    private static boolean folded(String history) {
        return history.equals("bf") || history.equals("kbf");
    }

    private static boolean complete(String history) {
        return history.equals("kk") || history.equals("bc") || history.equals("kbc");
    }

    private static int foldSign(String history) {
        return history.equals("bf") ? 1 : -1;
    }

    private static double contribution(String history, double bet) {
        return history.equals("kk") ? 0 : bet;
    }

    private static SixMaxPreflopBetting.Move move(Seat seat, Kind kind, double bb) {
        return new SixMaxPreflopBetting.Move(seat, kind, bb);
    }
}
