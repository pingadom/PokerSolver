package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPolicyFlopTransition.JointDeal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** Public flop actions and an observed turn update the entire six-hand joint distribution. */
public final class SixMaxPolicyTurnTransition {
    private record Reached(int index, double logMass) {}

    private final SixMaxHeadsUpFlopGame game;
    private final List<String> history;
    private final List<JointDeal> deals;
    private final double logHistoryProbability;
    private final double matchedFlopBetBb;

    public SixMaxPolicyTurnTransition(
            SixMaxHeadsUpFlopGame game, CfrSolution solution, List<String> history) {
        this.game = Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        this.history = List.copyOf(Objects.requireNonNull(history, "history"));
        var outcomes = game.chanceOutcomes(game.initialState());
        var completed = game.replay(outcomes.getFirst().state(), this.history);
        if (!game.isTerminal(completed) || completed.history().endsWith("f"))
            throw new IllegalArgumentException("A completed, non-folded flop round is required");
        matchedFlopBetBb = completed.history().endsWith("c") ? game.betBb() : 0;
        if (remainingStackBb() <= 0)
            throw new IllegalArgumentException("An all-in flop has no turn betting continuation");
        List<Reached> reached = new ArrayList<>();
        for (var outcome : outcomes) {
            var state = outcome.state();
            double logMass = Math.log(outcome.probability());
            for (String action : this.history) {
                logMass += Math.log(game.strategy(solution, state).get(action));
                state = game.afterAction(state, action);
            }
            if (Double.isFinite(logMass)) reached.add(new Reached(state.dealIndex(), logMass));
        }
        if (reached.isEmpty()) throw new IllegalArgumentException("Flop history has zero reach");
        double maximum = reached.stream().mapToDouble(Reached::logMass).max().orElseThrow();
        double total = reached.stream().mapToDouble(d -> Math.exp(d.logMass() - maximum)).sum();
        logHistoryProbability = maximum + Math.log(total);
        deals =
                reached.stream()
                        .map(
                                d ->
                                        new JointDeal(
                                                game.flop().deals().get(d.index()).hands(),
                                                Math.exp(d.logMass() - maximum) / total))
                        .toList();
    }

    public SixMaxHeadsUpFlopGame flopGame() {
        return game;
    }

    public List<String> history() {
        return history;
    }

    public List<JointDeal> deals() {
        return deals;
    }

    public double historyProbability() {
        return Math.exp(logHistoryProbability);
    }

    public double logHistoryProbability() {
        return logHistoryProbability;
    }

    public double potBb() {
        return game.flop().handoff().potBb() + 2 * matchedFlopBetBb;
    }

    public double remainingStackBb() {
        return game.flop().handoff().remainingStackBb() - matchedFlopBetBb;
    }

    public double committedBb(Seat seat) {
        return game.flop().handoff().committedBb(seat)
                + (seat == game.seat(0) || seat == game.seat(1) ? matchedFlopBetBb : 0);
    }

    /** Probability of this public card, conditional on the observed flop betting history. */
    public double turnProbability(Card turn) {
        requireTurn(turn);
        return deals.stream()
                        .filter(d -> compatible(d, turn))
                        .mapToDouble(JointDeal::probability)
                        .sum()
                / 37;
    }

    public TurnState conditionOnTurn(Card turn) {
        requireTurn(turn);
        double mass =
                deals.stream()
                        .filter(d -> compatible(d, turn))
                        .mapToDouble(JointDeal::probability)
                        .sum();
        if (mass <= 0) throw new IllegalArgumentException("Turn is blocked by every reached deal");
        var posterior =
                deals.stream()
                        .filter(d -> compatible(d, turn))
                        .map(d -> new JointDeal(d.hands(), d.probability() / mass))
                        .toList();
        var board = new ArrayList<>(game.flop().board());
        board.add(turn);
        return new TurnState(board, posterior, mass / 37);
    }

    public TurnState sampleTurn(long seed) {
        var random = new SplittableRandom(seed);
        double quantile = random.nextDouble();
        double cumulative = 0;
        JointDeal selected = deals.getLast();
        for (var deal : deals) {
            cumulative += deal.probability();
            if (quantile < cumulative) {
                selected = deal;
                break;
            }
        }
        var deck = remainingCards(selected, game.flop().board());
        return conditionOnTurn(deck.get(random.nextInt(deck.size())));
    }

    public final class TurnState {
        private final List<Card> board;
        private final List<JointDeal> posterior;
        private final double probability;

        private TurnState(List<Card> board, List<JointDeal> posterior, double probability) {
            this.board = List.copyOf(board);
            this.posterior = List.copyOf(posterior);
            this.probability = probability;
        }

        public SixMaxPolicyTurnTransition handoff() {
            return SixMaxPolicyTurnTransition.this;
        }

        public List<Card> board() {
            return board;
        }

        public List<JointDeal> deals() {
            return posterior;
        }

        public double probability() {
            return probability;
        }

        public List<Card> undealtCards(int index) {
            return remainingCards(posterior.get(index), board);
        }
    }

    private void requireTurn(Card turn) {
        if (turn == null || game.flop().board().contains(turn))
            throw new IllegalArgumentException("A distinct turn card is required");
    }

    private static boolean compatible(JointDeal deal, Card card) {
        return deal.hands().stream()
                .noneMatch(h -> h.first().equals(card) || h.second().equals(card));
    }

    private static List<Card> remainingCards(JointDeal deal, List<Card> board) {
        var deck = new Deck();
        for (var hand : deal.hands()) {
            deck.remove(hand.first());
            deck.remove(hand.second());
        }
        for (Card card : board) deck.remove(card);
        return List.copyOf(deck.cards());
    }
}
