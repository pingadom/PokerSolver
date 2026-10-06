package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** A single flop/turn/river betting tree retaining all six physical private hands. */
public final class SixMaxHeadsUpPostflopGame implements CfrGame<SixMaxHeadsUpPostflopGame.State> {
    public enum ChanceModel {
        EXACT_PHYSICAL_TURN_RIVER,
        QUANTILE_TURN_EXACT_RIVER
    }

    public record State(
            int dealIndex,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory) {}

    private static final Set<String> HISTORIES =
            Set.of("", "k", "b", "kb", "kk", "bc", "kbc", "bf", "kbf");
    private final SixMaxPolicyFlopTransition.FlopState flop;
    private final double flopBetBb;
    private final double requestedTurnBetBb;
    private final double requestedRiverBetBb;
    private final List<Double> turnQuantiles;
    private final List<ChanceOutcome<State>> deals;
    private final List<List<Card>> undealt;
    private final long[] available;
    private final double[][][] shares;
    private final Map<State, List<ChanceOutcome<State>>> chance = new HashMap<>();
    private final Map<State, String> informationSets = new HashMap<>();
    private final boolean cacheTraversal;
    private final Map<State, Node> nodes = new HashMap<>();

    private record Node(
            boolean terminal,
            int actor,
            List<String> actions,
            Map<String, State> children,
            double utility) {}

    private final double liveUtilityOffsetBb;
    private final String publicContext;

    public SixMaxHeadsUpPostflopGame(
            SixMaxPolicyFlopTransition.FlopState flop,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb) {
        this(flop, flopBetBb, turnBetBb, riverBetBb, List.of());
    }

    /**
     * Empty quantiles mean exact physical turn chance; a nonempty menu declares a different game.
     */
    public SixMaxHeadsUpPostflopGame(
            SixMaxPolicyFlopTransition.FlopState flop,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            List<Double> turnQuantiles) {
        this(flop, flopBetBb, turnBetBb, riverBetBb, turnQuantiles, false);
    }

    // Disabling this cache is only for paired performance and semantic equivalence audits.
    SixMaxHeadsUpPostflopGame(
            SixMaxPolicyFlopTransition.FlopState flop,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            List<Double> turnQuantiles,
            boolean cacheTraversal) {
        this.cacheTraversal = cacheTraversal;
        this.flop = Objects.requireNonNull(flop, "flop");
        for (double bet : new double[] {flopBetBb, turnBetBb, riverBetBb})
            if (!Double.isFinite(bet) || bet <= 0)
                throw new IllegalArgumentException("Requested bets must be finite and positive");
        this.flopBetBb = Math.min(flopBetBb, flop.handoff().remainingStackBb());
        requestedTurnBetBb = turnBetBb;
        requestedRiverBetBb = riverBetBb;
        this.turnQuantiles = List.copyOf(Objects.requireNonNull(turnQuantiles, "turnQuantiles"));
        if (turnQuantiles.size() > 8)
            throw new IllegalArgumentException("At most eight turn quantiles are supported");
        for (double q : this.turnQuantiles)
            if (!Double.isFinite(q) || q < 0 || q >= 1)
                throw new IllegalArgumentException("Turn quantiles must lie in [0, 1)");
        available = new long[flop.deals().size()];
        shares = new double[flop.deals().size()][52][52];
        var roots = new ArrayList<ChanceOutcome<State>>();
        var decks = new ArrayList<List<Card>>();
        for (int deal = 0; deal < flop.deals().size(); deal++) {
            double probability = flop.deals().get(deal).probability();
            if (probability > 0)
                roots.add(
                        new ChanceOutcome<>(new State(deal, "", null, "", null, ""), probability));
            var deck = flop.undealtCards(deal);
            decks.add(deck);
            for (Card card : deck) available[deal] |= 1L << cardIndex(card);
            Card[][] hands = new Card[2][7];
            for (int player = 0; player < 2; player++) {
                var hand = flop.deals().get(deal).hands().get(seat(player).ordinal());
                hands[player][0] = hand.first();
                hands[player][1] = hand.second();
                for (int i = 0; i < 3; i++) hands[player][i + 2] = flop.board().get(i);
            }
            for (int t = 0; t < 36; t++)
                for (int r = t + 1; r < 37; r++) {
                    for (var hand : hands) {
                        hand[5] = deck.get(t);
                        hand[6] = deck.get(r);
                    }
                    int first = HandEvaluator.evaluateBestScore(hands[0]);
                    int second = HandEvaluator.evaluateBestScore(hands[1]);
                    double share = first > second ? 1 : first == second ? 0.5 : 0;
                    int ti = cardIndex(deck.get(t)), ri = cardIndex(deck.get(r));
                    shares[deal][ti][ri] = share;
                    shares[deal][ri][ti] = share;
                }
        }
        deals = List.copyOf(roots);
        undealt = List.copyOf(decks);
        double folded = 0;
        for (Seat seat : Seat.values())
            if (seat != seat(0) && seat != seat(1)) folded += flop.handoff().committedBb(seat);
        liveUtilityOffsetBb = folded / 2;
        publicContext =
                "six-seat-connected-postflop|"
                        + seat(0)
                        + ":"
                        + seat(1)
                        + "|pre:"
                        + flop.handoff().history().stream()
                                .map(a -> a.seat() + ":" + a.action())
                                .collect(Collectors.joining(";"))
                        + "|flop:"
                        + flop.board().stream().map(Card::compact).collect(Collectors.joining(" "))
                        + "|pot:"
                        + flop.handoff().potBb()
                        + "|stack:"
                        + flop.handoff().remainingStackBb()
                        + "|bets:"
                        + this.flopBetBb
                        + ":"
                        + turnBetBb
                        + ":"
                        + riverBetBb
                        + "|chance:"
                        + chanceModel()
                        + ":"
                        + this.turnQuantiles;
    }

