package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded 2-6 seat preflop all-in subgame. Seat 0 has already shoved; later seats call or fold in
 * order. Responders may have shorter stacks; contribution tiers settle main and side pots with no
 * rake. This does not model opens, raises, or postflop play. Showdown payoffs are computed before
 * solving.
 */
public final class MultiwayPreflopCallGame
        implements MultiPlayerCfrGame<MultiwayPreflopCallGame.State> {
    public record State(int dealIndex, String history) {}

    private record Deal(
            List<WeightedCombo> combos,
            double weight,
            Map<Integer, MultiwayShowdownEstimate> estimates) {}

    private static final int MAX_JOINT_DEALS = 4096;
    private static final int MAX_PAYOFF_TABLE_ENTRIES = 2048;
    private final List<PreflopAllInSpot.Seat> seats;
    private final List<Double> committedBb;
    private final List<Double> stacksBb;
    private final double deadMoneyBb;
    private final double maximumTerminalPayoffStandardErrorBb;
    private final List<Deal> deals;
    private final List<ChanceOutcome<State>> chanceOutcomes;

    public MultiwayPreflopCallGame(
            List<PreflopAllInSpot.Seat> seats,
            List<List<WeightedCombo>> ranges,
            List<Double> committedBb,
            double stackBb,
            double deadMoneyBb,
            MultiwayShowdownOracle oracle) {
        this(
                seats,
                ranges,
                committedBb,
                seats == null ? null : java.util.Collections.nCopies(seats.size(), stackBb),
                deadMoneyBb,
                oracle);
    }

    /** Research-only unequal-stack constructor; saved v1 packs still use equal stacks. */
    public MultiwayPreflopCallGame(
            List<PreflopAllInSpot.Seat> seats,
            List<List<WeightedCombo>> ranges,
            List<Double> committedBb,
            List<Double> stacksBb,
            double deadMoneyBb,
            MultiwayShowdownOracle oracle) {
        Objects.requireNonNull(oracle, "oracle");
        if (seats == null
                || ranges == null
                || committedBb == null
                || stacksBb == null
                || seats.size() < 2
                || seats.size() > 6
                || ranges.size() != seats.size()
                || committedBb.size() != seats.size()
                || stacksBb.size() != seats.size())
            throw new IllegalArgumentException("Invalid seats, ranges, stacks, or commitments");
        this.seats = List.copyOf(seats);
        this.committedBb = List.copyOf(committedBb);
        this.stacksBb = List.copyOf(stacksBb);
        if (this.seats.size() < 2
                || this.seats.size() > 6
                || new HashSet<>(this.seats).size() != this.seats.size()
                || !Double.isFinite(this.stacksBb.get(0))
                || this.stacksBb.get(0) <= 1
                || !Double.isFinite(deadMoneyBb)
                || deadMoneyBb < 0)
            throw new IllegalArgumentException("Invalid seats, ranges, stack, or dead money");
        this.deadMoneyBb = deadMoneyBb;
        for (int player = 0; player < this.seats.size(); player++) {
            Double committed = this.committedBb.get(player);
            double stack = this.stacksBb.get(player);
            if (committed == null
                    || !Double.isFinite(stack)
                    || stack <= 0
                    || !Double.isFinite(committed)
                    || committed < 0
                    || (player == 0
                            ? committed != stack
                            : committed >= Math.min(stack, this.stacksBb.get(0))))
                throw new IllegalArgumentException("Invalid seat commitment");
            List<WeightedCombo> range = ranges.get(player);
            if (range == null || range.isEmpty())
                throw new IllegalArgumentException("Every seat needs a nonempty range");
            Set<String> seen = new HashSet<>();
            for (WeightedCombo combo : range)
                if (combo == null || !seen.add(combo.key()))
                    throw new IllegalArgumentException("Null or duplicate combo in range");
        }
        List<Deal> prepared = new ArrayList<>();
        enumerate(ranges, 0, new ArrayList<>(), new HashSet<>(), 1, prepared);
        if (prepared.isEmpty())
            throw new IllegalArgumentException("Ranges have no unblocked joint deal");
        if ((long) prepared.size() * ((1 << (playerCount() - 1)) - 1) > MAX_PAYOFF_TABLE_ENTRIES)
            throw new IllegalArgumentException("Showdown payoff table cap exceeded");
        double totalWeight = prepared.stream().mapToDouble(Deal::weight).sum();
        if (!Double.isFinite(totalWeight) || totalWeight <= 0)
            throw new IllegalArgumentException("Invalid joint deal weights");
        List<Deal> finished = new ArrayList<>(prepared.size());
        List<ChanceOutcome<State>> outcomes = new ArrayList<>(prepared.size());
        double maximumStandardError = 0;
        for (int index = 0; index < prepared.size(); index++) {
            Deal deal = prepared.get(index);
            Map<Integer, MultiwayShowdownEstimate> estimates = new LinkedHashMap<>();
            for (int mask = 3; mask < (1 << playerCount()); mask++) {
                if ((mask & 1) == 0 || Integer.bitCount(mask) < 2) continue;
                MultiwayShowdownEstimate estimate = oracle.estimate(deal.combos(), mask);
                validateEstimate(estimate, mask);
                estimates.put(mask, estimate);
            }
            for (int mask = 1; mask < (1 << playerCount()); mask += 2)
                maximumStandardError =
                        Math.max(
                                maximumStandardError,
                                AllInSidePots.settle(
                                                commitmentsForMask(mask),
                                                mask,
                                                deadMoneyBb,
                                                estimates::get)
                                        .maximumStandardErrorBb());
            finished.add(new Deal(deal.combos(), deal.weight(), Map.copyOf(estimates)));
            outcomes.add(new ChanceOutcome<>(new State(index, ""), deal.weight() / totalWeight));
        }
        deals = List.copyOf(finished);
        chanceOutcomes = List.copyOf(outcomes);
        maximumTerminalPayoffStandardErrorBb = maximumStandardError;
    }

    public List<PreflopAllInSpot.Seat> seats() {
        return seats;
    }

    public double stackBb() {
        return stacksBb.get(0);
    }

    public List<Double> stacksBb() {
        return stacksBb;
    }

    public double callCostBb(int player) {
        if (player < 1 || player >= playerCount())
            throw new IllegalArgumentException("Choose a responding seat");
        return Math.min(stacksBb.get(0), stacksBb.get(player)) - committedBb.get(player);
    }

    public double potBeforeDecision(String history) {
        if (history == null || history.length() >= playerCount() || !history.matches("[cf]*"))
            throw new IllegalArgumentException("Invalid public call history");
        double pot = deadMoneyBb;
        for (double committed : committedBb) pot += committed;
        for (int index = 0; index < history.length(); index++)
            if (history.charAt(index) == 'c') pot += callCostBb(index + 1);
        return pot;
    }

    /** Largest terminal payoff SE bound across seats, deals and call subsets. */
    public double maximumTerminalPayoffStandardErrorBb() {
        return maximumTerminalPayoffStandardErrorBb;
    }

    public List<WeightedCombo> dealtCombos(State state) {
        return deals.get(state.dealIndex()).combos();
    }

    @Override
    public int playerCount() {
        return seats.size();
    }

    @Override
    public State initialState() {
        return new State(-1, "");
    }

    @Override
    public boolean isTerminal(State state) {
        return state.dealIndex() >= 0 && state.history().length() == playerCount() - 1;
    }

    @Override
    public double[] terminalUtilities(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Not a terminal state");
        int mask = 1;
        for (int player = 1; player < playerCount(); player++)
            if (state.history().charAt(player - 1) == 'c') mask |= 1 << player;
        return AllInSidePots.settle(
                        commitmentsForMask(mask),
                        mask,
                        deadMoneyBb,
                        deals.get(state.dealIndex()).estimates()::get)
                .utilitiesBb();
    }

    @Override
    public int currentPlayer(State state) {
        if (state.dealIndex() == -1) return -1;
        if (isTerminal(state)) throw new IllegalArgumentException("Terminal state has no actor");
        return state.history().length() + 1;
    }

    @Override
    public List<String> legalActions(State state) {
        if (state.dealIndex() == -1 || isTerminal(state))
            throw new IllegalArgumentException("Not a decision state");
        return List.of("c", "f");
    }

    @Override
    public String informationSet(State state) {
        int player = currentPlayer(state);
        return deals.get(state.dealIndex()).combos().get(player).key() + ":" + state.history();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal call-game action");
        return new State(state.dealIndex(), state.history() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (state.dealIndex() != -1) throw new IllegalArgumentException("Not a chance node");
        return chanceOutcomes;
    }

    private void enumerate(
            List<List<WeightedCombo>> ranges,
            int player,
            List<WeightedCombo> selected,
            Set<Card> used,
            double weight,
            List<Deal> result) {
        if (player == ranges.size()) {
            if (result.size() == MAX_JOINT_DEALS)
                throw new IllegalArgumentException("Joint deal cap exceeded");
            result.add(new Deal(List.copyOf(selected), weight, Map.of()));
            return;
        }
        for (WeightedCombo combo : ranges.get(player)) {
            if (used.contains(combo.first()) || used.contains(combo.second())) continue;
            used.add(combo.first());
            used.add(combo.second());
            selected.add(combo);
            enumerate(ranges, player + 1, selected, used, weight * combo.weight(), result);
            selected.remove(selected.size() - 1);
            used.remove(combo.first());
            used.remove(combo.second());
        }
    }

    private double[] commitmentsForMask(int mask) {
        double[] amounts = new double[playerCount()];
        for (int player = 0; player < playerCount(); player++)
            amounts[player] =
                    (mask & (1 << player)) == 0
                            ? committedBb.get(player)
                            : Math.min(stacksBb.get(0), stacksBb.get(player));
        return amounts;
    }

    private void validateEstimate(MultiwayShowdownEstimate estimate, int mask) {
        if (estimate == null) throw new IllegalArgumentException("Missing oracle estimate");
        double[] shares = estimate.shares();
        double[] errors = estimate.standardErrors();
        if (shares == null || shares.length != playerCount())
            throw new IllegalArgumentException("Oracle share count must match seats");
        double sum = 0;
        for (int player = 0; player < playerCount(); player++) {
            double share = shares[player];
            if (!Double.isFinite(share)
                    || share < 0
                    || share > 1
                    || ((mask & (1 << player)) == 0 && share != 0)
                    || !Double.isFinite(errors[player])
                    || errors[player] < 0
                    || ((mask & (1 << player)) == 0 && errors[player] != 0))
                throw new IllegalArgumentException("Invalid oracle pot share");
            sum += share;
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Oracle pot shares must sum to one");
    }
}
