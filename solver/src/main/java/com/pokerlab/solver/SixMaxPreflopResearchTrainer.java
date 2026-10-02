package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Validation-only questions and conditional action EVs for the bounded six-seat preflop game. */
public final class SixMaxPreflopResearchTrainer {
    public record PublicAction(Seat seat, String action) {}

    /** Public betting history and the hero's cards; hidden hands and answer EVs are omitted. */
    public record Question(
            long seed,
            Seat actingSeat,
            String heroCombo,
            List<PublicAction> priorActions,
            List<String> legalActions,
            double potBb,
            double toCallBb,
            double stackBb,
            double smallBlindBb,
            double nashConvBb,
            double maximumPayoffStandardErrorBb,
            String publicationStatus) {
        public Question {
            priorActions = List.copyOf(priorActions);
            legalActions = List.copyOf(legalActions);
        }
    }

    /** One-step action EV assumes every later decision follows the fixed saved policy. */
    public record Feedback(
            String selectedAction,
            double selectedEvBb,
            double bestEvBb,
            double evLossBb,
            Map<String, Double> actionEvBb,
            Map<String, Double> actionFrequency) {
        public Feedback {
            actionEvBb = Map.copyOf(actionEvBb);
            actionFrequency = Map.copyOf(actionFrequency);
        }
    }

    private final SixMaxPreflopCheckdownGame game;
    private final CfrSolution solution;
    private final double nashConvBb;

    public SixMaxPreflopResearchTrainer(SixMaxPreflopCheckdownGame game, CfrSolution solution) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
        nashConvBb = MultiPlayerInformationSetBestResponse.assess(game, solution).nashConvBb();
    }

    /** Draws a dealt hand and policy trajectory, then uniformly selects one reached decision. */
    public Question question(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        var state = drawDeal(random);
        List<PublicAction> history = new ArrayList<>();
        SixMaxPreflopCheckdownGame.State selected = null;
        List<PublicAction> selectedHistory = List.of();
        int decisions = 0;
        while (!game.isTerminal(state)) {
            decisions++;
            if (random.nextInt(decisions) == 0) {
                selected = state;
                selectedHistory = List.copyOf(history);
            }
            String action = drawAction(state, random);
            history.add(new PublicAction(Seat.values()[game.currentPlayer(state)], action));
            state = game.afterAction(state, action);
        }
        if (selected == null) throw new IllegalStateException("No preflop decision in trajectory");
        Seat seat = Seat.values()[game.currentPlayer(selected)];
        return new Question(
                seed,
                seat,
                game.dealtHands(selected).get(seat.ordinal()).key(),
                selectedHistory,
                game.legalActions(selected),
                game.publicPotBb(selected),
                game.publicToCallBb(selected),
                game.stackBb(),
                game.smallBlindBb(),
                nashConvBb,
                game.maximumTerminalPayoffStandardErrorBb(),
                "VALIDATION_ONLY");
    }

    /**
     * Conditions on the shown hand and public action likelihoods, never on sampled hidden hands.
     */
    public Feedback grade(Question submitted, String selectedAction) {
        Objects.requireNonNull(submitted, "question");
        Objects.requireNonNull(selectedAction, "selectedAction");
        Question question = question(submitted.seed());
        if (!question.equals(submitted))
            throw new IllegalArgumentException("Question does not match this game and solution");
        if (!question.legalActions().contains(selectedAction))
            throw new IllegalArgumentException("Illegal selected action");
        int target = question.actingSeat().ordinal();
        double mass = 0;
        SixMaxPreflopCheckdownGame.State reached = null;
        Map<String, Double> weighted = new LinkedHashMap<>();
        for (String action : question.legalActions()) weighted.put(action, 0.0);
        Map<SixMaxPreflopCheckdownGame.State, Double> continuations = new HashMap<>();
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            if (!game.dealtHands(state).get(target).key().equals(question.heroCombo())) continue;
            double reach = outcome.probability();
            for (PublicAction prior : question.priorActions()) {
                if (game.currentPlayer(state) != prior.seat().ordinal())
                    throw new IllegalStateException("Question has inconsistent betting order");
                reach *=
                        MultiPlayerStrategyEvaluator.probability(
                                game, solution, state, prior.action());
                state = game.afterAction(state, prior.action());
            }
            if (reach > 0 && reached == null) reached = state;
            mass += reach;
            for (String action : question.legalActions())
                weighted.merge(
                        action,
                        reach
                                * continuation(
                                        game.afterAction(state, action), target, continuations),
                        Double::sum);
        }
        if (mass <= 0) throw new IllegalStateException("Question has no reachable hidden deals");
        Map<String, Double> ev = new LinkedHashMap<>();
        for (var entry : weighted.entrySet()) ev.put(entry.getKey(), entry.getValue() / mass);
        double selectedEv = ev.get(selectedAction);
        double bestEv = ev.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        Map<String, Double> frequency = solution.at(target, game.informationSet(reached));
        if (frequency == null) throw new IllegalStateException("Missing decision strategy");
        return new Feedback(
                selectedAction,
                selectedEv,
                bestEv,
                Math.max(0, bestEv - selectedEv),
                ev,
                frequency);
    }

    private SixMaxPreflopCheckdownGame.State drawDeal(SplittableRandom random) {
        double draw = random.nextDouble();
        double cumulative = 0;
        SixMaxPreflopCheckdownGame.State selected = null;
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            cumulative += outcome.probability();
            selected = outcome.state();
            if (draw < cumulative) break;
        }
        return Objects.requireNonNull(selected);
    }

    private String drawAction(SixMaxPreflopCheckdownGame.State state, SplittableRandom random) {
        double draw = random.nextDouble();
        double cumulative = 0;
        String selected = null;
        for (String action : game.legalActions(state)) {
            cumulative += MultiPlayerStrategyEvaluator.probability(game, solution, state, action);
            selected = action;
            if (draw < cumulative) break;
        }
        return Objects.requireNonNull(selected);
    }

    private double continuation(
            SixMaxPreflopCheckdownGame.State state,
            int target,
            Map<SixMaxPreflopCheckdownGame.State, Double> cache) {
        Double cached = cache.get(state);
        if (cached != null) return cached;
        double value;
        if (game.isTerminal(state)) value = game.terminalUtilities(state)[target];
        else {
            value = 0;
            for (String action : game.legalActions(state))
                value +=
                        MultiPlayerStrategyEvaluator.probability(game, solution, state, action)
                                * continuation(game.afterAction(state, action), target, cache);
        }
        cache.put(state, value);
        return value;
    }
}
