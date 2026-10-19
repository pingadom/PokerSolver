package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Six independent decisions from three owned sources. This is not a full-hand policy. */
public final class SixMaxMixedFlopIntervalCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v2";
    public static final String STATUS = "SIX_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_TWO_BB_AND_FOUR_BTN_FLOP_QUESTIONS/v2";
    public static final int MAX_QUESTIONS = 6;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        INITIAL_CHECK_BET,
        BB_FACING_BET,
        BTN_AFTER_CHECK
    }

    /** Exactly one unchanged original question protocol. */
    public record Decision(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Question initial,
            SixMaxLaterFlopTrainer.Question later,
            SixMaxBtnFlopTrainer.Question btn) {
        public Decision {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null)
                    || (kind == Kind.BTN_AFTER_CHECK) != (btn != null))
                throw new IllegalArgumentException("Exactly one matching typed question required");
        }

        public String reportHash() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.reportHash();
                case BB_FACING_BET -> later.reportHash();
                case BTN_AFTER_CHECK -> btn.reportHash();
            };
        }

        public int sourceQuestionIndex() {
            return btn == null ? 0 : btn.questionIndex();
        }

        public List<String> legalActions() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.context().legalActions();
                case BB_FACING_BET -> later.context().legalActions();
                case BTN_AFTER_CHECK -> btn.context().legalActions();
            };
        }
    }

    public record Question(
            String collectionHash,
            int index,
            int totalQuestions,
            String selection,
            int sourceQuestionIndex,
            Decision decision) {
        public Question {
            if (!pin(collectionHash)
                    || index < 0
                    || index >= MAX_QUESTIONS
                    || totalQuestions != MAX_QUESTIONS
                    || !SELECTION.equals(selection)
                    || decision == null
                    || sourceQuestionIndex != decision.sourceQuestionIndex()
                    || decision.kind() != kindAt(index)
                    || sourceQuestionIndex != (index < 2 ? 0 : index - 2))
                throw new IllegalArgumentException("Mixed collection question identity differs");
        }
    }

    public record Grade(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Feedback initial,
            SixMaxLaterFlopTrainer.Feedback later,
            SixMaxBtnFlopTrainer.Feedback btn) {
        public Grade {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null)
                    || (kind == Kind.BTN_AFTER_CHECK) != (btn != null))
                throw new IllegalArgumentException("Exactly one matching typed grade required");
        }

        public String reportHash() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.reportHash();
                case BB_FACING_BET -> later.reportHash();
                case BTN_AFTER_CHECK -> btn.reportHash();
            };
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.selected();
                case BB_FACING_BET -> later.selected();
                case BTN_AFTER_CHECK -> btn.selected();
            };
        }
    }

    public record Feedback(
            String collectionHash, int questionIndex, int sourceQuestionIndex, Grade decision) {}

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
                    || !pin(collectionHash)
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
                throw new IllegalArgumentException("Invalid mixed flop review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    private record SourceQuestion(Kind kind, String reportHash, int sourceQuestionIndex) {}

    private record Identity(
            String schemaVersion, String selection, List<SourceQuestion> questions) {}

    private final SixMaxSuppliedFlopTrainer initial;
    private final SixMaxLaterFlopTrainer later;
    private final SixMaxBtnFlopTrainer btn;
    private final String hash;
    private final List<Question> questions;

    /** Only owned, completely qualified results can grant grading authority. */
    public SixMaxMixedFlopIntervalCollection(
            SixMaxSuppliedFlopFeedback.Result initialOwned,
            SixMaxLaterFlopFeedback.Result laterOwned,
            SixMaxBtnFlopCurriculum.Result btnOwned)
            throws Exception {
        initial = new SixMaxSuppliedFlopTrainer(Objects.requireNonNull(initialOwned));
        later = new SixMaxLaterFlopTrainer(Objects.requireNonNull(laterOwned));
        btn = new SixMaxBtnFlopTrainer(Objects.requireNonNull(btnOwned));
        var a = initialOwned.report();
        var b = laterOwned.report();
        var c = btnOwned.report();
        compatible(a.request(), b.request(), c.request());
        if (a.context().hero().ordinal() != b.request().hero()
                || !a.context().heroCards().equals(b.request().heroCards())
                || !a.context().board().equals(b.context().board())
                || !a.context().publicHistory().equals(b.context().publicPreflopHistory())
                || !a.context().preflopHistory().equals(b.context().preflopHistory())
                || c.decisions().stream()
                        .anyMatch(
                                d ->
                                        !a.context().board().equals(d.context().board())
                                                || !a.context()
                                                        .publicHistory()
                                                        .equals(d.context().publicPreflopHistory())
                                                || !a.context()
                                                        .preflopHistory()
                                                        .equals(d.context().preflopHistory())))
            throw new IllegalArgumentException(
                    "Mixed sources must share the supplied physical hand model");
        hash =
                identity(
                        initial.question().reportHash(),
                        later.question().reportHash(),
                        btn.reportHash());
        var inventory = new ArrayList<Question>();
        inventory.add(
                new Question(
                        hash,
                        0,
                        MAX_QUESTIONS,
                        SELECTION,
                        0,
                        new Decision(Kind.INITIAL_CHECK_BET, initial.question(), null, null)));
        inventory.add(
                new Question(
                        hash,
                        1,
                        MAX_QUESTIONS,
                        SELECTION,
                        0,
                        new Decision(Kind.BB_FACING_BET, null, later.question(), null)));
        for (int i = 0; i < SixMaxBtnFlopTrainer.QUESTION_COUNT; i++)
            inventory.add(
                    new Question(
                            hash,
                            i + 2,
                            MAX_QUESTIONS,
                            SELECTION,
                            i,
                            new Decision(Kind.BTN_AFTER_CHECK, null, null, btn.question(i))));
        questions = List.copyOf(inventory);
    }

    static void compatible(
            SixMaxSuppliedFlopFeedback.Request a,
            SixMaxLaterFlopFeedback.Request b,
            SixMaxBtnFlopCurriculum.Request c) {
        if (!a.connected().equals(b.connected())
                || !a.connected().equals(c.connected())
                || a.hero() != 5
                || b.hero() != 5
                || !b.publicPreflopHistory().equals(c.publicPreflopHistory()))
            throw new IllegalArgumentException("Three compatible BB/BTN source requests required");
    }

    private static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Kind kindAt(int index) {
        return index == 0
                ? Kind.INITIAL_CHECK_BET
                : index == 1 ? Kind.BB_FACING_BET : Kind.BTN_AFTER_CHECK;
    }

    /** The ordered source-local indices bind all four distinct BTN questions to this identity. */
    static String identity(String initialPin, String laterPin, String btnPin) throws Exception {
        if (!pin(initialPin)
                || !pin(laterPin)
                || !pin(btnPin)
                || Set.of(initialPin, laterPin, btnPin).size() != 3)
            throw new IllegalArgumentException("Three distinct typed report pins required");
        var inventory = new ArrayList<SourceQuestion>();
        inventory.add(new SourceQuestion(Kind.INITIAL_CHECK_BET, initialPin, 0));
        inventory.add(new SourceQuestion(Kind.BB_FACING_BET, laterPin, 0));
        for (int i = 0; i < SixMaxBtnFlopTrainer.QUESTION_COUNT; i++)
            inventory.add(new SourceQuestion(Kind.BTN_AFTER_CHECK, btnPin, i));
        return SixMaxHeadsUpPreflopGame.hash(new Identity(SCHEMA, SELECTION, inventory));
    }

    public String collectionHash() {
        return hash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= MAX_QUESTIONS)
            throw new IllegalArgumentException("Question index outside mixed collection");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException("Question does not belong to this mixed collection");
        var d = candidate.decision();
        var grade =
                switch (d.kind()) {
                    case INITIAL_CHECK_BET ->
                            new Grade(d.kind(), initial.grade(d.initial(), action), null, null);
                    case BB_FACING_BET ->
                            new Grade(d.kind(), null, later.grade(d.later(), action), null);
                    case BTN_AFTER_CHECK ->
                            new Grade(d.kind(), null, null, btn.grade(d.btn(), action));
                };
        return new Feedback(hash, candidate.index(), candidate.sourceQuestionIndex(), grade);
    }

    public Review review(List<Choice> choices) {
        if (choices == null
                || choices.size() > MAX_ATTEMPTS
                || choices.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Between zero and 100 explicit choices required");
        var attempts = new ArrayList<Attempt>();
        var seen = new HashSet<Integer>();
        int within = 0, outside = 0, dependent = 0;
        for (var choice : List.copyOf(choices)) {
            var q = question(choice.questionIndex());
            if (!q.decision().reportHash().equals(choice.reportHash()))
                throw new IllegalArgumentException("Choice source pin differs");
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
            throw new IllegalArgumentException("Saved mixed inventory or identity differs");
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
            throw new IllegalArgumentException("Exact mixed flop review replay differs");
        return expected;
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public String reviewJson(List<Choice> choices) throws Exception {
        return document(review(choices));
    }

    private static String document(Review review) throws Exception {
        var text = SixMaxTextureStudy.json(review);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded mixed review exceeds cap");
        return text;
    }

    public void write(Path path, Review review) throws Exception {
        replay(review);
        path = path.toAbsolutePath().normalize();
        byte[] bytes = document(review).getBytes(StandardCharsets.UTF_8);
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored mixed review exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
