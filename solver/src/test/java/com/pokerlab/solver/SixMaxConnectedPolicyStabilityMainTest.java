package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxConnectedPolicyStabilityMainTest {
    @Test
    void sourceBoundComparisonRoundTripsAndPreservesBothCheckpointFiles(@TempDir Path temp)
            throws Exception {
        var source = SixMaxConnectedPolicyCheckpointTest.source();
        var sourcePath = temp.resolve("source.json");
        Files.writeString(sourcePath, MultiwayPackJson.writeFullRound(source));
        var snapshot = SixMaxConnectedPolicyCheckpointTest.snapshot(source);
        var first = temp.resolve("first.json");
        var second = temp.resolve("second.json");
        SixMaxConnectedPolicyCheckpoint.write(first, snapshot, source);
        Files.copy(first, second);
        var sourceBytes = Files.readAllBytes(sourcePath);
        var firstBytes = Files.readAllBytes(first);
        var secondBytes = Files.readAllBytes(second);
        var output = temp.resolve("reports/comparison.json");
        var args =
                new String[] {
                    sourcePath.toString(), first.toString(), second.toString(), output.toString()
                };
        SixMaxConnectedPolicyStabilityMain.main(args);
        var bytes = Files.readAllBytes(output);
        var artifact =
                new ObjectMapper()
                        .readValue(bytes, SixMaxConnectedPolicyStabilityMain.Artifact.class);
        assertEquals("six-max-connected-policy-stability/v1", artifact.schemaVersion());
        assertEquals(snapshot.sourcePackHash(), artifact.sourcePackHash());
        assertEquals(snapshot.selections(), artifact.selections());
        assertEquals(snapshot.solutionHash(), artifact.comparison().firstSolutionHash());
        assertEquals(artifact.firstQuality(), artifact.secondQuality());
        assertEquals(artifact.firstContent(), artifact.secondContent());
        for (var stage : artifact.comparison().stages())
            assertEquals(0, stage.maximumTotalVariation());
        SixMaxConnectedPolicyStabilityMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertArrayEquals(sourceBytes, Files.readAllBytes(sourcePath));
        assertArrayEquals(firstBytes, Files.readAllBytes(first));
        assertArrayEquals(secondBytes, Files.readAllBytes(second));
        for (var input : List.of(sourcePath, first, second)) {
            var alias = temp.resolve("alias-" + input.getFileName());
            Files.createLink(alias, input);
            var invalid =
                    new String[] {
                        sourcePath.toString(), first.toString(), second.toString(), alias.toString()
                    };
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxConnectedPolicyStabilityMain.main(invalid));
        }
        try (var files = Files.list(output.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void mismatchedMenuOrSourceAndSizeCapsDoNotReplaceExistingReport(@TempDir Path temp)
            throws Exception {
        var source = SixMaxConnectedPolicyCheckpointTest.source();
        var sourcePath = temp.resolve("source.json");
        Files.writeString(sourcePath, MultiwayPackJson.writeFullRound(source));
        var first = temp.resolve("first.json");
        var second = temp.resolve("second.json");
        SixMaxConnectedPolicyCheckpoint.write(
                first, SixMaxConnectedPolicyCheckpointTest.snapshot(source), source);
        var plan =
                SixMaxReachedContinuationStudy.select(
                        source.rebuildGame(),
                        source.solution(),
                        1,
                        1,
                        712,
                        SixMaxContinuationStudyBudget.widerFlops());
        var policy = SixMaxConnectedPreflopAudit.liftCheckdown(plan.game(), source.solution());
        SixMaxConnectedPolicyCheckpoint.write(
                second,
                SixMaxConnectedPolicyCheckpoint.capture(
                        source, plan.game(), policy, SixMaxContinuationStudyBudget.widerFlops()),
                source);
        var output = temp.resolve("comparison.json");
        Files.writeString(output, "keep report");
        var args =
                new String[] {
                    sourcePath.toString(), first.toString(), second.toString(), output.toString()
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyStabilityMain.main(args));
        var foreign = args.clone();
        foreign[0] = "../docs/data/sixmax-correlated-source-pack.json";
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyStabilityMain.main(foreign));
        try (var file = new java.io.RandomAccessFile(second.toFile(), "rw")) {
            file.setLength(SixMaxConnectedPolicyCheckpoint.MAX_BYTES + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyStabilityMain.main(args));
        try (var file = new java.io.RandomAccessFile(sourcePath.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyStabilityMain.main(args));
        assertEquals("keep report", Files.readString(output));
    }
}
