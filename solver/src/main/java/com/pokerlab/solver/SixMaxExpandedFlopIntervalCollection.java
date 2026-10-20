package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Ten independent decisions across two boards from four owned sources. This is not a full-hand
 * policy.
 */
public final class SixMaxExpandedFlopIntervalCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v3";
    public static final String STATUS = "TEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_TWO_BB_FOUR_BTN_CHECK_FOUR_BTN_FACING_BET/v3";
    public static final int MAX_QUESTIONS = 10;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        INITIAL_CHECK_BET,
        BB_FACING_BET,
        BTN_AFTER_CHECK,
        PAIRED_BTN_FACING_BET
    }

    /** Exactly one unchanged original question protocol. */
    public record Decision(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Question initial,
            SixMaxLaterFlopTrainer.Question later,
            SixMaxBtnFlopTrainer.Question btn,
            SixMaxPairedFlopTrainer.Question paired) {
        public Decision {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null)
                    || (kind == Kind.BTN_AFTER_CHECK) != (btn != null)
                    || (kind == Kind.PAIRED_BTN_FACING_BET) != (paired != null))
                throw new IllegalArgumentException("Exactly one matching typed question required");
        }

        public String reportHash() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.reportHash();
                case BB_FACING_BET -> later.reportHash();
                case BTN_AFTER_CHECK -> btn.reportHash();
                case PAIRED_BTN_FACING_BET -> paired.reportHash();
            };
        }

        public int sourceQuestionIndex() {
            return paired != null ? paired.questionIndex() : btn == null ? 0 : btn.questionIndex();
        }

        public List<String> legalActions() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.context().legalActions();
                case BB_FACING_BET -> later.context().legalActions();
                case BTN_AFTER_CHECK -> btn.context().legalActions();
                case PAIRED_BTN_FACING_BET -> paired.context().legalActions();
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
                    || sourceQuestionIndex != localIndex(index))
                throw new IllegalArgumentException("Mixed collection question identity differs");
        }
    }

    public record Grade(
            Kind kind,
            SixMaxSuppliedFlopTrainer.Feedback initial,
            SixMaxLaterFlopTrainer.Feedback later,
            SixMaxBtnFlopTrainer.Feedback btn,
            SixMaxPairedFlopTrainer.Feedback paired) {
        public Grade {
            if (kind == null
                    || (kind == Kind.INITIAL_CHECK_BET) != (initial != null)
                    || (kind == Kind.BB_FACING_BET) != (later != null)
                    || (kind == Kind.BTN_AFTER_CHECK) != (btn != null)
                    || (kind == Kind.PAIRED_BTN_FACING_BET) != (paired != null))
                throw new IllegalArgumentException("Exactly one matching typed grade required");
        }

        public String reportHash() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.reportHash();
                case BB_FACING_BET -> later.reportHash();
                case BTN_AFTER_CHECK -> btn.reportHash();
                case PAIRED_BTN_FACING_BET -> paired.reportHash();
            };
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return switch (kind) {
                case INITIAL_CHECK_BET -> initial.selected();
                case BB_FACING_BET -> later.selected();
                case BTN_AFTER_CHECK -> btn.selected();
                case PAIRED_BTN_FACING_BET -> paired.selected();
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
                throw new IllegalArgumentException("Invalid expanded flop review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    private record SourceQuestion(
            Kind kind,
            String reportHash,
            int sourceQuestionIndex,
            List<String> board,
            String context) {}

    public static final List<String> ORIGINAL_BOARD = List.of("2s", "8s", "Ts");
    public static final List<String> PAIRED_BOARD = List.of("8d", "Kc", "Kd");

    private record Identity(
            String schemaVersion, String selection, List<SourceQuestion> questions) {}

    private final SixMaxSuppliedFlopTrainer initial;
    private final SixMaxLaterFlopTrainer later;
    private final SixMaxBtnFlopTrainer btn;
    private final SixMaxPairedFlopTrainer paired;
    private final String hash;
    private final List<Question> questions;

    /** Only owned, completely qualified results can grant grading authority. */
    public SixMaxExpandedFlopIntervalCollection(
            SixMaxSuppliedFlopFeedback.Result initialOwned,
            SixMaxLaterFlopFeedback.Result laterOwned,
            SixMaxBtnFlopCurriculum.Result btnOwned,
            SixMaxPairedFlopCurriculum.Result pairedOwned)
            throws Exception {
        initial = new SixMaxSuppliedFlopTrainer(Objects.requireNonNull(initialOwned));
        later = new SixMaxLaterFlopTrainer(Objects.requireNonNull(laterOwned));
        btn = new SixMaxBtnFlopTrainer(Objects.requireNonNull(btnOwned));
        paired = new SixMaxPairedFlopTrainer(Objects.requireNonNull(pairedOwned));
        var a = initialOwned.report();
        var b = laterOwned.report();
        var c = btnOwned.report();
        compatible(a.request(), b.request(), c.request(), pairedOwned.report().request());
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
                    "Original sources must share the supplied physical hand model");
        hash =
                identity(
                        initial.question().reportHash(),
                        later.question().reportHash(),
                        btn.reportHash(),
                        paired.reportHash());
        var inventory = new ArrayList<Question>();
        inventory.add(
                new Question(
                        hash,
                        0,
                        MAX_QUESTIONS,
                        SELECTION,
                        0,
                        new Decision(
                                Kind.INITIAL_CHECK_BET, initial.question(), null, null, null)));
        inventory.add(
                new Question(
                        hash,
                        1,
                        MAX_QUESTIONS,
                        SELECTION,
                        0,
                        new Decision(Kind.BB_FACING_BET, null, later.question(), null, null)));
        for (int i = 0; i < SixMaxBtnFlopTrainer.QUESTION_COUNT; i++)
            inventory.add(
                    new Question(
                            hash,
                            i + 2,
                            MAX_QUESTIONS,
                            SELECTION,
                            i,
                            new Decision(Kind.BTN_AFTER_CHECK, null, null, btn.question(i), null)));
        for (int i = 0; i < SixMaxPairedFlopTrainer.QUESTION_COUNT; i++) {
            var q = paired.question(i);
            if (!q.context().board().equals(PAIRED_BOARD)
                    || !q.context().publicPreflopHistory().equals(a.context().publicHistory())
                    || !q.context().preflopHistory().equals(a.context().preflopHistory())
                    || !q.context().legalActions().equals(List.of("fold", "call")))
                throw new IllegalArgumentException("Paired source context differs");
            inventory.add(
                    new Question(
                            hash,
                            i + 6,
                            MAX_QUESTIONS,
                            SELECTION,
                            i,
                            new Decision(Kind.PAIRED_BTN_FACING_BET, null, null, null, q)));
        }
        questions = List.copyOf(inventory);
    }

    static void compatible(
            SixMaxSuppliedFlopFeedback.Request a,
            SixMaxLaterFlopFeedback.Request b,
            SixMaxBtnFlopCurriculum.Request c,
            SixMaxPairedFlopCurriculum.Request d) {
        SixMaxMixedFlopIntervalCollection.compatible(a, b, c);
        var paired = d.questions().getFirst();
        var connected = paired.parent().connected();
        if (!a.connected().flop().equals(ORIGINAL_BOARD)
                || !connected.flop().equals(PAIRED_BOARD)
                || !a.connected().input().equals(connected.input())
                || a.connected().betPotFraction() != connected.betPotFraction()
                || !b.publicPreflopHistory().equals(paired.publicPreflopHistory())
                || a.maximumLossBb() != d.maximumLossBb()
                || b.maximumLossBb() != d.maximumLossBb()
                || c.maximumLossBb() != d.maximumLossBb())
            throw new IllegalArgumentException(
                    "Four compatible sources on the two declared boards required");
    }

    private static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Kind kindAt(int index) {
        return index == 0
                ? Kind.INITIAL_CHECK_BET
                : index == 1
                        ? Kind.BB_FACING_BET
                        : index < 6 ? Kind.BTN_AFTER_CHECK : Kind.PAIRED_BTN_FACING_BET;
    }

    /** The ordered source-local indices bind all four distinct BTN questions to this identity. */
    static String identity(String initialPin, String laterPin, String btnPin, String pairedPin)
            throws Exception {
        if (!pin(initialPin)
                || !pin(laterPin)
                || !pin(btnPin)
                || !pin(pairedPin)
                || Set.of(initialPin, laterPin, btnPin, pairedPin).size() != 4)
            throw new IllegalArgumentException("Four distinct typed report pins required");
        var inventory = new ArrayList<SourceQuestion>();
        inventory.add(
                new SourceQuestion(
                        Kind.INITIAL_CHECK_BET,
                        initialPin,
                        0,
                        ORIGINAL_BOARD,
                        "BB_INITIAL_CHECK_BET"));
        inventory.add(
                new SourceQuestion(
                        Kind.BB_FACING_BET, laterPin, 0, ORIGINAL_BOARD, "BB_FACING_BTN_BET"));
        for (int i = 0; i < SixMaxBtnFlopTrainer.QUESTION_COUNT; i++)
            inventory.add(
                    new SourceQuestion(
                            Kind.BTN_AFTER_CHECK, btnPin, i, ORIGINAL_BOARD, "BTN_AFTER_BB_CHECK"));
        for (int i = 0; i < SixMaxPairedFlopTrainer.QUESTION_COUNT; i++)
            inventory.add(
                    new SourceQuestion(
                            Kind.PAIRED_BTN_FACING_BET,
                            pairedPin,
                            i,
                            PAIRED_BOARD,
                            "BTN_FACING_BB_BET"));
        return SixMaxHeadsUpPreflopGame.hash(new Identity(SCHEMA, SELECTION, inventory));
    }

    static int localIndex(int index) {
        return index < 2 ? 0 : index < 6 ? index - 2 : index - 6;
    }

    public String collectionHash() {
        return hash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= MAX_QUESTIONS)
            throw new IllegalArgumentException("Question index outside expanded collection");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException(
                    "Question does not belong to this expanded collection");
        var d = candidate.decision();
        var grade =
                switch (d.kind()) {
                    case INITIAL_CHECK_BET ->
                            new Grade(
                                    d.kind(), initial.grade(d.initial(), action), null, null, null);
                    case BB_FACING_BET ->
                            new Grade(d.kind(), null, later.grade(d.later(), action), null, null);
                    case BTN_AFTER_CHECK ->
                            new Grade(d.kind(), null, null, btn.grade(d.btn(), action), null);
                    case PAIRED_BTN_FACING_BET ->
                            new Grade(d.kind(), null, null, null, paired.grade(d.paired(), action));
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
            throw new IllegalArgumentException("Exact expanded flop review replay differs");
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
