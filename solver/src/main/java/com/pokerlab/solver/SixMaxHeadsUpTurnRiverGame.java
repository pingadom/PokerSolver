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
import java.util.stream.Collectors;

/** Two betting rounds with exact physical river chance and six-seat chip accounting. */
public final class SixMaxHeadsUpTurnRiverGame implements CfrGame<SixMaxHeadsUpTurnRiverGame.State> {
    public record State(int dealIndex, String turnHistory, Card river, String riverHistory) {}

    private static final List<String> HISTORIES =
            List.of("", "k", "b", "kb", "kk", "bc", "kbc", "bf", "kbf");
    private final SixMaxPolicyTurnTransition.TurnState turn;
    private final double turnBetBb;
    private final double requestedRiverBetBb;
    private final List<ChanceOutcome<State>> deals;
    private final List<List<Card>> rivers;
    private final List<Map<Card, Double>> firstShares;
    private final Map<State, List<ChanceOutcome<State>>> riverChance = new HashMap<>();
    private final Map<State, String> informationSets = new HashMap<>();
    private final String publicContext;

    public SixMaxHeadsUpTurnRiverGame(
            SixMaxPolicyTurnTransition.TurnState turn,
            double requestedTurnBetBb,
            double requestedRiverBetBb) {
        this.turn = Objects.requireNonNull(turn, "turn");
        if (!Double.isFinite(requestedTurnBetBb)
                || requestedTurnBetBb <= 0
                || !Double.isFinite(requestedRiverBetBb)
                || requestedRiverBetBb <= 0)
            throw new IllegalArgumentException("Requested bets must be finite and positive");
        turnBetBb = Math.min(requestedTurnBetBb, turn.handoff().remainingStackBb());
        this.requestedRiverBetBb = requestedRiverBetBb;
        var prepared = new ArrayList<ChanceOutcome<State>>();
        var decks = new ArrayList<List<Card>>();
        var shares = new ArrayList<Map<Card, Double>>();
        for (int index = 0; index < turn.deals().size(); index++) {
            double probability = turn.deals().get(index).probability();
            if (probability > 0)
                prepared.add(new ChanceOutcome<>(new State(index, "", null, ""), probability));
            var deck = turn.undealtCards(index);
            decks.add(deck);
            var values = new LinkedHashMap<Card, Double>();
            Card[][] hands = new Card[2][7];
            for (int player = 0; player < 2; player++) {
                var combo = turn.deals().get(index).hands().get(seat(player).ordinal());
                hands[player][0] = combo.first();
                hands[player][1] = combo.second();
                for (int card = 0; card < 4; card++)
                    hands[player][card + 2] = turn.board().get(card);
            }
            for (Card river : deck) {
                for (var hand : hands) hand[6] = river;
                int first = HandEvaluator.evaluateBestScore(hands[0]);
                int second = HandEvaluator.evaluateBestScore(hands[1]);
                values.put(river, first > second ? 1.0 : first == second ? 0.5 : 0.0);
            }
            shares.add(Map.copyOf(values));
        }
        deals = List.copyOf(prepared);
        rivers = List.copyOf(decks);
        firstShares = List.copyOf(shares);
        publicContext =
                "six-seat-turn-river|"
                        + seat(0)
                        + ":"
                        + seat(1)
                        + "|pre:"
                        + turn.handoff().flopGame().flop().handoff().history().stream()
                                .map(a -> a.seat() + ":" + a.action())
                                .collect(Collectors.joining(";"))
                        + "|flop-actions:"
                        + String.join(";", turn.handoff().history())
                        + "|flop-bet:"
                        + turn.handoff().flopGame().betBb()
                        + "|board:"
                        + turn.board().stream().map(Card::compact).collect(Collectors.joining(" "))
                        + "|pot:"
                        + turn.handoff().potBb()
                        + "|stack:"
                        + turn.handoff().remainingStackBb()
                        + "|turn-bet:"
                        + turnBetBb
                        + "|river-bet:"
                        + requestedRiverBetBb;
    }

    public SixMaxPolicyTurnTransition.TurnState turn() {
        return turn;
    }