    public SixMaxPolicyFlopTransition.FlopState flop() {
        return flop;
    }

    String informationSetPrefix() {
        return publicContext + "|seat:";
    }

    public ChanceModel chanceModel() {
        return turnQuantiles.isEmpty()
                ? ChanceModel.EXACT_PHYSICAL_TURN_RIVER
                : ChanceModel.QUANTILE_TURN_EXACT_RIVER;
    }

    public List<Double> turnQuantiles() {
        return turnQuantiles;
    }

    public double flopBetBb() {
        return flopBetBb;
    }

    public Seat seat(int player) {
        if (player != 0 && player != 1)
            throw new IllegalArgumentException("Expected player 0 or 1");
        return player == 0 ? flop.handoff().firstToAct() : flop.handoff().secondToAct();
    }

    public double liveUtilityOffsetBb() {
        return liveUtilityOffsetBb;
    }

    public WeightedCombo ownHand(State state) {
        int player = currentPlayer(state);
        if (player == -1) throw new IllegalArgumentException("Chance has no own hand");
        return flop.deals().get(state.dealIndex()).hands().get(seat(player).ordinal());
    }

    public double betBb(State state) {
        if (currentPlayer(state) == -1) throw new IllegalArgumentException("Chance has no bet");
        return state.turn() == null
                ? flopBetBb
                : state.river() == null ? turnBet(state) : riverBet(state);
    }

