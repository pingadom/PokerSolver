package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxHeadsUpPreflopCliTest {
    @TempDir Path temporary;
    static final String SOURCE = "../docs/data/sixmax-staged-three-nine-source-pack.json";
    static final String POLICY =
            SixMaxHeadsUpPreflopStudyTest.data("three-nine-twentytwo", "policy").toString();
    static final String REPORT =
            SixMaxHeadsUpPreflopStudyTest.data("three-nine-twentytwo", "report").toString();

    @FunctionalInterface
    interface Command {
        void run() throws Exception;
    }

    static String capture(Command command) throws Exception {
        var original = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            command.run();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void playableQuestionGradeSessionAndReviewCommandsUseCompleteReplay() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var question =
                mapper.readValue(
                        capture(
                                () ->
                                        SixMaxHeadsUpPreflopTrainerMain.main(
                                                new String[] {
                                                    "question", SOURCE, POLICY, REPORT, "711"
                                                })),
                        SixMaxHeadsUpPreflopTrainer.Question.class);
        var feedback =
                mapper.readValue(
                        capture(
                                () ->
                                        SixMaxHeadsUpPreflopTrainerMain.main(
                                                new String[] {
                                                    "grade",
                                                    SOURCE,
                                                    POLICY,
                                                    REPORT,
                                                    "711",
                                                    question.legalActions().getFirst()
                                                })),
                        SixMaxHeadsUpPreflopTrainer.Feedback.class);
        assertEquals(question.studyHash(), feedback.studyHash());
        assertTrue(feedback.evLossBb() >= 0);
        assertFalse(feedback.trainerAdmission());
        var questions =
                mapper.readValue(
                        capture(
                                () ->
                                        SixMaxHeadsUpPreflopTrainerMain.main(
                                                new String[] {
                                                    "session", SOURCE, POLICY, REPORT, "711"
                                                })),
                        SixMaxHeadsUpPreflopTrainer.Question[].class);
        assertEquals(10, questions.length);
        var args = new ArrayList<>(List.of("review", SOURCE, POLICY, REPORT, "711"));
        for (var q : questions) args.add(q.legalActions().getFirst());
        var review =
                mapper.readValue(
                        capture(
                                () ->
                                        SixMaxHeadsUpPreflopTrainerMain.main(
                                                args.toArray(String[]::new))),
                        SixMaxHeadsUpPreflopTrainer.Review.class);
        assertEquals(10, review.attempts().size());
        assertEquals(question.studyHash(), review.studyHash());
        assertEquals(
                review.attempts().stream().mapToDouble(a -> a.feedback().evLossBb()).sum(),
                review.totalEvLossBb(),
                1e-12);
        for (int i = 0; i < 10; i++)
            assertEquals(questions[i], review.attempts().get(i).question());
    }

    @Test
    void studyCliSolvesToNewPathsThenExactlyReplaysAndRejectsUnscreenedPractice() throws Exception {
        var policy = temporary.resolve("policy.json.gz");
        var report = temporary.resolve("report.json.gz");
        assertTrue(
                capture(
                                () ->
                                        SixMaxHeadsUpPreflopStudyMain.main(
                                                new String[] {
                                                    "solve",
                                                    SOURCE,
                                                    SixMaxHeadsUpPreflopStudyTest.data(
                                                                    "three-nine", "specification")
                                                            .toString(),
                                                    policy.toString(),
                                                    report.toString()
                                                }))
                        .contains("stable=4/4"));
        assertTrue(
                capture(
                                () ->
                                        SixMaxHeadsUpPreflopStudyMain.main(
                                                new String[] {
                                                    "replay",
                                                    SOURCE,
                                                    policy.toString(),
                                                    report.toString()
                                                }))
                        .contains("REPLAYED"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopTrainerMain.main(
                                new String[] {
                                    "question",
                                    SOURCE,
                                    temporary.resolve("absent.json").toString(),
                                    SixMaxHeadsUpPreflopStudyTest.data("five-target", "report")
                                            .toString(),
                                    "711"
                                }));
    }

    @Test
    void trainerCliRejectsInvalidCommandsSeedsAndAliasesBeforeInputLoading() throws Exception {
        for (var args :
                List.of(
                        new String[] {},
                        new String[] {"grade"},
                        new String[] {"unknown", "a", "b", "c", "1"},
                        new String[] {"review", "a", "b", "c", "1"}))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxHeadsUpPreflopTrainerMain.main(args));
        assertThrows(
                NumberFormatException.class,
                () ->
                        SixMaxHeadsUpPreflopTrainerMain.main(
                                new String[] {
                                    "question", "missing", "other", "report", "invalid"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopTrainerMain.main(
                                new String[] {"question", "missing", "./missing", "report", "1"}));
    }
}
