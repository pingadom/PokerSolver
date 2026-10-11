package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Ordered distinct qualified questions. Asking, grading and review use cached owned evidence. */
public final class SixMaxSuppliedIntervalCollection {
    public static final String SCHEMA = "pokerlab-supplied-interval-collection-review/v1";
    public static final String STATUS = "ORDERED_INTERVAL_COLLECTION_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_DISTINCT_QUALIFIED_INTERVAL_QUESTIONS/v1";
    public static final int MAX_QUESTIONS = 16;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public record Question(
            String collectionHash,
            int index,
            int totalQuestions,
            String selection,
            SixMaxSuppliedIntervalTrainer.Question decision) {}

    public record Feedback(
            String collectionHash,
            int questionIndex,
            SixMaxSuppliedIntervalTrainer.Feedback decision) {}

    public record Choice(int questionIndex, String reportHash, String action) {}

    public record Attempt(int index, int questionIndex, String action, Feedback feedback) {}

    public record Counts(
            int distinctQuestionsAttempted,
            int withinLimit,
            int outsideLimit,
            int strategyDependent) {}

    public record Review(
            String schemaVersion,
            String collectionHash,
            String selection,
            List<Question> questions,
            List<Attempt> attempts,
            Counts counts) {
        public Review {
            if (!SCHEMA.equals(schemaVersion)
                    || !SELECTION.equals(selection)
                    || collectionHash == null
                    || !collectionHash.matches("[0-9a-f]{64}")
                    || questions == null
                    || questions.size() < 2
                    || questions.size() > MAX_QUESTIONS
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS
                    || counts == null
                    || questions.stream().anyMatch(Objects::isNull)
                    || attempts.stream()
                            .anyMatch(
                                    a ->
                                            a == null
                                                    || a.feedback() == null
                                                    || a.feedback().decision() == null
                                                    || a.action() == null))
                throw new IllegalArgumentException("Invalid interval collection review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    private record SourceIdentity(
            String reportHash, String inputHash, int hero, String informationSet) {}

    private record Identity(String schemaVersion, String selection, List<SourceIdentity> sources) {}

    private final String hash;
    private final List<SixMaxSuppliedIntervalTrainer> trainers;
    private final List<Question> questions;

    public SixMaxSuppliedIntervalCollection(List<SixMaxSuppliedIntervalFeedback.Result> owned)
            throws Exception {
        if (owned == null
                || owned.size() < 2
                || owned.size() > MAX_QUESTIONS
                || owned.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Between two and sixteen owned questions required");
        owned = List.copyOf(owned);
        var bound = new ArrayList<SixMaxSuppliedIntervalTrainer>();
        for (var result : owned) bound.add(new SixMaxSuppliedIntervalTrainer(result));
        // The diagnostic Report record is public; only the owned result constructor above can
        // authorize grading. Identity calculation itself grants no authority.
        hash = identity(owned.stream().map(SixMaxSuppliedIntervalFeedback.Result::report).toList());
        trainers = List.copyOf(bound);
        var list = new ArrayList<Question>();
        for (int i = 0; i < trainers.size(); i++)
            list.add(new Question(hash, i, trainers.size(), SELECTION, trainers.get(i).question()));
        questions = List.copyOf(list);
    }

    static String identity(List<SixMaxSuppliedIntervalFeedback.Report> reports) throws Exception {
        if (reports == null || reports.size() < 2 || reports.size() > MAX_QUESTIONS)
            throw new IllegalArgumentException("Bounded distinct question inventory required");
        var sources = new ArrayList<SourceIdentity>();
        var seen = new HashSet<String>();
        for (var report : reports) {
            var r = Objects.requireNonNull(report).request();
            var inputHash = SixMaxHeadsUpPreflopGame.hash(r.input());
            if (!seen.add(inputHash + ":" + r.hero() + ":" + r.informationSet()))
                throw new IllegalArgumentException("Repeated target is not a distinct question");
            sources.add(
                    new SourceIdentity(
                            SixMaxHeadsUpPreflopGame.hash(report),
                            inputHash,
                            r.hero(),
                            r.informationSet()));
        }
        return SixMaxHeadsUpPreflopGame.hash(new Identity(SCHEMA, SELECTION, List.copyOf(sources)));
    }

    public String collectionHash() {
        return hash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= questions.size())
            throw new IllegalArgumentException("Question index outside collection");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException(
                    "Question does not belong to this pinned collection");
        return new Feedback(
                hash,
                candidate.index(),
                trainers.get(candidate.index()).grade(candidate.decision(), action));
    }

    public Review review(List<Choice> choices) {
        if (choices == null
                || choices.size() > MAX_ATTEMPTS
                || choices.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Between zero and 100 explicit choices required");
        choices = List.copyOf(choices);
        var attempts = new ArrayList<Attempt>();
        var seen = new HashSet<Integer>();
        int within = 0, outside = 0, dependent = 0;
        for (var choice : choices) {
            var question = question(choice.questionIndex());
            if (!question.decision().reportHash().equals(choice.reportHash()))
                throw new IllegalArgumentException("Choice report pin differs");
            var feedback = grade(question, choice.action());
            attempts.add(new Attempt(attempts.size(), question.index(), choice.action(), feedback));
            seen.add(question.index());
            switch (feedback.decision().selected().classification()) {
                case WITHIN_LIMIT -> within++;
                case OUTSIDE_LIMIT -> outside++;
                case STRATEGY_DEPENDENT -> dependent++;
            }
        }
        return new Review(
                SCHEMA,
                hash,
                SELECTION,
                questions,
                attempts,
                new Counts(seen.size(), within, outside, dependent));
    }

    public Review replay(Review saved) {
        if (saved == null
                || !hash.equals(saved.collectionHash())
                || !questions.equals(saved.questions()))
            throw new IllegalArgumentException("Saved collection identity/order differs");
        var choices =
                saved.attempts().stream()
                        .map(
                                a ->
                                        new Choice(
                                                a.questionIndex(),
                                                a.feedback().decision().reportHash(),
                                                a.action()))
                        .toList();
        var expected = review(choices);
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Exact collection review differs");
        return expected;
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public void write(Path path, Review review) throws Exception {
        replay(review);
        byte[] bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Collection review exceeds byte cap");
        path = path.toAbsolutePath().normalize();
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored collection review exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