    @Override
    public State initialState() {
        return new State(-1, "", null, "", null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return cacheTraversal ? node(state).terminal() : uncachedTerminal(state);
    }

    private boolean uncachedTerminal(State state) {
        requireState(state);
        return folded(state.flopHistory())
                || folded(state.turnHistory())
                || folded(state.riverHistory())
                || state.river() != null
                        && (complete(state.riverHistory()) || allInBeforeRiver(state));
    }

    @Override
    public int currentPlayer(State state) {
        if (!cacheTraversal) return uncachedActor(state);
        var node = node(state);
        if (node.terminal()) throw new IllegalArgumentException("Terminal state has no player");
        return node.actor();
    }

    private int uncachedActor(State state) {
        if (uncachedTerminal(state))
            throw new IllegalArgumentException("Terminal state has no player");
        if (state.dealIndex() < 0
                || state.turn() == null && complete(state.flopHistory())
                || state.turn() != null
                        && state.river() == null
                        && (complete(state.turnHistory()) || allInOnFlop(state))) return -1;
        String history = activeHistory(state);
        return history.isEmpty() || history.equals("kb") ? 0 : 1;
    }

    @Override
    public List<String> legalActions(State state) {
        if (!cacheTraversal) return uncachedActions(state);
        if (currentPlayer(state) == -1) throw new IllegalArgumentException("Chance has no actions");
        return node(state).actions();
    }

    private List<String> uncachedActions(State state) {
        if (uncachedActor(state) == -1) throw new IllegalArgumentException("Chance has no actions");
        String history = activeHistory(state);
        return history.isEmpty() || history.equals("k")
                ? List.of("check", "bet")
                : List.of("call", "fold");
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal action: " + action);
        return cacheTraversal ? node(state).children().get(action) : applyAction(state, action);
    }

    private static State applyAction(State state, String action) {
        String code =
                switch (action) {
                    case "check" -> "k";
                    case "bet" -> "b";
                    case "call" -> "c";
                    case "fold" -> "f";
                    default -> throw new IllegalArgumentException("Unknown action");
                };
        return state.turn() == null
                ? new State(state.dealIndex(), state.flopHistory() + code, null, "", null, "")
                : state.river() == null
                        ? new State(
                                state.dealIndex(),
                                state.flopHistory(),
                                state.turn(),
                                state.turnHistory() + code,
                                null,
                                "")
                        : new State(
                                state.dealIndex(),
                                state.flopHistory(),
                                state.turn(),
                                state.turnHistory(),
                                state.river(),
                                state.riverHistory() + code);
    }

    @Override
    public String informationSet(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance has no information set");
        return informationSets.computeIfAbsent(
                state,
                s ->
                        publicContext
                                + "|seat:"
                                + seat(currentPlayer(s))
                                + "|own:"
                                + ownHand(s).key()
                                + "|F:"
                                + s.flopHistory()
                                + "|T:"
                                + (s.turn() == null ? "" : s.turn().compact())
                                + ":"
                                + s.turnHistory()
                                + "|R:"
                                + (s.river() == null ? "" : s.river().compact())
                                + ":"
                                + s.riverHistory());
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        if (state.dealIndex() < 0) return deals;
        return chance.computeIfAbsent(
                state,
                s -> {
                    if (s.turn() == null) {
                        var deck = undealt.get(s.dealIndex());
                        var weights = new LinkedHashMap<Card, Double>();
                        if (turnQuantiles.isEmpty())
                            for (Card card : deck) weights.put(card, 1.0 / 37);
                        else
                            for (double q : turnQuantiles)
                                weights.merge(
                                        deck.get((int) (q * 37)),
                                        1.0 / turnQuantiles.size(),
                                        Double::sum);
                        return weights.entrySet().stream()
                                .map(
                                        e ->
                                                new ChanceOutcome<>(
                                                        new State(
                                                                s.dealIndex(),
                                                                s.flopHistory(),
                                                                e.getKey(),
                                                                "",
                                                                null,
                                                                ""),
                                                        e.getValue()))
                                .toList();
                    }
                    return undealt.get(s.dealIndex()).stream()
                            .filter(c -> !c.equals(s.turn()))
                            .map(
                                    c ->
                                            new ChanceOutcome<>(
                                                    new State(
                                                            s.dealIndex(),
                                                            s.flopHistory(),
                                                            s.turn(),
                                                            s.turnHistory(),
                                                            c,
                                                            ""),
                                                    1.0 / 36))
                            .toList();
                });
    }

    @Override
    public double terminalUtility(State state) {
        if (!cacheTraversal) return uncachedUtility(state);
        var node = node(state);
        if (!node.terminal()) throw new IllegalArgumentException("Not terminal");
        return node.utility();
    }

    private double uncachedUtility(State state) {
        if (!uncachedTerminal(state)) throw new IllegalArgumentException("Not terminal");
        double matched =
                matchedFlop(state)
                        + matchedTurn(state)
                        + (state.riverHistory().endsWith("c") ? riverBet(state) : 0);
        double share =
                state.flopHistory().equals("bf")
                                || state.turnHistory().equals("bf")
                                || state.riverHistory().equals("bf")
                        ? 1
                        : folded(state.flopHistory())
                                        || folded(state.turnHistory())
                                        || folded(state.riverHistory())
                                ? 0
                                : shares[state.dealIndex()][cardIndex(state.turn())][
                                        cardIndex(state.river())];
        return (flop.handoff().potBb() + 2 * matched) * share
                - flop.handoff().committedBb(seat(0))
                - matched
                - liveUtilityOffsetBb;
    }

    private Node node(State state) {
        return nodes.computeIfAbsent(
                state,
                s -> {
                    boolean terminal = uncachedTerminal(s);
                    int actor = terminal ? -1 : uncachedActor(s);
                    var actions = actor == -1 ? List.<String>of() : uncachedActions(s);
                    var children = new LinkedHashMap<String, State>();
                    for (String action : actions) children.put(action, applyAction(s, action));
                    return new Node(
                            terminal,
                            actor,
                            actions,
                            Map.copyOf(children),
                            terminal ? uncachedUtility(s) : 0);
                });
    }

    public Map<Seat, Double> terminalUtilitiesBb(State state) {
        return actualUtilities(terminalUtility(state));
    }

    public Map<Seat, Double> profileUtilitiesBb(CfrSolution solution) {
        return actualUtilities(continuation(Objects.requireNonNull(solution), initialState()));
    }

    public Map<Seat, Double> checkdownUtilitiesBb() {
        double share = 0;
        for (var outcome : deals) {
            var turnChance = afterAction(afterAction(outcome.state(), "check"), "check");
            for (var turn : chanceOutcomes(turnChance)) {
                var riverChance = afterAction(afterAction(turn.state(), "check"), "check");
                for (var river : chanceOutcomes(riverChance))
                    share +=
                            outcome.probability()
                                    * turn.probability()
                                    * river.probability()
                                    * shares[outcome.state().dealIndex()][
                                            cardIndex(turn.state().turn())][
                                            cardIndex(river.state().river())];
            }
        }
        return actualUtilities(
                flop.handoff().potBb() * share
                        - flop.handoff().committedBb(seat(0))
                        - liveUtilityOffsetBb);
    }

    double continuation(CfrSolution solution, State state) {
        if (isTerminal(state)) return terminalUtility(state);
        double value = 0;
        if (currentPlayer(state) == -1)
            for (var outcome : chanceOutcomes(state))
                value += outcome.probability() * continuation(solution, outcome.state());
        else
            for (var entry : strategy(solution, state).entrySet())
                if (entry.getValue() > 0)
                    value +=
                            entry.getValue()
                                    * continuation(solution, afterAction(state, entry.getKey()));
        return value;
    }

    Map<String, Double> strategy(CfrSolution solution, State state) {
        var weights = solution.at(currentPlayer(state), informationSet(state));
        var legal = legalActions(state);
        if (weights == null || weights.size() != legal.size())
            throw new IllegalArgumentException("Missing connected postflop strategy");
        double total = 0;
        for (String action : legal) {
            Double weight = weights.get(action);
            if (weight == null || !Double.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid connected postflop strategy");
            total += weight;
        }
        if (Math.abs(total - 1) > 1e-9)
            throw new IllegalArgumentException("Connected postflop strategy must sum to one");
        return weights;
    }

    private Map<Seat, Double> actualUtilities(double centered) {
        var values = new LinkedHashMap<Seat, Double>();
        for (Seat seat : Seat.values())
            values.put(
                    seat,
                    seat == seat(0)
                            ? centered + liveUtilityOffsetBb
                            : seat == seat(1)
                                    ? -centered + liveUtilityOffsetBb
                                    : -flop.handoff().committedBb(seat));
        return Map.copyOf(values);
    }

    private double matchedFlop(State s) {
        return s.flopHistory().endsWith("c") ? flopBetBb : 0;
    }

    private double turnBet(State s) {
        return Math.min(requestedTurnBetBb, flop.handoff().remainingStackBb() - matchedFlop(s));
    }

    private double matchedTurn(State s) {
        return s.turnHistory().endsWith("c") ? turnBet(s) : 0;
    }

    private double riverBet(State s) {
        return Math.min(
                requestedRiverBetBb,
                flop.handoff().remainingStackBb() - matchedFlop(s) - matchedTurn(s));
    }

    private boolean allInOnFlop(State s) {
        return matchedFlop(s) == flop.handoff().remainingStackBb();
    }

    private boolean allInBeforeRiver(State s) {
        return allInOnFlop(s)
                || s.turnHistory().endsWith("c")
                        && matchedTurn(s) == flop.handoff().remainingStackBb() - matchedFlop(s);
    }

    private static String activeHistory(State s) {
        return s.turn() == null
                ? s.flopHistory()
                : s.river() == null ? s.turnHistory() : s.riverHistory();
    }

    private static boolean complete(String h) {
        return h.equals("kk") || h.endsWith("c");
    }

    private static boolean folded(String h) {
        return h.endsWith("f");
    }

    private static int cardIndex(Card c) {
        return c.rank().ordinal() * 4 + c.suit().ordinal();
    }

    private boolean available(int deal, Card card) {
        return (available[deal] & (1L << cardIndex(card))) != 0;
    }

    private void requireState(State s) {
        if (s == null
                || s.flopHistory() == null
                || s.turnHistory() == null
                || s.riverHistory() == null
                || !HISTORIES.contains(s.flopHistory())
                || !HISTORIES.contains(s.turnHistory())
                || !HISTORIES.contains(s.riverHistory())
                || s.dealIndex() < -1
                || s.dealIndex() >= flop.deals().size()
                || s.dealIndex() == -1
                        && (!s.flopHistory().isEmpty()
                                || s.turn() != null
                                || s.river() != null
                                || !s.turnHistory().isEmpty()
                                || !s.riverHistory().isEmpty())
                || s.turn() == null
                        && (!s.turnHistory().isEmpty()
                                || s.river() != null
                                || !s.riverHistory().isEmpty())
                || s.turn() != null
                        && (!complete(s.flopHistory())
                                || !available(s.dealIndex(), s.turn())
                                || allInOnFlop(s) && !s.turnHistory().isEmpty())
                || s.river() == null && !s.riverHistory().isEmpty()
                || s.river() != null
                        && (!(complete(s.turnHistory()) || allInOnFlop(s))
                                || s.river().equals(s.turn())
                                || !available(s.dealIndex(), s.river())
                                || allInBeforeRiver(s) && !s.riverHistory().isEmpty()))
            throw new IllegalArgumentException("Invalid connected postflop state");
    }
}
