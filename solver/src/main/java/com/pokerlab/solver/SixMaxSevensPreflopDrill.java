package com.pokerlab.solver;

import java.util.*;

/** Immutable public context and cached grades from an owned qualified preflop result only. */
public final class SixMaxSevensPreflopDrill {
    public static final String SCHEMA = "pokerlab-sevens-preflop-drill/v1";
    public static final String EV_SCOPE =
            "CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1";
    public static final int MAX_ATTEMPTS = 128;
    public static final List<String> ACTIONS = List.of("fold", "call", "raise:9.0");

    public record Question(
            int sourceCaseIndex,
            String heroCards,
            List<String> board,
            SixMaxSizeMenuPreflopPlan.Table context,
            String evScope,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        public Question {
            board = List.copyOf(board);
            moves = List.copyOf(moves);
        }
    }

    public record Snapshot(
            String schemaVersion,
            boolean trainerAdmission,
            String studyHash,
            String evScope,
            List<Question> questions) {
        public Snapshot {
            questions = List.copyOf(questions);
        }
    }

    public record Grade(
            String studyHash,
            int sourceCaseIndex,
            String evScope,
            SixMaxSuppliedIntervalFeedback.Move move) {}

    public record Attempt(int sourceCaseIndex, String action) {
        public Attempt {
            if (sourceCaseIndex != 0 || action == null || !ACTIONS.contains(action))
                throw new IllegalArgumentException("Original case and legal action required");
        }
    }

    public record ReviewedAttempt(int ordinal, Attempt attempt, Grade grade) {}

    public record Counts(
            int distinctQuestions, int withinLimit, int outsideLimit, int strategyDependent) {}

    public record Review(String studyHash, List<ReviewedAttempt> attempts, Counts counts) {
        public Review {
            attempts = List.copyOf(attempts);
        }
    }

    public static final class Result {
        private final Snapshot snapshot;
        private final Map<String, Grade> grades;

        private Result(Snapshot snapshot, Map<String, Grade> grades) {
            this.snapshot = snapshot;
            this.grades = Map.copyOf(grades);
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        private void pin(String expected) {
            if (!snapshot.studyHash().equals(expected))
                throw new IllegalArgumentException("Study pin differs");
        }

        public Question question(String expected, int index) {
            pin(expected);
            if (index != 0) throw new IllegalArgumentException("No owned qualified question");
            return snapshot.questions().getFirst();
        }

        public Grade grade(String expected, int index, String action) {
            pin(expected);
            new Attempt(index, action);
            return grades.get(action);
        }

        public Review review(String expected, List<Attempt> attempts) {
            pin(expected);
            Objects.requireNonNull(attempts);
            if (attempts.size() > MAX_ATTEMPTS)
                throw new IllegalArgumentException("Review attempt cap exceeded");
            attempts = List.copyOf(attempts);
            var rows = new ArrayList<ReviewedAttempt>();
            int within = 0, outside = 0, dependent = 0;
            for (var attempt : attempts) {
                var grade = grade(expected, attempt.sourceCaseIndex(), attempt.action());
                rows.add(new ReviewedAttempt(rows.size(), attempt, grade));
                switch (grade.move().classification()) {
                    case WITHIN_LIMIT -> within++;
                    case OUTSIDE_LIMIT -> outside++;
                    case STRATEGY_DEPENDENT -> dependent++;
                }
            }
            return new Review(
                    snapshot.studyHash(),
                    rows,
                    new Counts(rows.isEmpty() ? 0 : 1, within, outside, dependent));
        }
    }

    private SixMaxSevensPreflopDrill() {}

    public static Result create(SixMaxSevensRootStudy.Result owned) throws Exception {
        Objects.requireNonNull(owned);
        var report = owned.report();
        String hash = SixMaxHeadsUpPreflopGame.hash(report);
        if (!SixMaxSevensPreflopDrillLoader.STUDY_HASH.equals(hash)
                || !report.feedbackSupported()
                || !report.completeOuterBounds()
                || !report.rejectionReasons().isEmpty()
                || !report.moves().stream().map(m -> m.action()).toList().equals(ACTIONS))
            throw new IllegalArgumentException("Complete owned sevens qualification required");
        var context = report.context();
        double committed =
                context.players().stream()
                        .filter(p -> p.seat() == context.actingSeat())
                        .findFirst()
                        .orElseThrow()
                        .committedBb();
        if (!Double.isFinite(committed) || committed < 0)
            throw new IllegalArgumentException("Original hero commitment required");
        var moves =
                report.moves().stream()
                        .map(
                                m ->
                                        new SixMaxSuppliedIntervalFeedback.Move(
                                                m.action(),
                                                m.lowerEvBb() + committed,
                                                m.upperEvBb() + committed,
                                                m.lowerLossBb(),
                                                m.upperLossBb(),
                                                m.classification()))
                        .toList();
        var question =
                new Question(0, report.request().heroCards(), List.of(), context, EV_SCOPE, moves);
        var grades = new LinkedHashMap<String, Grade>();
        moves.forEach(m -> grades.put(m.action(), new Grade(hash, 0, EV_SCOPE, m)));
        return new Result(new Snapshot(SCHEMA, false, hash, EV_SCOPE, List.of(question)), grades);
    }
}
