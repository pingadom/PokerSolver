package com.pokerlab.solver;

import java.util.*;

/** Immutable offline question/grade cache created only from owned qualified study capabilities. */
public final class SixMaxSizeMenuBtnDrill {
    public static final String SCHEMA = "pokerlab-size-menu-btn-drill/v1";
    public static final int MAX_ATTEMPTS = 128;

    public record Question(
            int sourceCaseIndex,
            String heroCards,
            SixMaxSuppliedFlopSizeMenuGame.PublicState context,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        public Question {
            moves = List.copyOf(moves);
        }
    }

    public record Snapshot(
            String schemaVersion,
            boolean trainerAdmission,
            String studyHash,
            List<Question> questions) {
        public Snapshot {
            questions = List.copyOf(questions);
        }
    }

    public record Grade(
            String studyHash, int sourceCaseIndex, SixMaxSuppliedIntervalFeedback.Move move) {}

    public record Attempt(int sourceCaseIndex, String action) {
        public Attempt {
            if (sourceCaseIndex < 0
                    || sourceCaseIndex >= 4
                    || !List.of("fold", "call").contains(action))
                throw new IllegalArgumentException("Valid source case and action required");
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

    private record Key(int sourceCaseIndex, String action) {}

    public static final class Result {
        private final Snapshot snapshot;
        private final Map<Key, Grade> grades;

        private Result(Snapshot snapshot, Map<Key, Grade> grades) {
            this.snapshot = snapshot;
            this.grades = Map.copyOf(grades);
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        private void pin(String expectedStudyHash) {
            if (!snapshot.studyHash().equals(expectedStudyHash))
                throw new IllegalArgumentException("Study pin differs");
        }

        public Grade grade(String expectedStudyHash, int sourceCaseIndex, String action) {
            pin(expectedStudyHash);
            var found = grades.get(new Key(sourceCaseIndex, action));
            if (found == null) throw new IllegalArgumentException("No owned qualified grade");
            return found;
        }

        public Review review(String expectedStudyHash, List<Attempt> attempts) {
            pin(expectedStudyHash);
            attempts = List.copyOf(attempts);
            if (attempts.size() > MAX_ATTEMPTS)
                throw new IllegalArgumentException("Review attempt cap exceeded");
            var rows = new ArrayList<ReviewedAttempt>();
            var distinct = new HashSet<Integer>();
            int within = 0, outside = 0, dependent = 0;
            for (var attempt : attempts) {
                var grade = grade(expectedStudyHash, attempt.sourceCaseIndex(), attempt.action());
                rows.add(new ReviewedAttempt(rows.size(), attempt, grade));
                distinct.add(attempt.sourceCaseIndex());
                switch (grade.move().classification()) {
                    case WITHIN_LIMIT -> within++;
                    case OUTSIDE_LIMIT -> outside++;
                    case STRATEGY_DEPENDENT -> dependent++;
                }
            }
            return new Review(
                    snapshot.studyHash(),
                    rows,
                    new Counts(distinct.size(), within, outside, dependent));
        }
    }

    private SixMaxSizeMenuBtnDrill() {}

    public static Result create(SixMaxSizeMenuBtnStudy.Result owned) throws Exception {
        Objects.requireNonNull(owned);
        var report = owned.report();
        String hash = SixMaxHeadsUpPreflopGame.hash(report);
        if (!owned.qualifiedCases().keySet().equals(Set.copyOf(report.qualifiedIndices())))
            throw new IllegalArgumentException("Owned qualification inventory differs");
        var questions = new ArrayList<Question>();
        var grades = new LinkedHashMap<Key, Grade>();
        for (int index : report.qualifiedIndices()) {
            var cap = owned.qualifiedCases().get(index);
            var item = report.cases().get(index);
            if (cap.index() != index
                    || !cap.studyHash().equals(hash)
                    || !item.qualifiedForOfflineDrill()
                    || !item.rejectionReasons().isEmpty())
                throw new IllegalArgumentException("Qualified case provenance differs");
            questions.add(new Question(index, item.heroCards(), item.context(), item.moves()));
            for (var move : item.moves())
                grades.put(new Key(index, move.action()), new Grade(hash, index, move));
        }
        if (questions.isEmpty()) throw new IllegalArgumentException("No owned qualified questions");
        return new Result(new Snapshot(SCHEMA, false, hash, questions), grades);
    }
}
