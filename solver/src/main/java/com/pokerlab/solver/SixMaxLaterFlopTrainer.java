package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One selected offline drill. Question, grading and replay use only cached owned evidence. */
public final class SixMaxLaterFlopTrainer {
    public static final String SCHEMA = "pokerlab-later-bb-flop-drill/v1";
    public static final String STATUS = "LATER_BB_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
    public static final String SELECTION = "SINGLE_QUALIFIED_LATER_BB_FLOP_QUESTION/v1";
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 256 * 1024;

    public record Question(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String informationSet,
            String hero,
            String heroCards,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context,
            String model,
            String chanceModel,
            String beliefs,
            String sourceHistoryReachStatus,
            double maximumLossBb,
            double securitySlackBb,
            double minimumReach,
            String reachUnits,
            String evScope) {}

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
                throw new IllegalArgumentException("Invalid later BB flop drill review");
            attempts = List.copyOf(attempts);
        }
    }

    private final Question question;
    private final List<SixMaxSuppliedIntervalFeedback.Move> moves;

    public SixMaxLaterFlopTrainer(SixMaxLaterFlopFeedback.Result owned) {
        Objects.requireNonNull(owned);
        var capability =
                owned.qualifiedFeedback()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Owned qualified flop feedback required"));
        var report = owned.report();
        moves = List.copyOf(report.moves());
        question =
                new Question(
                        capability.reportHash(),
                        STATUS,
                        false,
                        SELECTION,
                        report.informationSet(),
                        report.context().actingSeat().name(),
                        report.request().heroCards(),
                        report.context(),
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        report.request().maximumLossBb(),
                        SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                        SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR,
                        report.reachUnits(),
                        report.evScope());
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
                SixMaxSuppliedFlopFeedback.EV_SCOPE,
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
            throw new IllegalArgumentException("Exact later BB flop drill replay differs");
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
            throw new IllegalArgumentException("Later BB flop drill exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed later BB flop drill exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
