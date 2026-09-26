package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** Deterministic ten-decision session over one immutable two-player all-in solution. */
public final class PreflopDrillSession {
    public static final int LENGTH = 10;

    public record Attempt(
            int index, PreflopTrainer.Question question, PreflopTrainer.Feedback feedback) {}

    public record Review(List<Attempt> attempts, double totalEvLossBb, double averageEvLossBb) {
        public Review {
            attempts = List.copyOf(attempts);
        }
    }

    private final PreflopTrainer trainer;

    public PreflopDrillSession(PreflopTrainer trainer) {
        this.trainer = Objects.requireNonNull(trainer, "trainer");
    }

    public PreflopTrainer.Question question(long sessionSeed, int index) {
        if (index < 0 || index >= LENGTH)
            throw new IllegalArgumentException("Question index must be between 0 and 9");
        var random = new SplittableRandom(sessionSeed);
        long questionSeed = 0;
        for (int current = 0; current <= index; current++) questionSeed = random.nextLong();
        return trainer.question(questionSeed);
    }

    public Attempt grade(long sessionSeed, int index, PreflopTrainer.Action action) {
        Objects.requireNonNull(action, "action");
        var question = question(sessionSeed, index);
        return new Attempt(index, question, trainer.grade(question, action));
    }

    public Review review(long sessionSeed, List<PreflopTrainer.Action> actions) {
        if (actions == null
                || actions.size() != LENGTH
                || actions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A session review requires exactly ten actions");
        List<Attempt> attempts = new ArrayList<>(LENGTH);
        double total = 0;
        for (int index = 0; index < LENGTH; index++) {
            Attempt attempt = grade(sessionSeed, index, actions.get(index));
            attempts.add(attempt);
            total += attempt.feedback().evLossBb();
        }
        return new Review(attempts, total, total / LENGTH);
    }
}
