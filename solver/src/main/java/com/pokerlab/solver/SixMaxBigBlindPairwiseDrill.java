package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cached public questions and replayable reviews, bound to a complete owned qualification. */
public final class SixMaxBigBlindPairwiseDrill {
    public static final String SCHEMA = "pokerlab-big-blind-pairwise-review/v1";
    public static final String STATUS = "QUALIFIED_BOUNDED_BIG_BLIND_PAIRWISE_DRILL_RESEARCH_ONLY";
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 1024 * 1024;

    public record Question(
            String reportHash,
            int index,
            int totalQuestions,
            int sourceIndex,
            String heroCards,
            SixMaxSuppliedFlopBettingGame.PublicFlopState table) {}

    public record Feedback(
            Question question,
            String action,
            SixMaxSuppliedIntervalFeedback.Move selected,
            List<SixMaxSuppliedIntervalFeedback.Move> alternatives) {
        public Feedback {
            alternatives = List.copyOf(alternatives);
        }
    }

    public record Choice(int questionIndex, String action) {}

    public record Attempt(int index, Feedback feedback) {}

    public record Summary(
            int attempts,
            int questionsAttempted,
            int withinLimit,
            int outsideLimit,
            int strategyDependent) {}

    public record Review(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String reportHash,
            List<Integer> qualifiedSourceIndices,
            List<Attempt> attempts,
            Summary summary) {
        public Review {
            qualifiedSourceIndices = List.copyOf(qualifiedSourceIndices);
            attempts = List.copyOf(attempts);
            if (!SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || reportHash == null
                    || !reportHash.matches("[0-9a-f]{64}")
                    || qualifiedSourceIndices.isEmpty()
                    || attempts.size() > MAX_ATTEMPTS)
                throw new IllegalArgumentException("Bounded pinned BB review required");
            for (int i = 0; i < attempts.size(); i++) {
                var attempt = attempts.get(i);
                var feedback = attempt.feedback();
                var question = feedback.question();
                if (attempt.index() != i
                        || !reportHash.equals(question.reportHash())
                        || question.index() < 0
                        || question.index() >= qualifiedSourceIndices.size()
                        || question.totalQuestions() != qualifiedSourceIndices.size()
                        || question.sourceIndex() != qualifiedSourceIndices.get(question.index())
                        || !feedback.action().equals(feedback.selected().action()))
                    throw new IllegalArgumentException(
                            "BB review question or action identity differs");
            }
            if (!summarize(attempts).equals(summary))
                throw new IllegalArgumentException("BB review summary differs");
        }
    }

    private SixMaxBigBlindPairwiseDrill() {}

    public static Question question(SixMaxBigBlindPairwiseFeedback.Result owned, int index) {
        Objects.requireNonNull(owned);
        var qualified = owned.qualifiedQuestions();
        if (index < 0 || index >= qualified.size())
            throw new IllegalArgumentException("Unknown qualified BB question");
        var capability = qualified.get(index);
        var conditional =
                owned.report().study().source().cases().get(capability.index()).conditional();
        return new Question(
                capability.reportHash(),
                index,
                qualified.size(),
                capability.index(),
                conditional.request().heroCards(),
                conditional.context());
    }

    public static Feedback grade(
            SixMaxBigBlindPairwiseFeedback.Result owned, int index, String action) {
        var q = question(owned, index);
        var selected = SixMaxBigBlindPairwiseFeedback.grade(owned, q.sourceIndex(), action);
        return new Feedback(
                q, action, selected, owned.report().study().cases().get(q.sourceIndex()).moves());
    }

    private static Summary summarize(List<Attempt> attempts) {
        var questions = new HashSet<Integer>();
        int within = 0, outside = 0, dependent = 0;
        for (var attempt : attempts) {
            questions.add(attempt.feedback().question().index());
            switch (attempt.feedback().selected().classification()) {
                case WITHIN_LIMIT -> within++;
                case OUTSIDE_LIMIT -> outside++;
                case STRATEGY_DEPENDENT -> dependent++;
            }
        }
        return new Summary(attempts.size(), questions.size(), within, outside, dependent);
    }

    public static List<Choice> canonicalChoices(SixMaxBigBlindPairwiseFeedback.Result owned) {
        var choices = new ArrayList<Choice>();
        for (int i = 0; i < owned.qualifiedQuestions().size(); i++)
            for (String action : question(owned, i).table().legalActions())
                choices.add(new Choice(i, action));
        return List.copyOf(choices);
    }

    public static Review review(SixMaxBigBlindPairwiseFeedback.Result owned, List<Choice> choices) {
        choices = List.copyOf(choices);
        if (choices.size() > MAX_ATTEMPTS)
            throw new IllegalArgumentException("BB review attempt cap exceeded");
        if (owned.qualifiedQuestions().isEmpty())
            throw new IllegalArgumentException("No BB question qualified; no review authority");
        var attempts = new ArrayList<Attempt>();
        for (var choice : choices)
            attempts.add(
                    new Attempt(
                            attempts.size(),
                            grade(owned, choice.questionIndex(), choice.action())));
        return new Review(
                SCHEMA,
                STATUS,
                false,
                question(owned, 0).reportHash(),
                owned.report().qualifiedIndices(),
                attempts,
                summarize(attempts));
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    public static void verify(SixMaxBigBlindPairwiseFeedback.Result owned, Review saved) {
        var choices =
                saved.attempts().stream()
                        .map(
                                a ->
                                        new Choice(
                                                a.feedback().question().index(),
                                                a.feedback().action()))
                        .toList();
        if (!review(owned, choices).equals(saved))
            throw new IllegalArgumentException("Complete cached BB review regrade differs");
    }

    public static void write(
            Path path, SixMaxBigBlindPairwiseFeedback.Result owned, List<Choice> choices)
            throws Exception {
        var review = review(owned, choices);
        byte[] bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded BB review exceeds cap");
        path = path.toAbsolutePath().normalize();
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored BB review exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
