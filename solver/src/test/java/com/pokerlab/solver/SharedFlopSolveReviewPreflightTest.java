package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedFlopSolveReviewPreflightTest {
    @TempDir Path directory;
    static final Path REQUEST = Path.of("../docs/data/supplied-unpaired-shared-flop-request.json");

    @Test
    void existingOutputsAreNeverOverwrittenEvenForAnInvalidRequest() throws Exception {
        for (String output : List.of("report.json", "review.json")) {
            Path report = directory.resolve("report.json"),
                    review = directory.resolve("review.json");
            Path existing = directory.resolve(output);
            Files.writeString(existing, "keep");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSharedFlopFeedbackMain.solveReview(
                                    directory.resolve("missing-request"),
                                    report,
                                    review,
                                    List.of()));
            assertEquals("keep", Files.readString(existing));
            Files.delete(existing);
        }
    }

    @Test
    void invalidChoicesAbortBeforeOptimizationOrOutputCreation() {
        var invalid = new ArrayList<List<SixMaxSharedFlopTrainer.Choice>>();
        invalid.add(null);
        invalid.add(Arrays.asList((SixMaxSharedFlopTrainer.Choice) null));
        invalid.add(List.of(new SixMaxSharedFlopTrainer.Choice(-1, "fold")));
        invalid.add(List.of(new SixMaxSharedFlopTrainer.Choice(4, "call")));
        invalid.add(List.of(new SixMaxSharedFlopTrainer.Choice(0, "check")));
        invalid.add(List.of(new SixMaxSharedFlopTrainer.Choice(0, null)));
        invalid.add(Collections.nCopies(101, new SixMaxSharedFlopTrainer.Choice(0, "fold")));
        for (var choices : invalid) {
            Path report = directory.resolve("report.json.gz"),
                    review = directory.resolve("review.json");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSharedFlopFeedbackMain.solveReview(
                                    REQUEST, report, review, choices));
            assertFalse(Files.exists(report));
            assertFalse(Files.exists(review));
        }
    }

    @Test
    void inputAndOutputsMustBeDistinctBeforeAnySolve() {
        Path review = directory.resolve("review.json");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSharedFlopFeedbackMain.solveReview(
                                REQUEST, REQUEST, review, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopFeedbackMain.solveReview(REQUEST, review, review, List.of()));
    }

    @Test
    void malformedCommandChoicesAbortWithoutReadingMissingInputs() {
        for (String choice :
                List.of("-1:fold", "01:call", "1.5:fold", "0:check", "0:call:extra", "x:call"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSharedFlopFeedbackMain.main(
                                    new String[] {
                                        "solve-review", "missing", "report", "review", choice
                                    }));
    }
}
