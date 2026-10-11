package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One selected offline drill. Question, grading and replay use only cached owned evidence. */
public final class SixMaxSuppliedIntervalTrainer {
    public static final String SCHEMA = "pokerlab-supplied-interval-drill/v1";
    public static final String STATUS = "SELECTED_QUESTION_INTERVAL_DRILL_RESEARCH_ONLY";
    public static final String SELECTION = "SINGLE_QUALIFIED_SELECTED_QUESTION/v1";
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 256 * 1024;

    public record Question(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String model,
            String beliefs,
            String sourceHistoryReachStatus,
            String informationSet,
            Seat hero,
            String heroCards,
            List<PublicAction> history,
            SixMaxPreflopPublicTable.Snapshot table,
            List<String> legalActions,
            double maximumLossBb,
            double securitySlackBb,
            double minimumReach) {
        public Question {
            history = List.copyOf(history);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Feedback(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            double maximumLossBb,
            SixMaxSuppliedIntervalFeedback.Move selected,
            List<SixMaxSuppliedIntervalFeedback.Move> alternatives) {
        public Feedback {
            alternatives = List.copyOf(alternatives);
        }
    }

    public record Attempt(int index, String action, Feedback feedback) {}

    /**
     * Counts describe repeated attempts at this one question, not scenario coverage or accuracy.
     */
    public record Counts(int withinLimit, int outsideLimit, int strategyDependent) {}

    public record Review(
            String schemaVersion, Question question, List<Attempt> attempts, Counts counts) {
        public Review {
            if (!SCHEMA.equals(schemaVersion)
                    || question == null
                    || counts == null
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS)
                throw new IllegalArgumentException("Invalid interval drill review");
            attempts = List.copyOf(attempts);
        }
    }

    private final Question question;
    private final List<SixMaxSuppliedIntervalFeedback.Move> moves;

    public SixMaxSuppliedIntervalTrainer(SixMaxSuppliedIntervalFeedback.Result owned) {
        Objects.requireNonNull(owned);
        var capability =
                owned.qualifiedFeedback()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Owned qualified interval feedback required"));
        var context = owned.questionContext();
        var request = capability.request();
        var binding = owned.report().binding();
        moves = List.copyOf(capability.moves());
        question =
                new Question(
                        capability.reportHash(),
                        STATUS,
                        false,
                        SELECTION,
                        binding.model(),
                        binding.beliefs(),
                        binding.sourceHistoryReachStatus(),
                        request.informationSet(),
                        context.hero(),
                        context.heroCards(),
                        context.history(),
                        context.table(),
                        context.legalActions(),
                        request.maximumLossBb(),
                        request.securitySlack(),
                        request.minimumReach());
    }

    public Question question() {
        return question;
    }

    public Feedback grade(Question candidate, String action) {
        if (!question.equals(candidate))
            throw new IllegalArgumentException("Question does not belong to this qualified report");
        var selected =
                moves.stream()
                        .filter(m -> m.action().equals(action))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Illegal question action"));
        return new Feedback(
                question.reportHash(),
                STATUS,
                false,
                SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                question.maximumLossBb(),
                selected,
                moves);
    }

    public Review review(Question candidate, List<String> actions) {
        if (!question.equals(candidate))
            throw new IllegalArgumentException("Question does not belong to this qualified report");
        if (actions == null
                || actions.size() > MAX_ATTEMPTS
                || actions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Between zero and 100 explicit actions required");
        var attempts = new ArrayList<Attempt>();
        int within = 0, outside = 0, dependent = 0;
        for (String action : actions) {
            var feedback = grade(candidate, action);
            attempts.add(new Attempt(attempts.size(), action, feedback));
            switch (feedback.selected().classification()) {
                case WITHIN_LIMIT -> within++;
                case OUTSIDE_LIMIT -> outside++;
                case STRATEGY_DEPENDENT -> dependent++;
            }
        }
        return new Review(SCHEMA, question, attempts, new Counts(within, outside, dependent));
    }

    /** A caller-created review or report hash cannot authorize feedback or alter saved grades. */
    public Review replay(Review saved) {
        if (saved == null) throw new IllegalArgumentException("Saved review required");
        var expected =
                review(saved.question(), saved.attempts().stream().map(Attempt::action).toList());
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Exact interval drill replay differs");
        return expected;
    }

    public Review replay(Path path) throws Exception {
        return replay(read(path));
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    /** Validate against this owned qualification before creating a new saved artifact. */
    public void write(Path path, Review review) throws Exception {
        replay(review);
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Interval drill exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed interval drill exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
