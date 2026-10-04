package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One heads-up flop bet round reached from a six-seat policy. Remaining streets check down. Private
 * chance retains all six correlated hands and exact folded-card blockers.
 */
public final class SixMaxHeadsUpFlopGame implements CfrGame<SixMaxHeadsUpFlopGame.State> {
    public record State(int dealIndex, String history) {}

    private static final List<String> HISTORIES =
            List.of("", "k", "b", "kb", "kk", "bc", "kbc", "bf", "kbf");
    private final SixMaxPolicyFlopTransition.FlopState flop;
    private final double betBb;
    private final double[] firstShares;
    private final List<ChanceOutcome<State>> chance;
    private final double liveUtilityOffsetBb;
    private final String publicContext;

    public SixMaxHeadsUpFlopGame(SixMaxPolicyFlopTransition.FlopState flop, double betBb) {
        this.flop = Objects.requireNonNull(flop, "flop");
        if (!Double.isFinite(betBb) || betBb <= 0 || betBb > flop.handoff().remainingStackBb())
            throw new IllegalArgumentException(
                    "Flop bet must be positive and fit both live stacks");
        this.betBb = betBb;
        firstShares = new double[flop.deals().size()];
        List<ChanceOutcome<State>> prepared = new ArrayList<>();
        for (int index = 0; index < firstShares.length; index++) {
            firstShares[index] = flop.exactCheckdown(index).shares().get(seat(0));
            double probability = flop.deals().get(index).probability();
            if (probability > 0)
                prepared.add(new ChanceOutcome<>(new State(index, ""), probability));
        }
        chance = List.copyOf(prepared);
        double foldedCommitments = 0;
        for (Seat seat : Seat.values())
            if (seat != seat(0) && seat != seat(1))
                foldedCommitments += flop.handoff().committedBb(seat);
        // Live chip utilities sum to folded commitments, a fixed constant. Centering preserves
        // every strategy preference and permits the existing two-player zero-sum CFR interface.
        liveUtilityOffsetBb = foldedCommitments / 2;
        publicContext =
                "six-seat-flop|"
                        + seat(0)
                        + ":"
                        + seat(1)
                        + "|pre:"
                        + flop.handoff().history().stream()
                                .map(action -> action.seat() + ":" + action.action())
                                .collect(Collectors.joining(";"))
                        + "|board:"
                        + flop.board().stream()
                                .map(card -> card.compact())
                                .collect(Collectors.joining(" "))
                        + "|pot:"
                        + flop.handoff().potBb()
                        + "|stack:"
                        + flop.handoff().remainingStackBb()
                        + "|bet:"
                        + betBb;
    }

    public SixMaxPolicyFlopTransition.FlopState flop() {
        return flop;
    }

    public double betBb() {
        return betBb;
    }

    public double liveUtilityOffsetBb() {
        return liveUtilityOffsetBb;
    }

    public Seat seat(int player) {
        if (player != 0 && player != 1)
            throw new IllegalArgumentException("Expected player 0 or 1");
        return player == 0 ? flop.handoff().firstToAct() : flop.handoff().secondToAct();
    }

    public WeightedCombo ownHand(State state) {
        int player = currentPlayer(state);
        Seat acting = seat(player);
        return flop.deals().get(state.dealIndex()).hands().get(acting.ordinal());
    }

    @Override
    public State initialState() {
        return new State(-1, "");
    }

    @Override
    public boolean isTerminal(State state) {
        requireState(state);
        return state.history().equals("kk")
                || state.history().endsWith("c")
                || state.history().endsWith("f");
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no player");
        if (state.dealIndex() == -1) return -1;
        return state.history().equals("") || state.history().equals("kb") ? 0 : 1;
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance node has no action");
        return state.history().equals("") || state.history().equals("k")
                ? List.of("check", "bet")
                : List.of("call", "fold");
    }

