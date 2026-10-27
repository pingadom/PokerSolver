package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Twenty decisions: eighteen frozen questions and two separately qualified pot-size BB responses.
 */
public final class SixMaxSizingFlopCollection {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-review/v7";
    public static final String STATUS = "TWENTY_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ORDERED_LEGACY_EIGHTEEN_THEN_POT_SIZE_BB_QUESTIONS/v7";
    public static final String BELIEFS = "INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1";
    public static final int MAX_QUESTIONS = 20;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public enum Kind {
        LEGACY_EIGHTEEN_QUALIFIED_COLLECTION,
        POT_SIZE_BIG_BLIND_QUESTIONS
    }

    public record Decision(
            Kind kind,
            SixMaxUnpairedFlopCollection.Question legacy,
            SixMaxBigBlindPairwiseDrill.Question pot) {
        public Decision {
            if (kind == null
                    || (kind == Kind.LEGACY_EIGHTEEN_QUALIFIED_COLLECTION) != (legacy != null)
                    || (kind == Kind.POT_SIZE_BIG_BLIND_QUESTIONS) != (pot != null))
                throw new IllegalArgumentException("Exactly one matching owned question required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : pot.reportHash();
        }

        public List<String> legalActions() {
            return legacy != null ? legacy.decision().legalActions() : pot.table().legalActions();
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
                    || (decision.pot() != null
                            && (decision.pot().index() != local(index)
                                    || decision.pot().totalQuestions() != 2)))
                throw new IllegalArgumentException(
                        "Qualified collection question identity differs");
        }
    }

    public record Grade(
            Kind kind,
            SixMaxUnpairedFlopCollection.Feedback legacy,
            SixMaxBigBlindPairwiseDrill.Feedback pot) {
        public Grade {
            if (kind == null
                    || (kind == Kind.LEGACY_EIGHTEEN_QUALIFIED_COLLECTION) != (legacy != null)
                    || (kind == Kind.POT_SIZE_BIG_BLIND_QUESTIONS) != (pot != null))
                throw new IllegalArgumentException("Exactly one matching owned grade required");
        }

        public String reportHash() {
            return legacy != null ? legacy.decision().reportHash() : pot.question().reportHash();
        }

        public SixMaxSuppliedIntervalFeedback.Move selected() {
            return legacy != null ? legacy.decision().selected() : pot.selected();
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
            "b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2";
    public static final String POT_HASH =
            "04e99982ef9775a83cbffa28f64aedc7e146dd95dc38f09ad73604df24828996";

    private record Identity(
            String schemaVersion,
            String selection,
            String beliefs,
            String legacyCollectionHash,
            String legacyManifestSha256,
            String potReportHash,
            String sharedInputHash,
            String potRequestSha256,
            String potStoredSha256) {}

    private final SixMaxUnpairedFlopCollection legacy;
    private final SixMaxBigBlindPairwiseFeedback.Result pot;
    private final String hash;
    private final List<Question> questions;

    // Only complete replayed caches enter this package-private boundary.
    SixMaxSizingFlopCollection(
            SixMaxUnpairedFlopCollection legacy,
            SixMaxBigBlindPairwiseFeedback.Result pot,
            String legacyManifestSha,
            String sharedInput,
            String potRequestSha,
            String potStoredSha)
            throws Exception {
        this.legacy = Objects.requireNonNull(legacy);
        this.pot = Objects.requireNonNull(pot);
        if (!pot.report().qualifiedIndices().equals(List.of(3, 5)))
            throw new IllegalArgumentException("Two owned pot-size BB questions required");
        hash =
                identity(
                        legacy.collectionHash(),
                        legacyManifestSha,
                        SixMaxHeadsUpPreflopGame.hash(pot.report()),
                        sharedInput,
                        potRequestSha,
                        potStoredSha);
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
                                    i < 18 ? legacy.question(i) : null,
                                    i >= 18
                                            ? SixMaxBigBlindPairwiseDrill.question(pot, i - 18)
                                            : null)));
        questions = List.copyOf(inventory);
    }

    static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Kind kind(int index) {
        return index < 18
                ? Kind.LEGACY_EIGHTEEN_QUALIFIED_COLLECTION
                : Kind.POT_SIZE_BIG_BLIND_QUESTIONS;
    }

    private static int local(int index) {
        return index < 18 ? index : index - 18;
    }

    static String identity(
            String legacyHash,
            String legacyManifestSha,
            String potHash,
            String sharedInput,
            String potRequestSha,
            String potStoredSha)
            throws Exception {
        if (!LEGACY_HASH.equals(legacyHash)
                || !POT_HASH.equals(potHash)
                || !pin(legacyManifestSha)
                || !pin(sharedInput)
                || !pin(potRequestSha)
                || !pin(potStoredSha))
            throw new IllegalArgumentException("Exact typed source identities required");
        return SixMaxHeadsUpPreflopGame.hash(
                new Identity(
                        SCHEMA,
                        SELECTION,
                        BELIEFS,
                        legacyHash,
                        legacyManifestSha,
                        potHash,
                        sharedInput,
                        potRequestSha,
                        potStoredSha));
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
                        d.pot() != null
                                ? SixMaxBigBlindPairwiseDrill.grade(pot, d.pot().index(), action)
                                : null));
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
