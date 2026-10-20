package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Four cached BTN decisions. Only an owned complete qualification can authorize grades. */
public final class SixMaxPairedFlopTrainer {
    public static final String SCHEMA = "pokerlab-paired-four-hand-flop-drill/v1";
    public static final String STATUS = "FOUR_PAIRED_BTN_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
    public static final String SELECTION = SixMaxPairedFlopCurriculum.SELECTION;
    public static final int QUESTION_COUNT = 4;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_BYTES = 256 * 1024;

    public record Question(
            String reportHash,
            int questionIndex,
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
            int questionIndex,
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

    public record Choice(int questionIndex, String action) {}

    public record Attempt(int index, int questionIndex, String action, Feedback feedback) {}

    /** Distinct questions and repeated decisions are separate; neither measures poker accuracy. */
    public record Counts(
            int distinctQuestionsAttempted,
            int withinLimit,
            int outsideLimit,
            int strategyDependent) {}

    public record Review(
            String schemaVersion,
            String reportHash,
            String selection,
            List<Question> questions,
            List<Attempt> attempts,
            Counts counts) {
        public Review {
            if (!SCHEMA.equals(schemaVersion)
                    || reportHash == null
                    || !SELECTION.equals(selection)
                    || questions == null
                    || questions.size() != QUESTION_COUNT
                    || attempts == null
                    || attempts.size() > MAX_ATTEMPTS
                    || counts == null)
                throw new IllegalArgumentException("Invalid paired BTN flop drill review");
            questions = List.copyOf(questions);
            attempts = List.copyOf(attempts);
        }
    }

    private final String reportHash;
    private final List<Question> questions;
    private final List<List<SixMaxSuppliedIntervalFeedback.Move>> moves;

    public SixMaxPairedFlopTrainer(SixMaxPairedFlopCurriculum.Result owned) {
        Objects.requireNonNull(owned);
        var capability =
                owned.qualifiedFeedback()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Owned qualified four-hand BTN feedback required"));
        reportHash = capability.reportHash();
        var report = owned.report();
        var inventory = new ArrayList<Question>();
        for (var decision : report.decisions()) {
            inventory.add(
                    new Question(
                            reportHash,
                            inventory.size(),
                            STATUS,
                            false,
                            SELECTION,
                            decision.conditional().informationSet(),
                            decision.conditional().context().actingSeat().name(),
                            decision.conditional().request().heroCards(),
                            decision.conditional().context(),
                            SixMaxSuppliedFlopBettingGame.MODEL,
                            SixMaxSuppliedFlopBettingGame.CHANCE,
                            SixMaxSuppliedRangePreflopGame.BELIEFS,
                            SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                            report.request().maximumLossBb(),
                            SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                            SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR,
                            report.decisions().getFirst().conditional().reachUnits(),
                            report.decisions().getFirst().conditional().evScope()));
        }
        if (inventory.size() != QUESTION_COUNT
                || !inventory.stream()
                        .map(Question::heroCards)
                        .toList()
                        .equals(SixMaxPairedFlopCurriculum.HANDS))
            throw new IllegalArgumentException("Complete ordered BTN inventory required");
        questions = List.copyOf(inventory);
        moves = report.decisions().stream().map(d -> List.copyOf(d.moves())).toList();
    }

    public String reportHash() {
        return reportHash;
    }

    public List<Question> questions() {
        return questions;
    }

    public Question question(int index) {
        if (index < 0 || index >= questions.size())
            throw new IllegalArgumentException("Question index outside BTN inventory");
        return questions.get(index);
    }

    public Feedback grade(Question candidate, String action) {
        if (candidate == null || !question(candidate.questionIndex()).equals(candidate))
            throw new IllegalArgumentException("Question does not belong to this qualified report");
        var alternatives = moves.get(candidate.questionIndex());
        var selected =
                alternatives.stream()
                        .filter(m -> m.action().equals(action))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Illegal question action"));
        return new Feedback(
                reportHash,
                candidate.questionIndex(),
                STATUS,
                false,
                candidate.evScope(),
                candidate.maximumLossBb(),
                selected,
                alternatives);
    }

    public Review review(List<Choice> choices) {
        if (choices == null
                || choices.size() > MAX_ATTEMPTS
                || choices.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Between zero and 100 explicit choices required");
        var attempts = new ArrayList<Attempt>();
        var seen = new HashSet<Integer>();
        int within = 0, outside = 0, dependent = 0;
        for (var choice : choices) {
            var feedback = grade(question(choice.questionIndex()), choice.action());
            attempts.add(
                    new Attempt(
                            attempts.size(), choice.questionIndex(), choice.action(), feedback));
            seen.add(choice.questionIndex());
            switch (feedback.selected().classification()) {
                case WITHIN_LIMIT -> within++;
                case OUTSIDE_LIMIT -> outside++;
                case STRATEGY_DEPENDENT -> dependent++;
            }
        }
        return new Review(
                SCHEMA,
                reportHash,
                SELECTION,
                questions,
                attempts,
                new Counts(seen.size(), within, outside, dependent));
    }

    /** Full comparison binds inventory, provenance, ordering, alternatives and every count. */
    public Review replay(Review saved) {
        if (saved == null) throw new IllegalArgumentException("Saved review required");
        var expected =
                review(
                        saved.attempts().stream()
                                .map(a -> new Choice(a.questionIndex(), a.action()))
                                .toList());
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Exact paired BTN flop drill replay differs");
        return expected;
    }

    public Review replay(Path path) throws Exception {
        return replay(read(path));
    }

    public static Review read(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Review.class);
    }

    /** Canonical offline document from cached choices; never serialize client-supplied grades. */
    public String reviewJson(List<Choice> choices) throws Exception {
        return new String(document(review(choices)), StandardCharsets.UTF_8);
    }

    private static byte[] document(Review review) throws Exception {
        byte[] bytes = SixMaxTextureStudy.json(review).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded paired BTN drill exceeds byte cap");
        return bytes;
    }

    public void write(Path path, Review review) throws Exception {
        replay(review);
        path = path.toAbsolutePath().normalize();
        byte[] bytes = document(review);
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored paired BTN drill exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }
}