    public double turnBetBb() {
        return turnBetBb;
    }

    public double riverBetBb(State state) {
        requireState(state);
        return Math.min(
                requestedRiverBetBb, turn.handoff().remainingStackBb() - matchedTurnBet(state));
    }

    public Seat seat(int player) {
        return turn.handoff().flopGame().seat(player);
    }

    public double liveUtilityOffsetBb() {
        return turn.handoff().flopGame().liveUtilityOffsetBb();
    }

    public WeightedCombo ownHand(State state) {
        int player = currentPlayer(state);
        if (player == -1) throw new IllegalArgumentException("Chance node has no own hand");
        return turn.deals().get(state.dealIndex()).hands().get(seat(player).ordinal());
    }

    @Override
    public State initialState() {
        return new State(-1, "", null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        requireState(state);
        return folded(state.turnHistory())
                || state.river() != null
                        && (complete(state.riverHistory())
                                || folded(state.riverHistory())
                                || allInOnTurn(state));
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        if (state.dealIndex() == -1 || complete(state.turnHistory()) && state.river() == null)
            return -1;
        String history = state.river() == null ? state.turnHistory() : state.riverHistory();
        return history.equals("") || history.equals("kb") ? 0 : 1;
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance node has no actions");
        String history = state.river() == null ? state.turnHistory() : state.riverHistory();
        return history.equals("") || history.equals("k")
                ? List.of("check", "bet")
                : List.of("call", "fold");
    }

    @Override
    public String informationSet(State state) {
        currentPlayer(state);
        if (state.dealIndex() < 0 || complete(state.turnHistory()) && state.river() == null)
            throw new IllegalArgumentException("Chance node has no information set");
        return informationSets.computeIfAbsent(
                state,
                s ->
                        publicContext
                                + "|seat:"
                                + seat(currentPlayer(s))
                                + "|own:"
                                + ownHand(s).key()
                                + "|turn-actions:"
                                + s.turnHistory()
                                + "|river:"
                                + (s.river() == null ? "" : s.river().compact())
                                + "|river-actions:"
                                + s.riverHistory());
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal action: " + action);
        String code =
                switch (action) {
                    case "check" -> "k";
                    case "bet" -> "b";
                    case "call" -> "c";
                    case "fold" -> "f";
                    default -> throw new IllegalArgumentException("Unknown action");
                };
        return state.river() == null
                ? new State(state.dealIndex(), state.turnHistory() + code, null, "")
                : new State(
                        state.dealIndex(),
                        state.turnHistory(),
                        state.river(),
                        state.riverHistory() + code);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        if (state.dealIndex() < 0) return deals;
        return riverChance.computeIfAbsent(
                state,
                s ->
                        rivers.get(s.dealIndex()).stream()
                                .map(
                                        card ->
                                                new ChanceOutcome<>(
                                                        new State(
                                                                s.dealIndex(),
                                                                s.turnHistory(),
                                                                card,
                                                                ""),
                                                        1.0 / 36))
                                .toList());
    }

    /** Replay public actions and the observed river, rejecting blocked or premature cards. */
    public State replay(
            State dealt, List<String> turnActions, Card river, List<String> riverActions) {
        if (river == null && !riverActions.isEmpty())
            throw new IllegalArgumentException("River actions require a public river card");
        if (!deals.stream().anyMatch(d -> d.state().equals(dealt)))
            throw new IllegalArgumentException("Replay must start at a dealt turn root");
        State state = dealt;
        for (String action : List.copyOf(turnActions)) state = afterAction(state, action);
        if (river != null) {
            var outcomes = chanceOutcomes(state);
            state =
                    outcomes.stream()
                            .filter(o -> o.state().river().equals(river))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Blocked river card"))
                            .state();
        }
        for (String action : List.copyOf(riverActions)) state = afterAction(state, action);
        return state;
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        double additional =
                matchedTurnBet(state)
                        + (state.riverHistory().endsWith("c") ? riverBetBb(state) : 0);
        return (turn.handoff().potBb() + 2 * additional) * firstShare(state)
                - turn.handoff().committedBb(seat(0))
                - additional
                - liveUtilityOffsetBb();
    }

    public Map<Seat, Double> terminalUtilitiesBb(State state) {
        return actualUtilities(terminalUtility(state));
    }

    public Map<Seat, Double> profileUtilitiesBb(CfrSolution solution) {
        return actualUtilities(
                profileValue(Objects.requireNonNull(solution, "solution"), initialState()));
    }

    public Map<Seat, Double> exactCheckdownUtilitiesBb() {
        double firstShare = 0;
        for (var outcome : deals)
            firstShare +=
                    outcome.probability()
                            * firstShares.get(outcome.state().dealIndex()).values().stream()
                                    .mapToDouble(Double::doubleValue)
                                    .sum()
                            / 36;
        return actualUtilities(
                turn.handoff().potBb() * firstShare
                        - turn.handoff().committedBb(seat(0))
                        - liveUtilityOffsetBb());
    }

    private Map<Seat, Double> actualUtilities(double centered) {
        var utilities = new LinkedHashMap<Seat, Double>();
        for (Seat seat : Seat.values())
            utilities.put(
                    seat,
                    seat == seat(0)
                            ? centered + liveUtilityOffsetBb()
                            : seat == seat(1)
                                    ? -centered + liveUtilityOffsetBb()
                                    : -turn.handoff().committedBb(seat));
        return Map.copyOf(utilities);
    }

    private double profileValue(CfrSolution solution, State state) {
        if (isTerminal(state)) return terminalUtility(state);
        double value = 0;
        if (currentPlayer(state) == -1) {
            for (var outcome : chanceOutcomes(state))
                value += outcome.probability() * profileValue(solution, outcome.state());
        } else {
            var weights = strategy(solution, state);
            for (String action : legalActions(state))
                if (weights.get(action) > 0)
                    value +=
                            weights.get(action)
                                    * profileValue(solution, afterAction(state, action));
        }
        return value;
    }

    Map<String, Double> strategy(CfrSolution solution, State state) {
        var weights = solution.at(currentPlayer(state), informationSet(state));
        var legal = legalActions(state);
        if (weights == null || weights.size() != legal.size())
            throw new IllegalArgumentException("Missing turn/river strategy");
        double total = 0;
        for (String action : legal) {
            Double weight = weights.get(action);
            if (weight == null || !Double.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid turn/river strategy");
            total += weight;
        }
        if (Math.abs(total - 1) > 1e-9)
            throw new IllegalArgumentException("Turn/river strategy must sum to one");
        return weights;
    }

    private double firstShare(State state) {
        if (state.turnHistory().equals("bf") || state.riverHistory().equals("bf")) return 1;
        if (state.turnHistory().equals("kbf") || state.riverHistory().equals("kbf")) return 0;
        return firstShares.get(state.dealIndex()).get(state.river());
    }

    private double matchedTurnBet(State state) {
        return state.turnHistory().endsWith("c") ? turnBetBb : 0;
    }

    private boolean allInOnTurn(State state) {
        return matchedTurnBet(state) == turn.handoff().remainingStackBb();
    }

    private static boolean complete(String history) {
        return history.equals("kk") || history.endsWith("c");
    }

    private static boolean folded(String history) {
        return history.endsWith("f");
    }

    private void requireState(State state) {
        if (state == null
                || state.turnHistory() == null
                || state.riverHistory() == null
                || !HISTORIES.contains(state.turnHistory())
                || !HISTORIES.contains(state.riverHistory())
                || state.dealIndex() < -1
                || state.dealIndex() >= turn.deals().size()
                || state.dealIndex() == -1
                        && (!state.turnHistory().isEmpty()
                                || state.river() != null
                                || !state.riverHistory().isEmpty())
                || state.river() == null && !state.riverHistory().isEmpty()
                || state.river() != null
                        && (!complete(state.turnHistory())
                                || !rivers.get(state.dealIndex()).contains(state.river())
                                || allInOnTurn(state) && !state.riverHistory().isEmpty()))
            throw new IllegalArgumentException("Invalid turn/river state");
    }
}
