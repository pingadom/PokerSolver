package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Eighteen decisions: the original fourteen plus four hands on an unpaired physical flop. */
public final class SixMaxUnpairedFlopCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v6";
    public static final String STATUS = "EIGHTEEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION =
            "ORDERED_LEGACY_FOURTEEN_THEN_UNPAIRED_FLOP_QUESTIONS/v6";
    public static final String BELIEFS = "INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1";
    public static final int MAX_QUESTIONS = 18;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        LEGACY_FOURTEEN_QUALIFIED_COLLECTION,
        SHARED_REFERENCE_UNPAIRED_QUESTIONS
    }

    public record Decision(
            Kind kind,
            SixMaxCallFlopCollection.Question legacy,
            SixMaxSharedFlopTrainer.Question shared) {
        public Decision {
            if (kind == null
                    || (kind == Kind.LEGACY_FOURTEEN_QUALIFIED_COLLECTION) != (legacy != null)
                    || (kind == Kind.SHARED_REFERENCE_UNPAIRED_QUESTIONS) != (shared != null))
                throw new IllegalArgumentException("Exactly one matching owned question required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : shared.decision().reportHash();
        }

        public List<String> legalActions() {
            return legacy != null
                    ? legacy.decision().legalActions()
                    : shared.decision().context().legalActions();
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
                    || sourceQuestionIndex != local(index)
                    || decision.kind() != kind(index)
                    || (decision.legacy() != null && decision.legacy().index() != index)
                    || (decision.shared() != null
                            && (decision.shared().index() != local(index)
                                    || decision.shared().totalQuestions() != 4)))
                throw new IllegalArgumentException(
                        "Qualified collection question identity differs");
        }
    }

    public record Grade(
            Kind kind,
            SixMaxCallFlopCollection.Feedback legacy,
            SixMaxRefinedFlopTrainer.Feedback shared) {
        public Grade {
            if (kind == null
                    || (kind == Kind.LEGACY_FOURTEEN_QUALIFIED_COLLECTION) != (legacy != null)
                    || (kind == Kind.SHARED_REFERENCE_UNPAIRED_QUESTIONS) != (shared != null))
                throw new IllegalArgumentException("Exactly one matching owned grade required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : shared.reportHash();
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return legacy != null ? legacy.decision().selected() : shared.selected();
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
                    || !pin(collectionHash)
                    || !SELECTION.equals(selection)
                    || questions == null
                    || questions.size() != MAX_QUESTIONS
                    || questions.stream().anyMatch(Objects::isNull)
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS
                    || counts == null
                    || attempts.stream()
                            .anyMatch(
                                    a ->
                                            a == null
                                                    || a.action() == null
                                                    || a.feedback() == null
                                                    || a.feedback().decision() == null))
                throw new IllegalArgumentException("Invalid qualified collection review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    public static final String LEGACY_HASH =
            "62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619";
    public static final String BATCH_HASH =
            "c5845cabc3a93208520549d5893ea311ad3c5409f792b96729b0d325fab077c9";

    private record Identity(
            String schemaVersion,
            String selection,
            String beliefs,
            String legacyCollectionHash,
            String legacyManifestSha256,
            String batchReportHash,
            String sharedInputHash,
            String batchRequestSha256,
            String batchStoredSha256) {}

    private final SixMaxCallFlopCollection legacy;
    private final SixMaxSharedFlopTrainer shared;
    private final String hash;
    private final List<Question> questions;

    // Only complete replayed caches enter this package-private boundary.
    SixMaxUnpairedFlopCollection(
            SixMaxCallFlopCollection legacy,
            SixMaxSharedFlopTrainer batch,
            String legacyManifestSha,
            String sharedInput,
            String batchRequestSha,
            String batchStoredSha)
            throws Exception {
        this.legacy = Objects.requireNonNull(legacy);
        this.shared = Objects.requireNonNull(batch);
        if (batch.questions().size() != 4)
            throw new IllegalArgumentException("Four owned shared questions required");
        hash =
                identity(
                        legacy.collectionHash(),
                        legacyManifestSha,
                        batch.reportHash(),
                        sharedInput,
                        batchRequestSha,
                        batchStoredSha);
        var inventory = new ArrayList<Question>();
        for (int i = 0; i < MAX_QUESTIONS; i++)
            inventory.add(
                    new Question(
                            hash,
                            i,
                            MAX_QUESTIONS,
                            SELECTION,
                            local(i),
                            new Decision(
                                    kind(i),
                                    i < 14 ? legacy.question(i) : null,
                                    i >= 14 ? batch.question(i - 14) : null)));
        questions = List.copyOf(inventory);
    }

    static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Kind kind(int index) {
        return index < 14
                ? Kind.LEGACY_FOURTEEN_QUALIFIED_COLLECTION
                : Kind.SHARED_REFERENCE_UNPAIRED_QUESTIONS;
    }

    private static int local(int index) {
        return index < 14 ? index : index - 14;
    }

    static String identity(
            String legacyHash,
            String legacyManifestSha,
            String batchHash,
            String sharedInput,
            String batchRequestSha,
            String batchStoredSha)
            throws Exception {
        if (!LEGACY_HASH.equals(legacyHash)
                || !BATCH_HASH.equals(batchHash)
                || !pin(legacyManifestSha)
                || !pin(sharedInput)
                || !pin(batchRequestSha)
                || !pin(batchStoredSha))
            throw new IllegalArgumentException("Exact typed source identities required");
        return SixMaxHeadsUpPreflopGame.hash(
                new Identity(
                        SCHEMA,
                        SELECTION,
                        BELIEFS,
                        legacyHash,
                        legacyManifestSha,
                        batchHash,
                        sharedInput,
                        batchRequestSha,
                        batchStoredSha));
    }

    public String collectionHash() {
        return hash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= MAX_QUESTIONS)
            throw new IllegalArgumentException("Question outside qualified collection");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.index()).equals(candidate))
            throw new IllegalArgumentException("Foreign qualified collection question");
        var d = candidate.decision();
        return new Feedback(
                hash,
                candidate.index(),
                candidate.sourceQuestionIndex(),
                new Grade(
                        d.kind(),
                        d.legacy() != null ? legacy.grade(d.legacy(), action) : null,
                        d.shared() != null ? shared.grade(d.shared(), action) : null));
    }

    public Review review(List<Choice> choices) {
        if (choices == null
                || choices.size() > MAX_ATTEMPTS
                || choices.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Zero to 100 explicit choices required");
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
            throw new IllegalArgumentException("Saved qualified inventory or pin differs");
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
            throw new IllegalArgumentException("Exact qualified collection review differs");
        return expected;
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public String reviewJson(List<Choice> choices) throws Exception {
        String text = SixMaxTextureStudy.json(review(choices));
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Review exceeds cap");
        return text;
    }

    public void write(Path path, Review saved) throws Exception {
        replay(saved);
        var choices =
                saved.attempts().stream()
                        .map(
                                a ->
                                        new Choice(
                                                a.questionIndex(),
                                                a.feedback().decision().reportHash(),
                                                a.action()))
                        .toList();
        path = path.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.writeString(
                path, reviewJson(choices), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
