package com.pokerlab.solver;

import java.util.*;

/** Immutable public context and cached grades from an owned qualified preflop result only. */
public final class SixMaxRootHandBankRaiseDrill {
    public static final String SCHEMA = "pokerlab-bank-raise-drill/v1";
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
            if ((sourceCaseIndex != 0 && sourceCaseIndex != 2)
                    || action == null
                    || !ACTIONS.contains(action))
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
        private final Map<Integer, Map<String, Grade>> grades;

        private Result(Snapshot snapshot, Map<Integer, Map<String, Grade>> grades) {
            this.snapshot = snapshot;
            var copies = new LinkedHashMap<Integer, Map<String, Grade>>();
            grades.forEach((index, moves) -> copies.put(index, Map.copyOf(moves)));
            this.grades = Map.copyOf(copies);
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
            return snapshot.questions().stream()
                    .filter(q -> q.sourceCaseIndex() == index)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No owned qualified question"));
        }

        public Grade grade(String expected, int index, String action) {
            pin(expected);
            new Attempt(index, action);
            return grades.get(index).get(action);
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
                    new Counts(
                            (int)
                                    attempts.stream()
                                            .map(Attempt::sourceCaseIndex)
                                            .distinct()
                                            .count(),
                            within,
                            outside,
                            dependent));
        }
    }

    private SixMaxRootHandBankRaiseDrill() {}

    public static Result create(SixMaxRootHandBankRaiseFeedback.Result owned) throws Exception {
        Objects.requireNonNull(owned);
        var report = owned.report();
        String hash = SixMaxHeadsUpPreflopGame.hash(report);
        var qualified =
                owned.qualifiedFeedback()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Owned qualified feedback required"));
        if (!SixMaxRootHandBankRaiseDrillLoader.STUDY_HASH.equals(hash)
                || !qualified.reportHash().equals(hash)
                || !qualified
                        .sourceMinimumHash()
                        .equals(SixMaxRootHandBankRaiseFeedback.SOURCE_HASH)
                || !report.qualifiedForOfflineDrill()
                || report.trainerAdmission()
                || !report.assessments().stream()
                        .map(SixMaxRootHandBankRaiseFeedback.Assessment::sourceQuestionIndex)
                        .toList()
                        .equals(List.of(0, 2)))
            throw new IllegalArgumentException("Complete owned two-hand feedback required");
        var questions = new ArrayList<Question>();
        var grades = new LinkedHashMap<Integer, Map<String, Grade>>();
        for (var assessment : report.assessments()) {
            int index = assessment.sourceQuestionIndex();
            if (!assessment.qualifiedForOfflineDrill()
                    || !assessment.rejectionReasons().isEmpty()
                    || !assessment.moves().stream().map(m -> m.action()).toList().equals(ACTIONS))
                throw new IllegalArgumentException(
                        "Every original hand must retain qualified complete feedback");
            var context = assessment.context();
            double committed =
                    context.players().stream()
                            .filter(p -> p.seat() == context.actingSeat())
                            .findFirst()
                            .orElseThrow()
                            .committedBb();
            if (!Double.isFinite(committed) || committed != 1.)
                throw new IllegalArgumentException("Original posted BB commitment required");
            var moves =
                    assessment.moves().stream()
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
            questions.add(
                    new Question(
                            index, assessment.heroCards(), List.of(), context, EV_SCOPE, moves));
            var handGrades = new LinkedHashMap<String, Grade>();
            moves.forEach(m -> handGrades.put(m.action(), new Grade(hash, index, EV_SCOPE, m)));
            grades.put(index, handGrades);
        }
        return new Result(new Snapshot(SCHEMA, false, hash, EV_SCOPE, questions), grades);
    }
}
