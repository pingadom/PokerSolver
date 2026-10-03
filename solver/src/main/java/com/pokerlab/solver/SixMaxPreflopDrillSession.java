package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** Replayable ten-decision sessions bound to the complete immutable full-round artifact. */
public final class SixMaxPreflopDrillSession {
    public static final int LENGTH = 10;
    public static final double MAX_NASH_CONV_BB = 0.05;

    public record Question(
            long sessionSeed,
            int index,
            String packHash,
            SixMaxPreflopResearchTrainer.Question decision) {}

    public record Attempt(Question question, SixMaxPreflopResearchTrainer.Feedback feedback) {}

    public record Review(
            String packHash, List<Attempt> attempts, double totalEvLossBb, double averageEvLossBb) {
        public Review {
            attempts = List.copyOf(attempts);
        }
    }

    private final String packHash;
    private final SixMaxPreflopResearchTrainer trainer;

    public SixMaxPreflopDrillSession(SixMaxPreflopSolutionPack pack) {
        Objects.requireNonNull(pack, "pack");
        var game = pack.rebuildGame();
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(pack.payoffMethod())
                || pack.maxTerminalPayoffSEBb() != 0)
            throw new IllegalArgumentException("Full-round sessions require exact-board payoffs");
        if (pack.nashConvBb() > MAX_NASH_CONV_BB)
            throw new IllegalArgumentException("Full-round pack exceeds the deviation threshold");
        packHash = MultiwayPackJson.fullRoundContentHash(pack);
        trainer = new SixMaxPreflopResearchTrainer(game, pack.solution());
    }

    public Question question(long sessionSeed, int index) {
        if (index < 0 || index >= LENGTH)
            throw new IllegalArgumentException("Question index must be between 0 and 9");
        var random = new SplittableRandom(sessionSeed);
        long seed = 0;
        for (int current = 0; current <= index; current++) seed = random.nextLong();
        return new Question(sessionSeed, index, packHash, trainer.question(seed));
    }

    public String packHash() {
        return packHash;
    }

    /** Recreates the shown decision and rejects altered cards, history or artifact identity. */
    public Attempt grade(Question submitted, String action) {
        Objects.requireNonNull(submitted, "question");
        var expected = question(submitted.sessionSeed(), submitted.index());
        if (!expected.equals(submitted))
            throw new IllegalArgumentException("Question does not match this session and pack");
        return new Attempt(expected, trainer.grade(expected.decision(), action));
    }

    public Review review(long sessionSeed, String submittedPackHash, List<String> actions) {
        if (!packHash.equals(submittedPackHash))
            throw new IllegalArgumentException("Pack hash does not match this session");
        if (actions == null
                || actions.size() != LENGTH
                || actions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A review needs exactly ten actions");
        List<Attempt> attempts = new ArrayList<>();
        double total = 0;
        for (int index = 0; index < LENGTH; index++) {
            var attempt = grade(question(sessionSeed, index), actions.get(index));
            attempts.add(attempt);
            total += attempt.feedback().evLossBb();
        }
        return new Review(packHash, attempts, total, total / LENGTH);
    }
}
