package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Deterministic, validation-only drill for the bounded multiway call/fold subgame. */
public final class MultiwayCallTrainer {
    public enum Action {
        CALL,
        FOLD
    }

    public record PublicAction(PreflopAllInSpot.Seat seat, Action action) {}

    public record Question(
            long seed,
            int actingPlayer,
            PreflopAllInSpot.Seat actingSeat,
            String heroCombo,
            List<PublicAction> priorResponses,
            double potBb,
            double callCostBb,
            double stackBb,
            double maximumPayoffStandardErrorBb,
            double nashConvBb,
            String publicationStatus,
            List<Action> legalActions) {
        public Question {
            priorResponses = List.copyOf(priorResponses);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Feedback(
            Action selectedAction,
            double selectedEvBb,
            double bestEvBb,
            double evLossBb,
            double callEvBb,
            double foldEvBb,
            double callFrequency,
            double foldFrequency) {}

    private final MultiwayPreflopCallGame game;
    private final CfrSolution solution;
    private final double nashConvBb;

    public MultiwayCallTrainer(MultiwayPreflopCallGame game, CfrSolution solution) {
        this.game = Objects.requireNonNull(game, "game");
        this.solution = Objects.requireNonNull(solution, "solution");
        nashConvBb = MultiwayCallBestResponse.assess(game, solution).nashConvBb();
    }

    public Question question(long seed, int actingPlayer) {
        if (actingPlayer < 1 || actingPlayer >= game.playerCount())
            throw new IllegalArgumentException("Choose a responding seat");
        SplittableRandom random = new SplittableRandom(seed);
        double draw = random.nextDouble();
        double cumulative = 0;
        MultiwayPreflopCallGame.State state = null;
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            cumulative += outcome.probability();
            state = outcome.state();
            if (draw < cumulative) break;
        }
        List<PublicAction> prior = new ArrayList<>();
        for (int player = 1; player < actingPlayer; player++) {
            double callProbability =
                    MultiPlayerStrategyEvaluator.probability(game, solution, state, "c");
            Action action = random.nextDouble() < callProbability ? Action.CALL : Action.FOLD;
            prior.add(new PublicAction(game.seats().get(player), action));
            state = game.afterAction(state, symbol(action));
        }
        return new Question(
                seed,
                actingPlayer,
                game.seats().get(actingPlayer),
                game.dealtCombos(state).get(actingPlayer).key(),
                prior,
                game.potBeforeDecision(state.history()),
                game.callCostBb(actingPlayer),
                game.stacksBb().get(actingPlayer),
                game.maximumTerminalPayoffStandardErrorBb(),
                nashConvBb,
                "VALIDATION_ONLY",
                List.of(Action.CALL, Action.FOLD));
    }

    /**
     * Grades against the conditional distribution of hidden hands, not one sampled opponent deal.
     */
    public Feedback grade(Question submitted, Action action) {
        Objects.requireNonNull(submitted, "question");
        Objects.requireNonNull(action, "action");
        Question question = question(submitted.seed(), submitted.actingPlayer());
        if (!question.equals(submitted))
            throw new IllegalArgumentException("Question does not match this solution");
        StringBuilder history = new StringBuilder();
        for (PublicAction prior : question.priorResponses()) history.append(symbol(prior.action()));
        double mass = 0;
        double call = 0;
        double fold = 0;
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            if (!game.dealtCombos(state)
                    .get(question.actingPlayer())
                    .key()
                    .equals(question.heroCombo())) continue;
            double reach = outcome.probability();
            for (int index = 0; index < history.length(); index++) {
                String priorAction = String.valueOf(history.charAt(index));
                reach *=
                        MultiPlayerStrategyEvaluator.probability(
                                game, solution, state, priorAction);
                state = game.afterAction(state, priorAction);
            }
            mass += reach;
            call += reach * continuation(game.afterAction(state, "c"), question.actingPlayer());
            fold += reach * continuation(game.afterAction(state, "f"), question.actingPlayer());
        }
        if (mass <= 0) throw new IllegalStateException("Question has no reachable hidden deals");
        call /= mass;
        fold /= mass;
        double selected = action == Action.CALL ? call : fold;
        double best = Math.max(call, fold);
        Map<String, Double> strategy =
                solution.at(question.actingPlayer(), question.heroCombo() + ":" + history);
        if (strategy == null) throw new IllegalArgumentException("Missing decision strategy");
        return new Feedback(
                action,
                selected,
                best,
                Math.max(0, best - selected),
                call,
                fold,
                strategy.get("c"),
                strategy.get("f"));
    }

    private double continuation(MultiwayPreflopCallGame.State state, int target) {
        if (game.isTerminal(state)) return game.terminalUtilities(state)[target];
        double value = 0;
        for (String action : game.legalActions(state))
            value +=
                    MultiPlayerStrategyEvaluator.probability(game, solution, state, action)
                            * continuation(game.afterAction(state, action), target);
        return value;
    }

    private static String symbol(Action action) {
        return action == Action.CALL ? "c" : "f";
    }
}
