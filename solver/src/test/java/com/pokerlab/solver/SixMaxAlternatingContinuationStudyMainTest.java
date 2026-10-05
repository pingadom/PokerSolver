package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxAlternatingContinuationStudyMainTest {
    static String[] arguments(Path source, Path report, Path checkpoint) {
        return new String[] {
            source.toString(),
            report.toString(),
            checkpoint.toString(),
            "711",
            "1",
            "1",
            "1",
            "1",
            "2",
            "1",
            "711",
            "1",
            "1000000",
            "1000000"
        };
    }

    static SixMaxAlternatingContinuationStudyMain.Artifact read(Path path) throws Exception {
        return new ObjectMapper()
                .readValue(path.toFile(), SixMaxAlternatingContinuationStudyMain.Artifact.class);
    }

    @Test
    void persistsAcceptedPolicyAndResumesWithoutTrainingOrReplacingRejectedPolicy(
            @TempDir Path temp) throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var sourceBytes = Files.readAllBytes(source);
        var report = temp.resolve("fresh.json");
        var checkpoint = temp.resolve("policy.json");
        var args = arguments(source, report, checkpoint);
        args[6] = "40";
        args[7] = "10";
        args[8] = "1";
        args[13] = "0.000001";
        SixMaxAlternatingContinuationStudyMain.main(args);
        var fresh = read(report);
        assertEquals("FRESH", fresh.executionMode());
        assertEquals(
                new SixMaxAlternatingContinuationStudyMain.FreshTrainingSettings(711, 1, 1),
                fresh.freshTrainingSettings());
        assertNotNull(fresh.initialTraining());
        assertNull(fresh.resumedSolutionHash());
        assertEquals(1, fresh.rounds().acceptedRounds());
        assertEquals("six-max-alternating-continuation-study/v2", fresh.schemaVersion());
        assertNotNull(fresh.sourcePrivateSupport());
        assertEquals(
                fresh.rounds().retainedAudit().solutionHash(),
                fresh.retainedPrivateSupport().solutionHash());
        assertEquals(1, fresh.rounds().rounds().size());
        assertNotEquals(
                fresh.rounds().initialAudit().solutionHash(),
                fresh.rounds().retainedAudit().solutionHash());
        var checkpointBytes = Files.readAllBytes(checkpoint);
        var loaded =
                SixMaxConnectedPolicyCheckpoint.read(
                        checkpoint, SixMaxConnectedPolicyCheckpointTest.source());
        assertEquals(
                fresh.rounds().retainedAudit().solutionHash(), loaded.snapshot().solutionHash());
        var resumed = Arrays.copyOf(args, 16);
        resumed[1] = temp.resolve("resumed.json").toString();
        resumed[14] = "--resume";
        resumed[15] = checkpoint.toString();
        // These fresh-training budgets are unused in resume mode and must not appear as executed
        // work.
        resumed[4] = "3000";
        resumed[5] = "500";
        resumed[13] = "1000000";
        SixMaxAlternatingContinuationStudyMain.main(resumed);
        var result = read(Path.of(resumed[1]));
        assertEquals("RESUMED", result.executionMode());
        assertNull(result.initialTraining());
        assertNull(result.freshTrainingSettings());
        assertEquals(loaded.snapshot().solutionHash(), result.resumedSolutionHash());
        assertEquals(fresh.rounds().retainedAudit(), result.rounds().initialAudit());
        assertEquals(0, result.rounds().acceptedRounds());
        assertEquals(fresh.rounds().retainedAudit(), result.rounds().retainedAudit());
        assertArrayEquals(checkpointBytes, Files.readAllBytes(checkpoint));
        assertEquals(fresh.retainedPrivateSupport(), result.retainedPrivateSupport());
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        resumed[10] = "712";
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxAlternatingContinuationStudyMain.main(resumed));
        assertArrayEquals(checkpointBytes, Files.readAllBytes(checkpoint));
    }

    @Test
    void planOnlyPreflightsWiderGameWithoutCreatingCheckpoint(@TempDir Path temp) throws Exception {
        var report = temp.resolve("plan.json");
        var checkpoint = temp.resolve("absent.json");
        var args =
                Arrays.copyOf(
                        arguments(
                                Path.of("../docs/data/sixmax-diverse-source-pack.json"),
                                report,
                                checkpoint),
                        15);
        args[9] = "2";
        args[11] = "2";
        args[12] = "0.05";
        args[13] = "0.000001";
        args[14] = "--plan-only";
        SixMaxAlternatingContinuationStudyMain.main(args);
        var artifact = read(report);
        assertEquals("PLANNED", artifact.executionStatus());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(12, artifact.cost().compatibleDealFlops());
        assertEquals(1_358_137, artifact.cost().completeTreeStates());
        assertNull(artifact.rounds());
        assertNull(artifact.initialTraining());
        assertEquals(4, artifact.sourcePrivateSupport().sourceJointDeals());
        assertEquals(4, artifact.sourcePrivateSupport().boards().size());
        assertNull(artifact.retainedPrivateSupport());
        assertEquals(
                new SixMaxAlternatingContinuationStudyMain.FreshTrainingSettings(711, 1, 1),
                artifact.freshTrainingSettings());
        assertFalse(Files.exists(checkpoint));
    }

    @Test
    void rejectsAliasesBadBudgetsAndUnqualifiedBaselineBeforeMutation(@TempDir Path temp)
            throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var sourceBytes = Files.readAllBytes(source);
        var report = temp.resolve("report.json");
        var checkpoint = temp.resolve("checkpoint.json");
        Files.writeString(report, "keep report");
        Files.writeString(checkpoint, "keep checkpoint");
        var args = arguments(source, report, checkpoint);
        for (var pair :
                List.of(
                        new String[] {"1", source.toString()},
                        new String[] {"2", source.toString()},
                        new String[] {"2", report.toString()},
                        new String[] {"8", "4"},
                        new String[] {"6", "0"},
                        new String[] {"7", "501"},
                        new String[] {"12", "NaN"},
                        new String[] {"13", "0"},
                        new String[] {"12", "0.000000000001"})) {
            var invalid = args.clone();
            invalid[Integer.parseInt(pair[0])] = pair[1];
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxAlternatingContinuationStudyMain.main(invalid));
            assertEquals("keep report", Files.readString(report));
            assertEquals("keep checkpoint", Files.readString(checkpoint));
        }
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
    }
}
