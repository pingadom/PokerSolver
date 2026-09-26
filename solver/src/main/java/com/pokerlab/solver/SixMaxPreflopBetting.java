package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A bounded, no-rake six-seat preflop betting round. It resolves fold wins and identifies all-in
 * showdowns, but deliberately leaves non-all-in pots at a postflop-continuation boundary. This is a
 * rules engine, not a strategy or payoff approximation for that boundary.
 */
public final class SixMaxPreflopBetting {
    private static final long UNITS_PER_BB = 1_000_000;
    private static final Seat[] ORDER = Seat.values();

    public enum Kind {
        POST_SMALL_BLIND,
        POST_BIG_BLIND,
        FOLD,
        CHECK,
        CALL,
        RAISE_TO
    }

    public enum Status {
        DECISION,
        UNCONTESTED,
        ALL_IN_SHOWDOWN,
        POSTFLOP_CONTINUATION_REQUIRED
    }

    /** The amount is the seat's total commitment after this action, except a fold uses zero. */
    public record Move(Seat seat, Kind kind, double amountBb) {
        public Move {
            Objects.requireNonNull(seat, "seat");
            Objects.requireNonNull(kind, "kind");
            toUnits(amountBb);
        }
    }

    public record Rules(double stackBb, double smallBlindBb, List<Double> raiseToBb) {
        public Rules {
            raiseToBb = List.copyOf(Objects.requireNonNull(raiseToBb, "raiseToBb"));
        }

        public static Rules reference100Bb() {
            return new Rules(100, 0.5, List.of(3.0, 10.0, 22.0, 40.0, 100.0));
        }
    }

    public static final class State {
        private final SixMaxPreflopBetting game;
        private final long[] committed;
        private final boolean[] folded;
        private final boolean[] pending;
        private final long currentBet;
        private final long lastFullRaise;
        private final int actor;
        private final int winner;
        private final Status status;
        private final List<Move> history;

        private State(
                SixMaxPreflopBetting game,
                long[] committed,
                boolean[] folded,
                boolean[] pending,
                long currentBet,
                long lastFullRaise,
                int actor,
                int winner,
                Status status,
                List<Move> history) {
            this.game = game;
            this.committed = committed;
            this.folded = folded;
            this.pending = pending;
            this.currentBet = currentBet;
            this.lastFullRaise = lastFullRaise;
            this.actor = actor;
            this.winner = winner;
            this.status = status;
            this.history = List.copyOf(history);
        }

        public Status status() {
            return status;
        }

        public Seat actingSeat() {
            if (status != Status.DECISION) throw new IllegalStateException("Betting round is over");
            return ORDER[actor];
        }

        public double potBb() {
            return fromUnits(Arrays.stream(committed).sum());
        }

        public double currentBetBb() {
            return fromUnits(currentBet);
        }

        public double committedBb(Seat seat) {
            return fromUnits(committed[seat.ordinal()]);
        }

        public double toCallBb() {
            return fromUnits(currentBet - committed[actingSeat().ordinal()]);
        }

        public boolean isFolded(Seat seat) {
            return folded[seat.ordinal()];
        }

        public List<Seat> liveSeats() {
            List<Seat> live = new ArrayList<>();
            for (Seat seat : ORDER) if (!folded[seat.ordinal()]) live.add(seat);
            return List.copyOf(live);
        }

        public List<Move> history() {
            return history;
        }

        public List<Move> legalActions() {
            return game.legalActions(this);
        }

        public Seat winningSeat() {
            if (status != Status.UNCONTESTED)
                throw new IllegalStateException("There is no uncontested winner");
            return ORDER[winner];
        }

        /** Literal chip profit for the sole remaining seat; folded chips stay in the pot. */
        public double uncontestedProfitBb() {
            return potBb() - committedBb(winningSeat());
        }
    }

    private final Rules rules;
    private final long stack;
    private final long smallBlind;
    private final List<Long> raiseTargets;

    public SixMaxPreflopBetting(Rules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        stack = toUnits(rules.stackBb());
        smallBlind = toUnits(rules.smallBlindBb());
        if (stack <= UNITS_PER_BB || smallBlind <= 0 || smallBlind >= UNITS_PER_BB)
            throw new IllegalArgumentException("Invalid stack or small blind");
        List<Long> targets = new ArrayList<>();
        long previous = UNITS_PER_BB;
        for (double targetBb : rules.raiseToBb()) {
            long target = toUnits(targetBb);
            if (target <= previous || target > stack)
                throw new IllegalArgumentException("Raise targets must increase up to the stack");
            targets.add(target);
            previous = target;
        }
        if (targets.isEmpty())
            throw new IllegalArgumentException("At least one raise size is needed");
        raiseTargets = List.copyOf(targets);
    }

    public Rules rules() {
        return rules;
    }

    public State initialState() {
        long[] committed = new long[ORDER.length];
        committed[Seat.SB.ordinal()] = smallBlind;
        committed[Seat.BB.ordinal()] = UNITS_PER_BB;
        boolean[] pending = new boolean[ORDER.length];
        Arrays.fill(pending, true);
        return new State(
                this,
                committed,
                new boolean[ORDER.length],
                pending,
                UNITS_PER_BB,
                UNITS_PER_BB,
                Seat.UTG.ordinal(),
                -1,
                Status.DECISION,
                List.of(
                        new Move(Seat.SB, Kind.POST_SMALL_BLIND, rules.smallBlindBb()),
                        new Move(Seat.BB, Kind.POST_BIG_BLIND, 1)));
    }

