package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxContinuationFeedbackStudyMainTest {
    @Test
    void exportsMatchedControlAndChangedReachDeterministicallyWithOptionalFinalRefinement(
            @TempDir Path temp) throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var bytes = Files.readAllBytes(source);
        var output = temp.resolve("feedback.json");
        String[] args = {
            source.toString(),
            output.toString(),
            "711",
            "1",
            "1",
            "1",
            "0",
            "1",
            "711",
            "1",
            "0.00000001"
        };
        SixMaxContinuationFeedbackStudyMain.main(args);
        var artifact =
                new ObjectMapper()
                        .readValue(
                                output.toFile(),
                                SixMaxContinuationFeedbackStudyMain.Artifact.class);
        var run = artifact.runs().getFirst();
        assertEquals("six-max-continuation-feedback-study/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals("COMPLETED", artifact.executionStatus());
        var pack = MultiwayPackJson.readFullRound(Files.readString(source));
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
        assertEquals(pack.spotHash(), artifact.sourceSpotHash());
        assertEquals(
                run.initialPostflopRefinement().originalSolutionHash(),
                run.unrefinedContinuationControl().originalSolutionHash());
        assertEquals(
                run.initialPostflopRefinement().candidateSolutionHash(),
                run.preflopFeedback().originalSolutionHash());
        assertEquals(run.preflopFeedback().candidateSolutionHash(), run.finalSolutionHash());
        assertEquals(
                run.unrefinedContinuationControl().preflopIterations(),
                run.preflopFeedback().preflopIterations());
        assertEquals(
                run.initialPostflopRefinement().candidateQuality(),
                run.preflopFeedback().originalParentQuality());
        assertEquals(
                run.preflopFeedback().originalReach(),
                run.unrefinedContinuationControl().originalReach());
        assertEquals(
                run.preflopFeedback().replacedPreflopInformationSets()
                        + run.preflopFeedback().preservedPostflopInformationSets(),
                run.visitedInformationSets() + run.uniformlyCompletedInformationSets());
        assertNull(run.finalPostflopRefinement());
        assertFalse(run.finalConditionalGapWithinTarget());
        assertEquals(
                run.finalParentNashConvChangeFromJointBb() <= artifact.parentNashConvToleranceBb(),
                run.finalParentNashConvDidNotIncreaseFromJoint());
        args[1] = temp.resolve("repeat.json").toString();
        SixMaxContinuationFeedbackStudyMain.main(args);
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(Path.of(args[1])));
        args[1] = temp.resolve("final.json").toString();
        args[6] = "1";
        SixMaxContinuationFeedbackStudyMain.main(args);
        var finalRun =
                new ObjectMapper()
                        .readValue(
                                Path.of(args[1]).toFile(),
                                SixMaxContinuationFeedbackStudyMain.Artifact.class)
                        .runs()
                        .getFirst();
        assertNotNull(finalRun.finalPostflopRefinement());
        assertEquals(
                finalRun.preflopFeedback().candidateSolutionHash(),
                finalRun.finalPostflopRefinement().originalSolutionHash());
        assertEquals(
                finalRun.finalSolutionHash(),
                finalRun.finalPostflopRefinement().candidateSolutionHash());
        assertEquals(
                finalRun.preflopFeedback().candidateParentQuality(),
                finalRun.finalPostflopRefinement().originalQuality());
        assertEquals(
                finalRun.preflopFeedback().candidateReach().selectedPhysicalFlopProbability(),
                finalRun.finalPostflopRefinement().bettingContinuationProbability(),
                1e-15);
        assertArrayEquals(bytes, Files.readAllBytes(source));
    }

    @Test
    void preflightsTheWiderDeclaredGameWithoutTraining(@TempDir Path temp) throws Exception {
        var source = Path.of("../docs/data/sixmax-diverse-source-pack.json");
        var output = temp.resolve("plan.json");
        SixMaxContinuationFeedbackStudyMain.main(
                new String[] {
                    source.toString(),
                    output.toString(),
                    "711,712",
                    "500",
                    "300",
                    "500",
                    "300",
                    "2",
                    "711",
                    "2",
                    "0.05",
                    "--plan-only"
                });
        var artifact =
                new ObjectMapper()
                        .readValue(
                                output.toFile(),
                                SixMaxContinuationFeedbackStudyMain.Artifact.class);
        assertEquals("PLANNED", artifact.executionStatus());
        assertTrue(artifact.runs().isEmpty());
        assertEquals(List.of(711L, 712L), artifact.trainingSeeds());
        assertEquals(12, artifact.cost().compatibleDealFlops());
        assertEquals(1_358_137, artifact.cost().completeTreeStates());
        assertEquals(2, artifact.flopsPerHistory());
        assertEquals(300, artifact.finalPostflopIterations());
    }

    @Test
    void rejectsInvalidBudgetsAndSourceOverwriteWithoutChangingFiles(@TempDir Path temp)
            throws Exception {
        var source = Path.of("../docs/data/sixmax-diverse-source-pack.json");
        var bytes = Files.readAllBytes(source);
        var output = temp.resolve("preserve.json");
        Files.writeString(output, "keep me");
        String[] valid = {
            source.toString(), output.toString(), "711", "1", "1", "1", "0", "2", "711", "2", "0.05"
        };
        for (var pair :
                List.of(
                        new String[] {"2", "711,711"},
                        new String[] {"3", "3001"},
                        new String[] {"4", "0"},
                        new String[] {"5", "0"},
                        new String[] {"6", "-1"},
                        new String[] {"6", "501"},
                        new String[] {"9", "3"},
                        new String[] {"10", "NaN"})) {
            var args = valid.clone();
            args[Integer.parseInt(pair[0])] = pair[1];
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxContinuationFeedbackStudyMain.main(args));
            assertEquals("keep me", Files.readString(output));
        }
        valid[1] = source.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxContinuationFeedbackStudyMain.main(valid));
        assertArrayEquals(bytes, Files.readAllBytes(source));
    }
}
