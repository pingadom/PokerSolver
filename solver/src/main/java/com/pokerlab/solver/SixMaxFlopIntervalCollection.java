package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Two independently qualified contexts; cached decisions do not establish a full-hand policy. */
public final class SixMaxFlopIntervalCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v1";
    public static final String STATUS = "TWO_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_INITIAL_AND_LATER_BB_FLOP_QUESTIONS/v1";
    public static final int MAX_QUESTIONS = 2;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        INITIAL_CHECK_BET,
        BB_FACING_BET
    }

    /** Explicit typed union retains both original question protocols, without hidden evidence. */
    public record Decision(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Question initial,
            SixMaxLaterFlopTrainer.Question later) {
        public Decision {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null))
                throw new IllegalArgumentException(
                        "Exactly one matching typed flop question required");
        }

        public String reportHash() {
            return initial != null ? initial.reportHash() : later.reportHash();
        }
    }

    public record Question(
            String collectionHash,
            int index,
            int totalQuestions,
            String selection,
            Decision decision) {}

    public record Grade(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Feedback initial,
            SixMaxLaterFlopTrainer.Feedback later) {
        public Grade {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null))
                throw new IllegalArgumentException(
                        "Exactly one matching typed flop grade required");
        }

        public String reportHash() {
            return initial != null ? initial.reportHash() : later.reportHash();
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return initial != null ? initial.selected() : later.selected();
        }
    }

    public record Feedback(String collectionHash, int questionIndex, Grade decision) {}

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
                    || questions.size() != MAX_QUESTIONS
                    || questions.stream().anyMatch(Objects::isNull)
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS
                    || attempts.stream()
                            .anyMatch(
                                    a ->
                                            a == null
                                                    || a.action() == null
                                                    || a.feedback() == null
                                                    || a.feedback().decision() == null)
                    || counts == null)
                throw new IllegalArgumentException("Invalid flop collection review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    private record SourcePin(Kind kind, String reportHash) {}

    private record Identity(String schemaVersion, String selection, List<SourcePin> sources) {}

    private final SixMaxSuppliedFlopTrainer initial;
    private final SixMaxLaterFlopTrainer later;
    private final String hash;
    private final List<Question> questions;

    /** Public reports/hashes cannot construct either private owned qualification capability. */
    public SixMaxFlopIntervalCollection(
            SixMaxSuppliedFlopFeedback.Result initialOwned,
            SixMaxLaterFlopFeedback.Result laterOwned)
            throws Exception {
        initial = new SixMaxSuppliedFlopTrainer(Objects.requireNonNull(initialOwned));
        later = new SixMaxLaterFlopTrainer(Objects.requireNonNull(laterOwned));
        var a = initialOwned.report();
        var b = laterOwned.report();
        if (!a.request().connected().equals(b.request().connected())
                || a.context().hero().ordinal() != b.request().hero()
                || !a.context().heroCards().equals(b.request().heroCards())
                || !a.context().board().equals(b.context().board())
                || !a.context().publicHistory().equals(b.context().publicPreflopHistory())
                || !a.context().preflopHistory().equals(b.context().preflopHistory()))
            throw new IllegalArgumentException(
                    "Flop collection sources must share the supplied hand model");
        hash = identity(initial.question().reportHash(), later.question().reportHash());
        questions =
                List.of(
                        new Question(
                                hash,
                                0,
                                MAX_QUESTIONS,
                                SELECTION,
                                new Decision(Kind.INITIAL_CHECK_BET, initial.question(), null)),
                        new Question(
                                hash,
                                1,
                                MAX_QUESTIONS,
                                SELECTION,
                                new Decision(Kind.BB_FACING_BET, null, later.question())));
    }

    /** String-only canonical identity; calculating a pin grants no qualification authority. */
    static String identity(String initialPin, String laterPin) throws Exception {
        if (initialPin == null
                || laterPin == null
                || !initialPin.matches("[0-9a-f]{64}")
                || !laterPin.matches("[0-9a-f]{64}")
                || initialPin.equals(laterPin))
            throw new IllegalArgumentException("Two distinct typed source pins required");
        return SixMaxHeadsUpPreflopGame.hash(
                new Identity(
                        SCHEMA,
                        SELECTION,
                        List.of(
                                new SourcePin(Kind.INITIAL_CHECK_BET, initialPin),
                                new SourcePin(Kind.BB_FACING_BET, laterPin))));
    }

    public String collectionHash() {
        return hash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= MAX_QUESTIONS)
            throw new IllegalArgumentException("Question index outside flop collection");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException(
                    "Question does not belong to this pinned flop collection");
        var d = candidate.decision();
        var grade =
                d.kind() == Kind.INITIAL_CHECK_BET
                        ? new Grade(d.kind(), initial.grade(d.initial(), action), null)
                        : new Grade(d.kind(), null, later.grade(d.later(), action));
        return new Feedback(hash, candidate.index(), grade);
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
            var q = question(choice.questionIndex());
            if (!q.decision().reportHash().equals(choice.reportHash()))
                throw new IllegalArgumentException("Choice report pin differs");
            var feedback = grade(q, choice.action());
            attempts.add(new Attempt(attempts.size(), q.index(), choice.action(), feedback));
            seen.add(q.index());
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
            throw new IllegalArgumentException("Saved flop collection identity/order differs");
        var expected =
                review(
                        saved.attempts().stream()
                                .map(
                                        a ->
                                                new Choice(
                                                        a.questionIndex(),
                                                        a.feedback().decision().reportHash(),
                                                        a.action()))
                                .toList());
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Exact flop collection replay differs");
        return expected;
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public void write(Path path, Review review) throws Exception {
        replay(review);
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded flop collection exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored flop collection exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
