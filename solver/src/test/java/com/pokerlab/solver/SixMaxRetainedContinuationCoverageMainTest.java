package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxRetainedContinuationCoverageMainTest {
    private static Path checkpoint(Path temp) throws Exception {
        var source = SixMaxConnectedPolicyCheckpointTest.source();
        var path = temp.resolve("checkpoint.json");
        SixMaxConnectedPolicyCheckpoint.write(
                path, SixMaxConnectedPolicyCheckpointTest.snapshot(source), source);
        return path;
    }

    @Test
    void boundReportRoundTripsDeterministicallyWithoutMutatingInputs(@TempDir Path temp)
            throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var checkpoint = checkpoint(temp);
        var sourceBytes = Files.readAllBytes(source);
        var checkpointBytes = Files.readAllBytes(checkpoint);
        var output = temp.resolve("reports/coverage.json");
        var args =
                new String[] {
                    source.toString(),
                    checkpoint.toString(),
                    output.toString(),
                    "--search",
                    "711",
                    "2",
                    "1",
                    "1"
                };
        SixMaxRetainedContinuationCoverageMain.main(args);
        var bytes = Files.readAllBytes(output);
        var artifact =
                new ObjectMapper()
                        .readValue(
                                output.toFile(),
                                SixMaxRetainedContinuationCoverageMain.Artifact.class);
        assertEquals("six-max-retained-continuation-coverage/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        var loaded =
                SixMaxConnectedPolicyCheckpoint.read(
                        checkpoint, SixMaxConnectedPolicyCheckpointTest.source());
        assertEquals(loaded.snapshot().solutionHash(), artifact.checkpointSolutionHash());
        assertEquals(loaded.snapshot().sourcePackHash(), artifact.sourcePackHash());
        assertEquals(loaded.snapshot().sourceSpotHash(), artifact.sourceSpotHash());
        assertEquals(loaded.snapshot().selections(), artifact.checkpointSelections());
        assertEquals(artifact.checkpointSolutionHash(), artifact.retainedCoverage().solutionHash());
        assertEquals(
                SixMaxRetainedContinuationCoverage.Settings.researchDefault(),
                artifact.retainedCoverage().settings());
        assertFalse(artifact.retainedCoverage().criteriaMet());
        assertNotNull(artifact.sourceMenuSearch());
        assertNotNull(artifact.retainedMenuSearch());
        SixMaxRetainedContinuationCoverageMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        assertArrayEquals(checkpointBytes, Files.readAllBytes(checkpoint));
        try (var paths = Files.list(output.getParent())) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void invalidOptionsAndInputAliasesPreserveBothInputsAndExistingReport(@TempDir Path temp)
            throws Exception {
        var source = temp.resolve("source.json");
        Files.copy(Path.of("src/test/resources/six-seat-full-round-pack.json"), source);
        var checkpoint = checkpoint(temp);
        var sourceBytes = Files.readAllBytes(source);
        var checkpointBytes = Files.readAllBytes(checkpoint);
        var output = temp.resolve("report.json");
        Files.writeString(output, "keep report");
        for (var input : java.util.List.of(source, checkpoint)) {
            var alias = temp.resolve("link-" + input.getFileName());
            Files.createLink(alias, input);
            for (var target :
                    java.util.List.of(
                            input, alias, temp.resolve("nested/../" + input.getFileName())))
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxRetainedContinuationCoverageMain.main(
                                        new String[] {
                                            source.toString(),
                                            checkpoint.toString(),
                                            target.toString()
                                        }));
        }
        for (var options :
                new String[][] {
                    {"--unknown", "1", "1", "1", "1"},
                    {"--search", "711", "2"},
                    {"--search", "711", "17", "2", "1"},
                    {"--thresholds", "NaN", ".25", ".05", "2"},
                    {
                        "--thresholds",
                        ".0001",
                        ".25",
                        ".05",
                        "2",
                        "--thresholds",
                        ".0001",
                        ".25",
                        ".05",
                        "2"
                    },
                    {"--search", "711", "2", "1", "1", "--search", "711", "2", "1", "1"}
                }) {
            var args =
                    new java.util.ArrayList<>(
                            java.util.List.of(
                                    source.toString(), checkpoint.toString(), output.toString()));
            args.addAll(java.util.List.of(options));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxRetainedContinuationCoverageMain.main(args.toArray(String[]::new)));
            assertEquals("keep report", Files.readString(output));
        }
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        assertArrayEquals(checkpointBytes, Files.readAllBytes(checkpoint));
        Files.writeString(checkpoint, "invalid checkpoint");
        assertThrows(
                Exception.class,
                () ->
                        SixMaxRetainedContinuationCoverageMain.main(
                                new String[] {
                                    source.toString(), checkpoint.toString(), output.toString()
                                }));
        assertEquals("keep report", Files.readString(output));
    }

    @Test
    void sourceBindingAndSizeCapsFailBeforeReplacingOutput(@TempDir Path temp) throws Exception {
        var checkpoint = checkpoint(temp);
        var output = temp.resolve("report.json");
        Files.writeString(output, "keep report");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRetainedContinuationCoverageMain.main(
                                new String[] {
                                    "../docs/data/sixmax-eight-deal-source-pack.json",
                                    checkpoint.toString(),
                                    output.toString()
                                }));
        var source = temp.resolve("oversize-source.json");
        try (var file = new java.io.RandomAccessFile(source.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRetainedContinuationCoverageMain.main(
                                new String[] {
                                    source.toString(), checkpoint.toString(), output.toString()
                                }));
        try (var file = new java.io.RandomAccessFile(checkpoint.toFile(), "rw")) {
            file.setLength(SixMaxConnectedPolicyCheckpoint.MAX_BYTES + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRetainedContinuationCoverageMain.main(
                                new String[] {
                                    "src/test/resources/six-seat-full-round-pack.json",
                                    checkpoint.toString(),
                                    output.toString()
                                }));
        assertEquals("keep report", Files.readString(output));
    }
}
