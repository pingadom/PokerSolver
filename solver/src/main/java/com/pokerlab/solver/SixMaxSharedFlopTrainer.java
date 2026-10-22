package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cached batch decisions. Only a complete owned qualification can create this trainer. */
public final class SixMaxSharedFlopTrainer {
    public static final String SCHEMA = "pokerlab-shared-flop-review/v1";
    public static final String STATUS = "SHARED_REFERENCE_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_ALL_QUALIFIED_SHARED_REFERENCE_QUESTIONS/v1";
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 512 * 1024;

    public record Question(
            int index, int totalQuestions, SixMaxRefinedFlopTrainer.Question decision) {}

    public record Choice(int questionIndex, String action) {}

    public record Attempt(
            int index,
            Question question,
            String action,
            SixMaxRefinedFlopTrainer.Feedback feedback) {}

    /** Attempt classifications and distinct scenario coverage have different denominators. */
    public record Counts(
            int withinLimit,
            int outsideLimit,
            int strategyDependent,
            int distinctQuestions,
            int totalQuestions) {}

    public record Review(
            String schemaVersion, String reportHash, List<Attempt> attempts, Counts counts) {
        public Review {
            if (!SCHEMA.equals(schemaVersion)
                    || reportHash == null
                    || !reportHash.matches("[0-9a-f]{64}")
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS
                    || attempts.stream()
                            .anyMatch(
                                    a ->
                                            a == null
                                                    || a.question() == null
                                                    || a.action() == null
                                                    || a.feedback() == null)
                    || counts == null)
                throw new IllegalArgumentException("Invalid shared flop review");
            attempts = List.copyOf(attempts);
        }
    }

    private final String reportHash;
    private final List<Question> questions;
    private final List<List<SixMaxSuppliedIntervalFeedback.Move>> moves;

    public SixMaxSharedFlopTrainer(SixMaxSharedFlopFeedback.Result owned) {
        Objects.requireNonNull(owned);
        reportHash =
                owned.qualifiedBatch()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Whole owned qualified shared batch required"))
                        .reportHash();
        var report = owned.report();
        var result = new ArrayList<Question>();
        var allMoves = new ArrayList<List<SixMaxSuppliedIntervalFeedback.Move>>();
        for (var item : report.cases()) {
            var c = item.conditional();
            result.add(
                    new Question(
                            item.index(),
                            report.cases().size(),
                            new SixMaxRefinedFlopTrainer.Question(
                                    reportHash,
                                    STATUS,
                                    false,
                                    SELECTION,
                                    c.informationSet(),
                                    c.context().actingSeat().name(),
                                    c.request().heroCards(),
                                    c.context(),
                                    SixMaxSuppliedFlopBettingGame.MODEL,
                                    SixMaxSuppliedFlopBettingGame.CHANCE,
                                    SixMaxSuppliedRangePreflopGame.BELIEFS,
                                    SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                                    report.request().maximumLossBb(),
                                    SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                                    SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR,
                                    c.reachUnits(),
                                    c.evScope())));
            allMoves.add(List.copyOf(item.moves()));
        }
        questions = List.copyOf(result);
        moves = List.copyOf(allMoves);
    }

    public String reportHash() {
        return reportHash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= questions.size())
            throw new IllegalArgumentException("Unknown shared question index");
        return questions.get(index);
    }

    public SixMaxRefinedFlopTrainer.Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException("Question does not belong to this shared batch");
        var menu = moves.get(candidate.index());
        var selected =
                menu.stream()
                        .filter(m -> m.action().equals(action))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Illegal shared question action"));
        return new SixMaxRefinedFlopTrainer.Feedback(
                reportHash,
                STATUS,
                false,
                candidate.decision().evScope(),
                candidate.decision().maximumLossBb(),
                selected,
                menu);
    }

    public Review review(List<Choice> choices) {
        if (choices == null
                || choices.size() > MAX_ATTEMPTS
                || choices.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException(
                    "Between zero and 100 explicit shared choices required");
        var attempts = new ArrayList<Attempt>();
        var seen = new HashSet<Integer>();
        int within = 0, outside = 0, dependent = 0;
        for (var choice : choices) {
            var q = question(choice.questionIndex());
            var feedback = grade(q, choice.action());
            seen.add(q.index());
            attempts.add(new Attempt(attempts.size(), q, choice.action(), feedback));
            switch (feedback.selected().classification()) {
                case WITHIN_LIMIT -> within++;
                case OUTSIDE_LIMIT -> outside++;
                case STRATEGY_DEPENDENT -> dependent++;
            }
        }
        return new Review(
                SCHEMA,
                reportHash,
                attempts,
                new Counts(within, outside, dependent, seen.size(), questions.size()));
    }

    public Review replay(Review saved) {
        if (saved == null || !reportHash.equals(saved.reportHash()))
            throw new IllegalArgumentException("Exact shared review pin required");
        var expected =
                review(
                        saved.attempts().stream()
                                .map(a -> new Choice(a.question().index(), a.action()))
                                .toList());
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Exact shared flop review replay differs");
        return expected;
    }

    public Review replay(Path path) throws Exception {
        return replay(read(path));
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public void write(Path path, Review review) throws Exception {
        replay(review);
        var bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded shared review exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored shared review exceeds cap");
        path = path.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
