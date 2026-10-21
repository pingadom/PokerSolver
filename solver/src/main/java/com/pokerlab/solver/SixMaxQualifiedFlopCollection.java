package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Eleven independent decisions; the added source has different supplied joint hand beliefs. */
public final class SixMaxQualifiedFlopCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v4";
    public static final String STATUS = "ELEVEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_LEGACY_TEN_THEN_QUALIFIED_WEAK_HAND/v4";
    public static final String BELIEFS = "INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1";
    public static final int MAX_QUESTIONS = 11;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        LEGACY_TEN_QUESTION_COLLECTION,
        QUALIFIED_WEAK_HAND_FLOP
    }

    public record Decision(
            Kind kind,
            SixMaxExpandedFlopIntervalCollection.Question legacy,
            SixMaxRefinedFlopTrainer.Question weak) {
        public Decision {
            if (kind == null
                    || (kind == Kind.LEGACY_TEN_QUESTION_COLLECTION) != (legacy != null)
                    || (kind == Kind.QUALIFIED_WEAK_HAND_FLOP) != (weak != null))
                throw new IllegalArgumentException("Exactly one matching owned question required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : weak.reportHash();
        }

        public List<String> legalActions() {
            return legacy != null
                    ? legacy.decision().legalActions()
                    : weak.context().legalActions();
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
                    || (decision.legacy() != null && decision.legacy().index() != index))
                throw new IllegalArgumentException(
                        "Qualified collection question identity differs");
        }
    }

    public record Grade(
            Kind kind,
            SixMaxExpandedFlopIntervalCollection.Feedback legacy,
            SixMaxRefinedFlopTrainer.Feedback weak) {
        public Grade {
            if (kind == null
                    || (kind == Kind.LEGACY_TEN_QUESTION_COLLECTION) != (legacy != null)
                    || (kind == Kind.QUALIFIED_WEAK_HAND_FLOP) != (weak != null))
                throw new IllegalArgumentException("Exactly one matching owned grade required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : weak.reportHash();
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return legacy != null ? legacy.decision().selected() : weak.selected();
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

    private record Identity(
            String schemaVersion,
            String selection,
            String beliefs,
            String legacyCollectionHash,
            String legacyInputHash,
            String weakReportHash,
            String weakInputHash) {}

    private final SixMaxExpandedFlopIntervalCollection legacy;
    private final SixMaxWeakHandFlopTrainer weak;
    private final String hash;
    private final List<Question> questions;

    // Package-private: only the loader supplies both fully replayed caches and verified identities.
    SixMaxQualifiedFlopCollection(
            SixMaxExpandedFlopIntervalCollection legacy,
            SixMaxWeakHandFlopTrainer weak,
            String legacyInputHash,
            String weakInputHash)
            throws Exception {
        this.legacy = Objects.requireNonNull(legacy);
        this.weak = Objects.requireNonNull(weak);
        hash =
                identity(
                        legacy.collectionHash(),
                        legacyInputHash,
                        weak.question().reportHash(),
                        weakInputHash);
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
                                    i < 10 ? legacy.question(i) : null,
                                    i == 10 ? weak.question() : null)));
        questions = List.copyOf(inventory);
    }

    static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Kind kind(int index) {
        return index < 10 ? Kind.LEGACY_TEN_QUESTION_COLLECTION : Kind.QUALIFIED_WEAK_HAND_FLOP;
    }

    private static int local(int index) {
        return index < 10 ? index : 0;
    }

    static String identity(String legacyHash, String legacyInput, String weakHash, String weakInput)
            throws Exception {
        if (!pin(legacyHash)
                || !pin(legacyInput)
                || !pin(weakInput)
                || legacyInput.equals(weakInput)
                || !SixMaxWeakHandFlopTrainer.REPORT_HASH.equals(weakHash))
            throw new IllegalArgumentException(
                    "Distinct source input identities and pinned weak report required");
        return SixMaxHeadsUpPreflopGame.hash(
                new Identity(
                        SCHEMA, SELECTION, BELIEFS, legacyHash, legacyInput, weakHash, weakInput));
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
                        d.weak() != null ? weak.grade(d.weak(), action) : null));
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