    public List<Move> legalActions(State state) {
        requireOwnState(state);
        if (state.status != Status.DECISION) return List.of();
        Seat seat = ORDER[state.actor];
        long committed = state.committed[state.actor];
        List<Move> legal = new ArrayList<>();
        if (committed < state.currentBet) {
            legal.add(new Move(seat, Kind.FOLD, 0));
            legal.add(new Move(seat, Kind.CALL, fromUnits(state.currentBet)));
        } else {
            legal.add(new Move(seat, Kind.CHECK, fromUnits(committed)));
        }
        for (long target : raiseTargets) {
            if (target > state.currentBet && target - state.currentBet >= state.lastFullRaise)
                legal.add(new Move(seat, Kind.RAISE_TO, fromUnits(target)));
        }
        return List.copyOf(legal);
    }

    public State apply(State state, Move move) {
        requireOwnState(state);
        Objects.requireNonNull(move, "move");
        boolean legal =
                legalActions(state).stream()
                        .anyMatch(
                                candidate ->
                                        candidate.seat() == move.seat()
                                                && candidate.kind() == move.kind()
                                                && toUnits(candidate.amountBb())
                                                        == toUnits(move.amountBb()));
        if (!legal) throw new IllegalArgumentException("Illegal or out-of-turn preflop action");
        long[] committed = state.committed.clone();
        boolean[] folded = state.folded.clone();
        boolean[] pending = state.pending.clone();
        long currentBet = state.currentBet;
        long lastFullRaise = state.lastFullRaise;
        int actor = state.actor;
        pending[actor] = false;
        switch (move.kind()) {
            case FOLD -> folded[actor] = true;
            case CALL -> committed[actor] = currentBet;
            case RAISE_TO -> {
                long target = toUnits(move.amountBb());
                committed[actor] = target;
                lastFullRaise = target - currentBet;
                currentBet = target;
                for (int index = 0; index < ORDER.length; index++)
                    pending[index] = !folded[index] && index != actor;
            }
            case CHECK -> {}
            default ->
                    throw new IllegalArgumentException("Blind posts are only in the initial state");
        }
        List<Move> history = new ArrayList<>(state.history);
        history.add(move);
        int live = 0;
        int winner = -1;
        for (int index = 0; index < ORDER.length; index++) {
            if (!folded[index]) {
                live++;
                winner = index;
            }
        }
        if (live == 1)
            return new State(
                    this,
                    committed,
                    folded,
                    pending,
                    currentBet,
                    lastFullRaise,
                    -1,
                    winner,
                    Status.UNCONTESTED,
                    history);
        boolean moreActions = false;
        for (boolean waiting : pending) moreActions |= waiting;
        if (!moreActions) {
            boolean allIn = true;
            for (int index = 0; index < ORDER.length; index++)
                if (!folded[index] && committed[index] != stack) allIn = false;
            return new State(
                    this,
                    committed,
                    folded,
                    pending,
                    currentBet,
                    lastFullRaise,
                    -1,
                    -1,
                    allIn ? Status.ALL_IN_SHOWDOWN : Status.POSTFLOP_CONTINUATION_REQUIRED,
                    history);
        }
        int next = actor;
        do next = (next + 1) % ORDER.length;
        while (!pending[next]);
        return new State(
                this,
                committed,
                folded,
                pending,
                currentBet,
                lastFullRaise,
                next,
                -1,
                Status.DECISION,
                history);
    }

    /** Replays a historical bounded spot through the full six-seat action order. */
    public State replay(List<PreflopAllInSpot.Action> actions) {
        if (actions == null || actions.size() < 2)
            throw new IllegalArgumentException("Blind posts are required");
        if (actions.get(0).seat() != Seat.SB
                || actions.get(0).kind() != PreflopAllInSpot.ActionKind.POST_SMALL_BLIND
                || toUnits(actions.get(0).amountBb()) != smallBlind
                || actions.get(1).seat() != Seat.BB
                || actions.get(1).kind() != PreflopAllInSpot.ActionKind.POST_BIG_BLIND
                || toUnits(actions.get(1).amountBb()) != UNITS_PER_BB)
            throw new IllegalArgumentException("History must start with these blind posts");
        State state = initialState();
        for (int index = 2; index < actions.size(); index++) {
            var action = Objects.requireNonNull(actions.get(index), "action");
            Kind kind =
                    switch (action.kind()) {
                        case FOLD -> Kind.FOLD;
                        case RAISE_TO -> Kind.RAISE_TO;
                        default -> throw new IllegalArgumentException("Blind posts cannot repeat");
                    };
            state = apply(state, new Move(action.seat(), kind, action.amountBb()));
        }
        return state;
    }

    /** Builds the narrow action menu needed to verify an already documented spot history. */
    public static SixMaxPreflopBetting forHistory(
            double stackBb, double smallBlindBb, List<PreflopAllInSpot.Action> actions) {
        List<Double> targets =
                actions.stream()
                        .filter(action -> action.kind() == PreflopAllInSpot.ActionKind.RAISE_TO)
                        .map(PreflopAllInSpot.Action::amountBb)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        if (!targets.contains(stackBb)) targets.add(stackBb);
        targets.sort(Comparator.naturalOrder());
        return new SixMaxPreflopBetting(new Rules(stackBb, smallBlindBb, targets));
    }

    private void requireOwnState(State state) {
        if (state == null || state.game != this)
            throw new IllegalArgumentException("State belongs to a different betting game");
    }

    private static long toUnits(double bb) {
        if (!Double.isFinite(bb) || bb < 0 || bb > 1_000_000)
            throw new IllegalArgumentException("Invalid chip amount");
        double scaled = bb * UNITS_PER_BB;
        long units = Math.round(scaled);
        if (Math.abs(scaled - units) > 0.001)
            throw new IllegalArgumentException("Chip amount needs at most six decimal places");
        return units;
    }

    private static double fromUnits(long units) {
        return (double) units / UNITS_PER_BB;
    }
}
