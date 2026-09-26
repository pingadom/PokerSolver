package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Flop, turn and river single-bet decisions; restricted public turn, exact river. */
public final class FlopTurnRiverGame implements CfrGame<FlopTurnRiverGame.State> {
    public record State(
            WeightedCombo first,
            WeightedCombo second,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory) {}

    private record Showdown(WeightedCombo first, WeightedCombo second, Card turn, Card river) {}

    private record Deal(WeightedCombo first, WeightedCombo second) {}

    private record TurnDeal(WeightedCombo first, WeightedCombo second, Card turn) {}

    private final FlopTurnRiverSpot spot;
    private final List<ChanceOutcome<State>> deals;
    private final Map<Deal, List<Card>> turns;
    private final Map<TurnDeal, List<Card>> rivers;
    private final Map<Showdown, Integer> showdownResults;

    public FlopTurnRiverGame(FlopTurnRiverSpot spot) {
        this.spot = Objects.requireNonNull(spot, "spot");
        double totalWeight = 0;
        for (WeightedCombo first : spot.firstRange())
            for (WeightedCombo second : spot.secondRange())
                if (!first.conflictsWith(second)) totalWeight += first.weight() * second.weight();
        if (totalWeight <= 0 || !Double.isFinite(totalWeight))
            throw new IllegalArgumentException("Ranges have no finite legal joint weight");
        List<ChanceOutcome<State>> prepared = new ArrayList<>();
        Map<Deal, List<Card>> turnMap = new HashMap<>();
        Map<TurnDeal, List<Card>> riverMap = new HashMap<>();
        Map<Showdown, Integer> resultMap = new HashMap<>();
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (first.conflictsWith(second)) continue;
                prepared.add(
                        new ChanceOutcome<>(
                                new State(first, second, "", null, "", null, ""),
                                first.weight() * second.weight() / totalWeight));
                List<Card> legalTurns =
                        spot.turnCandidates().stream()
                                .filter(card -> !FlopTurnRiverSpot.blocked(card, first, second))
                                .toList();
                turnMap.put(new Deal(first, second), legalTurns);
                for (Card turn : legalTurns) {
                    List<Card> legalRivers = remainingCards(first, second, turn);
                    riverMap.put(new TurnDeal(first, second, turn), legalRivers);
                    for (Card river : legalRivers)
                        resultMap.put(
                                new Showdown(first, second, turn, river),
                                showdown(first, second, turn, river));
                }
            }
        }
        deals = List.copyOf(prepared);
        turns = Map.copyOf(turnMap);
        rivers = Map.copyOf(riverMap);
        showdownResults = Map.copyOf(resultMap);
    }

    public FlopTurnRiverSpot spot() {
        return spot;
    }

    @Override
    public State initialState() {
        return new State(null, null, "", null, "", null, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return isFold(state.flopHistory())
                || state.turn() != null && isFold(state.turnHistory())
                || state.river() != null
                        && (isFold(state.riverHistory()) || isComplete(state.riverHistory()));
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        double halfPot = spot.potBb() / 2;
        if (isFold(state.flopHistory())) return foldSign(state.flopHistory()) * halfPot;
        halfPot += contribution(state.flopHistory(), spot.flopBetBb());
        if (isFold(state.turnHistory())) return foldSign(state.turnHistory()) * halfPot;
        halfPot += contribution(state.turnHistory(), spot.turnBetBb());
        if (isFold(state.riverHistory())) return foldSign(state.riverHistory()) * halfPot;
        halfPot += contribution(state.riverHistory(), spot.riverBetBb());
        return showdownResults.get(
                        new Showdown(state.first(), state.second(), state.turn(), state.river()))
                * halfPot;
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        if (state.first() == null
                || isComplete(state.flopHistory()) && state.turn() == null
                || isComplete(state.turnHistory()) && state.river() == null) return -1;
        return switch (activeHistory(state)) {
            case "", "kb" -> 0;
            case "k", "b" -> 1;
            default -> throw new IllegalArgumentException("Invalid betting history");
        };
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1) throw new IllegalArgumentException("Chance node");
        return switch (activeHistory(state)) {
            case "", "k" -> List.of("k", "b");
            case "b", "kb" -> List.of("c", "f");
            default -> throw new IllegalArgumentException("Invalid betting history");
        };
    }

    @Override
    public String informationSet(State state) {
        WeightedCombo own = currentPlayer(state) == 0 ? state.first() : state.second();
        String prefix = own.key() + "|F:" + state.flopHistory();
        if (state.turn() == null) return prefix;
        prefix += "|T:" + state.turn().compact() + ":" + state.turnHistory();
        if (state.river() == null) return prefix;
        return prefix + "|R:" + state.river().compact() + ":" + state.riverHistory();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal action: " + action);
        if (state.turn() == null)
            return new State(
                    state.first(),
                    state.second(),
                    state.flopHistory() + action,
                    null,
                    "",
                    null,
                    "");
        if (state.river() == null)
            return new State(
                    state.first(),
                    state.second(),
                    state.flopHistory(),
                    state.turn(),
                    state.turnHistory() + action,
                    null,
                    "");
        return new State(
                state.first(),
                state.second(),
                state.flopHistory(),
                state.turn(),
                state.turnHistory(),
                state.river(),
                state.riverHistory() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.first() == null) return deals;
        if (state.turn() == null && isComplete(state.flopHistory())) {
            List<Card> cards = turns.get(new Deal(state.first(), state.second()));
            List<ChanceOutcome<State>> outcomes = new ArrayList<>(cards.size());
            for (Card turn : cards)
                outcomes.add(
                        new ChanceOutcome<>(
                                new State(
                                        state.first(),
                                        state.second(),
                                        state.flopHistory(),
                                        turn,
                                        "",
                                        null,
                                        ""),
                                1.0 / cards.size()));
            return outcomes;
        }
        if (state.turn() != null && state.river() == null && isComplete(state.turnHistory())) {
            List<Card> cards =
                    rivers.get(new TurnDeal(state.first(), state.second(), state.turn()));
            List<ChanceOutcome<State>> outcomes = new ArrayList<>(cards.size());
            for (Card river : cards)
                outcomes.add(
                        new ChanceOutcome<>(
                                new State(
                                        state.first(),
                                        state.second(),
                                        state.flopHistory(),
                                        state.turn(),
                                        state.turnHistory(),
                                        river,
                                        ""),
                                1.0 / cards.size()));
            return outcomes;
        }
        throw new IllegalArgumentException("Not a chance node");
    }

    private List<Card> remainingCards(WeightedCombo first, WeightedCombo second, Card turn) {
        Deck deck = new Deck();
        spot.flop().forEach(deck::remove);
        deck.remove(first.first());
        deck.remove(first.second());
        deck.remove(second.first());
        deck.remove(second.second());
        deck.remove(turn);
        return deck.cards();
    }

    private int showdown(WeightedCombo first, WeightedCombo second, Card turn, Card river) {
        List<Card> board = spot.flop();
        int firstScore =
                HandEvaluator.evaluateBestScore(
                        first.first(),
                        first.second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        turn,
                        river);
        int secondScore =
                HandEvaluator.evaluateBestScore(
                        second.first(),
                        second.second(),
                        board.get(0),
                        board.get(1),
                        board.get(2),
                        turn,
                        river);
        return Integer.compare(firstScore, secondScore);
    }

    private static String activeHistory(State state) {
        return state.turn() == null
                ? state.flopHistory()
                : state.river() == null ? state.turnHistory() : state.riverHistory();
    }

    private static int foldSign(String history) {
        return history.equals("bf") ? 1 : -1;
    }

    private static double contribution(String history, double bet) {
        return history.equals("kk") ? 0 : bet;
    }

    private static boolean isFold(String history) {
        return history.equals("bf") || history.equals("kbf");
    }

    private static boolean isComplete(String history) {
        return history.equals("kk") || history.equals("bc") || history.equals("kbc");
    }
}