    @Override
    public String informationSet(State state) {
        return publicContext
                + "|seat:"
                + seat(currentPlayer(state))
                + "|own:"
                + ownHand(state).key()
                + "|actions:"
                + state.history();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal flop action: " + action);
        String code =
                switch (action) {
                    case "check" -> "k";
                    case "bet" -> "b";
                    case "call" -> "c";
                    case "fold" -> "f";
                    default -> throw new IllegalArgumentException("Unknown flop action");
                };
        return new State(state.dealIndex(), state.history() + code);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        return chance;
    }

    public Map<Seat, Double> terminalUtilitiesBb(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        boolean called = state.history().endsWith("c");
        double additional = called ? betBb : 0;
        double share =
                state.history().equals("bf")
                        ? 1
                        : state.history().equals("kbf") ? 0 : firstShares[state.dealIndex()];
        double pot = flop.handoff().potBb() + 2 * additional;
        Map<Seat, Double> utilities = new LinkedHashMap<>();
        for (Seat seat : Seat.values()) {
            double winnings =
                    seat == seat(0) ? pot * share : seat == seat(1) ? pot * (1 - share) : 0;
            double contribution =
                    flop.handoff().committedBb(seat)
                            + (seat == seat(0) || seat == seat(1) ? additional : 0);
            utilities.put(seat, winnings - contribution);
        }
        return Map.copyOf(utilities);
    }

    @Override
    public double terminalUtility(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        double additional = state.history().endsWith("c") ? betBb : 0;
        double share =
                state.history().equals("bf")
                        ? 1
                        : state.history().equals("kbf") ? 0 : firstShares[state.dealIndex()];
        return (flop.handoff().potBb() + 2 * additional) * share
                - flop.handoff().committedBb(seat(0))
                - additional
                - liveUtilityOffsetBb;
    }

    public Map<Seat, Double> profileUtilitiesBb(CfrSolution solution) {
        Objects.requireNonNull(solution, "solution");
        double centered = profileValue(solution, initialState());
        Map<Seat, Double> utilities = new LinkedHashMap<>();
        for (Seat seat : Seat.values())
            utilities.put(
                    seat,
                    seat == seat(0)
                            ? centered + liveUtilityOffsetBb
                            : seat == seat(1)
                                    ? -centered + liveUtilityOffsetBb
                                    : -flop.handoff().committedBb(seat));
        return Map.copyOf(utilities);
    }

    private double profileValue(CfrSolution solution, State state) {
        if (isTerminal(state)) return terminalUtility(state);
        double value = 0;
        if (currentPlayer(state) == -1) {
            for (var outcome : chanceOutcomes(state))
                value += outcome.probability() * profileValue(solution, outcome.state());
        } else {
            var probabilities = strategy(solution, state);
            for (String action : legalActions(state))
                value +=
                        probabilities.get(action)
                                * profileValue(solution, afterAction(state, action));
        }
        return value;
    }

    Map<String, Double> strategy(CfrSolution solution, State state) {
        Map<String, Double> weights = solution.at(currentPlayer(state), informationSet(state));
        List<String> legal = legalActions(state);
        if (weights == null || weights.size() != legal.size())
            throw new IllegalArgumentException("Missing flop strategy");
        double total = 0;
        for (String action : legal) {
            Double weight = weights.get(action);
            if (weight == null || !Double.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid flop strategy");
            total += weight;
        }
        if (Math.abs(total - 1) > 1e-9)
            throw new IllegalArgumentException("Flop strategy must sum to one");
        return weights;
    }

    /** Replay only public betting actions; neither opponent nor folded-seat cards enter the key. */
    public State replay(State dealt, List<String> actions) {
        if (currentPlayer(dealt) != 0 || !dealt.history().isEmpty())
            throw new IllegalArgumentException("Replay must start at a dealt flop root");
        State state = dealt;
        for (String action : List.copyOf(actions)) state = afterAction(state, action);
        return state;
    }

    private void requireState(State state) {
        if (state == null
                || state.history() == null
                || !HISTORIES.contains(state.history())
                || state.dealIndex() < -1
                || state.dealIndex() >= firstShares.length
                || state.dealIndex() == -1 && !state.history().isEmpty())
            throw new IllegalArgumentException("Invalid flop game state");
    }
}
