package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Offline heads-up flop handoff derived from a fixed six-seat policy. The posterior retains all six
 * physical hands, including folded-seat blockers; it is not a product of independent ranges. The
 * source policy still assumes mandatory checkdown. This bridge does not solve postflop play.
 */
public final class SixMaxPolicyFlopTransition {
    public static final int FLOPS_PER_DEAL = 9_880; // Choose 3 from 40 after six hands are dealt.
    private static final List<Seat> POSTFLOP_ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);

    public record JointDeal(List<WeightedCombo> hands, double probability) {
        public JointDeal {
            hands = List.copyOf(hands);
        }
    }

    public record Checkdown(Map<Seat, Double> utilitiesBb, Map<Seat, Double> shares, long runouts) {
        public Checkdown {
            utilitiesBb = Map.copyOf(utilitiesBb);
            shares = Map.copyOf(shares);
        }
    }

    /** An offline sampled physical runout; hidden hands must never be exposed as trainer input. */
    public record Runout(JointDeal deal, List<Card> board) {
        public Runout {
            board = List.copyOf(board);
        }
    }

    private record LogDeal(List<WeightedCombo> hands, double logMass) {}

    private final List<PublicAction> history;
    private final SixMaxPreflopBetting.State publicState;
    private final List<JointDeal> deals;
    private final Seat firstToAct;
    private final Seat secondToAct;
    private final double logReachProbability;
    private final SixMaxPreflopCheckdownGame.ChanceModel chanceModel;

    public SixMaxPolicyFlopTransition(
            SixMaxPreflopCheckdownGame game, CfrSolution solution, List<PublicAction> history) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        this.history = List.copyOf(Objects.requireNonNull(history, "history"));
        if (game.rakeRule().fraction() > 0 && game.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Postflop rake is not supported by this handoff");
        var outcomes = game.chanceOutcomes(game.initialState());
        var representative = replay(game, outcomes.getFirst().state(), this.history);
        publicState = game.publicBettingState(representative);
        if (publicState.status() != SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                || publicState.liveSeats().size() != 2)
            throw new IllegalArgumentException(
                    "A completed heads-up non-all-in history is required");
        firstToAct =
                POSTFLOP_ORDER.stream()
                        .filter(publicState.liveSeats()::contains)
                        .findFirst()
                        .orElseThrow();
        secondToAct =
                publicState.liveSeats().stream()
                        .filter(seat -> seat != firstToAct)
                        .findFirst()
                        .orElseThrow();
        if (publicState.committedBb(firstToAct) != publicState.committedBb(secondToAct)
                || publicState.remainingStackBb(firstToAct) <= 0
                || publicState.remainingStackBb(secondToAct) <= 0)
            throw new IllegalArgumentException(
                    "Equal live commitments and remaining stacks are required");

        List<LogDeal> reached = new ArrayList<>();
        for (var outcome : outcomes) {
            var state = outcome.state();
            double logMass = Math.log(outcome.probability());
            for (var action : this.history) {
                double probability =
                        MultiPlayerStrategyEvaluator.probability(
                                game, solution, state, action.action());
                logMass += Math.log(probability);
                state = game.afterAction(state, action.action());
            }
            if (Double.isFinite(logMass)) reached.add(new LogDeal(game.dealtHands(state), logMass));
        }
        if (reached.isEmpty()) throw new IllegalArgumentException("History has zero policy reach");
        double maximum = reached.stream().mapToDouble(LogDeal::logMass).max().orElseThrow();
        double total =
                reached.stream().mapToDouble(deal -> Math.exp(deal.logMass() - maximum)).sum();
        logReachProbability = maximum + Math.log(total);
        deals =
                reached.stream()
                        .map(
                                deal ->
                                        new JointDeal(
                                                deal.hands(),
                                                Math.exp(deal.logMass() - maximum) / total))
                        .toList();
        chanceModel = game.chanceModel();
    }

    public List<PublicAction> history() {
        return history;
    }

    public List<JointDeal> deals() {
        return deals;
    }

    public Seat firstToAct() {
        return firstToAct;
    }

    public Seat secondToAct() {
        return secondToAct;
    }

    public double potBb() {
        return publicState.potBb();
    }

    public double remainingStackBb() {
        return publicState.remainingStackBb(firstToAct);
    }

    public double reachProbability() {
        return Math.exp(logReachProbability);
    }

    public double logReachProbability() {
        return logReachProbability;
    }

    public SixMaxPreflopCheckdownGame.ChanceModel chanceModel() {
        return chanceModel;
    }

    /** Marginals are descriptive only; their product would lose joint card/action correlations. */
    public Map<String, Double> marginal(Seat seat) {
        Objects.requireNonNull(seat, "seat");
        Map<String, Double> result = new LinkedHashMap<>();
        for (var deal : deals)
            result.merge(deal.hands().get(seat.ordinal()).key(), deal.probability(), Double::sum);
        return Map.copyOf(result);
    }

    public double flopProbability(List<Card> flop) {
        var board = canonicalFlop(flop);
        return deals.stream()
                        .filter(deal -> compatible(deal, board))
                        .mapToDouble(JointDeal::probability)
                        .sum()
                / FLOPS_PER_DEAL;
    }

    public FlopState conditionOnFlop(List<Card> flop) {
        var board = canonicalFlop(flop);
        double mass =
                deals.stream()
                        .filter(deal -> compatible(deal, board))
                        .mapToDouble(JointDeal::probability)
                        .sum();
        if (mass <= 0)
            throw new IllegalArgumentException("Flop is blocked by every reached six-seat deal");
        var posterior =
                deals.stream()
                        .filter(deal -> compatible(deal, board))
                        .map(deal -> new JointDeal(deal.hands(), deal.probability() / mass))
                        .toList();
        return new FlopState(board, posterior, mass / FLOPS_PER_DEAL);
    }

    /** Draw a reached six-hand deal first, then a uniform legal unordered flop. */
    public FlopState sampleFlop(long seed) {
        var random = new SplittableRandom(seed);
        var deal = draw(deals, random);
        var deck = remainingDeck(deal, List.of());
        List<Card> board = new ArrayList<>();
        for (int i = 0; i < 3; i++) board.add(deck.remove(random.nextInt(deck.size())));
        return conditionOnFlop(board);
    }

    public final class FlopState {
        private final List<Card> board;
        private final List<JointDeal> posterior;
        private final double probability;

        private FlopState(List<Card> board, List<JointDeal> posterior, double probability) {
            this.board = List.copyOf(board);
            this.posterior = List.copyOf(posterior);
            this.probability = probability;
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

        public List<Card> undealtCards(int dealIndex) {
            return List.copyOf(remainingDeck(posterior.get(dealIndex), board));
        }

        public Runout sampleRunout(long seed) {
            var random = new SplittableRandom(seed);
            var deal = draw(posterior, random);
            var deck = remainingDeck(deal, board);
            var runout = new ArrayList<>(board);
            runout.add(deck.remove(random.nextInt(deck.size())));
            runout.add(deck.remove(random.nextInt(deck.size())));
            return new Runout(deal, runout);
        }

        /** Exact checkdown baseline over 666 turn/river pairs per compatible six-hand deal. */
        public Checkdown exactCheckdown() {
            double firstShare = 0;
            long runouts = 0;
            for (var deal : posterior) {
                var deck = remainingDeck(deal, board);
                Card[][] hands = new Card[2][7];
                for (int player = 0; player < 2; player++) {
                    var combo =
                            deal.hands().get((player == 0 ? firstToAct : secondToAct).ordinal());
                    hands[player][0] = combo.first();
                    hands[player][1] = combo.second();
                    for (int card = 0; card < 3; card++) hands[player][card + 2] = board.get(card);
                }
                double sum = 0;
                long count = 0;
                for (int turn = 0; turn < deck.size() - 1; turn++)
                    for (int river = turn + 1; river < deck.size(); river++) {
                        for (var hand : hands) {
                            hand[5] = deck.get(turn);
                            hand[6] = deck.get(river);
                        }
                        int first = HandEvaluator.evaluateBestScore(hands[0]);
                        int second = HandEvaluator.evaluateBestScore(hands[1]);
                        sum += first > second ? 1 : first == second ? 0.5 : 0;
                        count++;
                    }
                firstShare += deal.probability() * sum / count;
                runouts += count;
            }
            var shares = Map.of(firstToAct, firstShare, secondToAct, 1 - firstShare);
            Map<Seat, Double> utilities = new LinkedHashMap<>();
            for (Seat seat : Seat.values())
                utilities.put(
                        seat,
                        potBb() * shares.getOrDefault(seat, 0.0) - publicState.committedBb(seat));
            return new Checkdown(utilities, shares, runouts);
        }
    }

    private static SixMaxPreflopCheckdownGame.State replay(
            SixMaxPreflopCheckdownGame game,
            SixMaxPreflopCheckdownGame.State state,
            List<PublicAction> history) {
        for (var action : history) {
            if (game.isTerminal(state)
                    || game.currentPlayer(state) != action.seat().ordinal()
                    || !game.legalActions(state).contains(action.action()))
                throw new IllegalArgumentException("Illegal public preflop history");
            state = game.afterAction(state, action.action());
        }
        return state;
    }

    private static List<Card> canonicalFlop(List<Card> flop) {
        if (flop == null
                || flop.size() != 3
                || flop.stream().anyMatch(Objects::isNull)
                || flop.stream().distinct().count() != 3)
            throw new IllegalArgumentException("Three distinct flop cards are required");
        return flop.stream().sorted(Comparator.comparing(Card::compact)).toList();
    }

    private static boolean compatible(JointDeal deal, List<Card> board) {
        return deal.hands().stream()
                .noneMatch(
                        combo -> board.contains(combo.first()) || board.contains(combo.second()));
    }

    private static List<Card> remainingDeck(JointDeal deal, List<Card> board) {
        Deck deck = new Deck();
        for (var combo : deal.hands()) {
            deck.remove(combo.first());
            deck.remove(combo.second());
        }
        for (var card : board) deck.remove(card);
        return new ArrayList<>(deck.cards());
    }

    private static JointDeal draw(List<JointDeal> deals, SplittableRandom random) {
        double threshold = random.nextDouble();
        double cumulative = 0;
        for (var deal : deals) {
            cumulative += deal.probability();
            if (threshold < cumulative) return deal;
        }
        return deals.getLast();
    }
}
