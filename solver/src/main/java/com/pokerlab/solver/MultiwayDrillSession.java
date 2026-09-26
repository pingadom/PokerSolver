package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** Stateless ten-decision research session, replayable against one immutable solution. */
public final class MultiwayDrillSession {
    public static final int LENGTH = 10;

    public record Attempt(
            int index,
            MultiwayCallTrainer.Question question,
            MultiwayCallTrainer.Feedback feedback) {}

    public record Review(List<Attempt> attempts, double totalEvLossBb, double averageEvLossBb) {
        public Review {
            attempts = List.copyOf(attempts);
        }
    }

    private final MultiwayCallTrainer trainer;
    private final int players;

    public MultiwayDrillSession(MultiwayPreflopCallGame game, CfrSolution solution) {
        trainer = new MultiwayCallTrainer(game, solution);
        players = game.playerCount();
    }

    /** player=0 mixes all responding seats; otherwise practices the specified seat index. */
    public MultiwayCallTrainer.Question question(long sessionSeed, int index, int player) {
        if (index < 0 || index >= LENGTH)
            throw new IllegalArgumentException("Question index must be between 0 and 9");
        if (player < 0 || player >= players)
            throw new IllegalArgumentException("Unknown responding seat");
        SplittableRandom random = new SplittableRandom(sessionSeed);
        long questionSeed = 0;
        int sampledPlayer = 1;
        for (int current = 0; current <= index; current++) {
            questionSeed = random.nextLong();
            sampledPlayer = random.nextInt(1, players);
        }
        return trainer.question(questionSeed, player == 0 ? sampledPlayer : player);
    }

    public Attempt grade(
            long sessionSeed, int index, int player, MultiwayCallTrainer.Action action) {
        Objects.requireNonNull(action, "action");
        var question = question(sessionSeed, index, player);
        return new Attempt(index, question, trainer.grade(question, action));
    }

    /** Recomputes all scores; callers supply only their chosen actions, never EV values. */
    public Review review(long sessionSeed, int player, List<MultiwayCallTrainer.Action> actions) {
        if (actions == null
                || actions.size() != LENGTH
                || actions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A session review requires exactly ten actions");
        List<Attempt> attempts = new ArrayList<>();
        double total = 0;
        for (int index = 0; index < LENGTH; index++) {
            Attempt attempt = grade(sessionSeed, index, player, actions.get(index));
            attempts.add(attempt);
            total += attempt.feedback().evLossBb();
        }
        return new Review(attempts, total, total / LENGTH);
    }
}
