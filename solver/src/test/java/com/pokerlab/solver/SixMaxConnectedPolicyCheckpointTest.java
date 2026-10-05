package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxConnectedPolicyCheckpointTest {
    static SixMaxPreflopSolutionPack source() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("src/test/resources/six-seat-full-round-pack.json")));
    }

    static SixMaxConnectedPolicyCheckpoint.Snapshot snapshot(SixMaxPreflopSolutionPack source) {
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var game =
                SixMaxReachedContinuationStudy.select(
                                source.rebuildGame(), source.solution(), 1, 1, 711, budget)
                        .game();
        var policy = SixMaxConnectedPreflopAudit.liftCheckdown(game, source.solution());
        return SixMaxConnectedPolicyCheckpoint.capture(source, game, policy, budget);
    }

    @Test
    void roundTripsExplicitPolicyAndDeclaredGameDeterministically(@TempDir Path temp)
            throws Exception {
        var source = source();
        var snapshot = snapshot(source);
        var file = temp.resolve("policy.json");
        SixMaxConnectedPolicyCheckpoint.write(file, snapshot, source);
        var bytes = Files.readAllBytes(file);
        var loaded = SixMaxConnectedPolicyCheckpoint.read(file, source);
        assertEquals(snapshot, loaded.snapshot());
        assertEquals(snapshot.selections(), loaded.game().selections());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                loaded.game(), loaded.snapshot().policy(), 2_000_000)
                        .addedInformationSets());
        SixMaxConnectedPolicyCheckpoint.write(file, loaded.snapshot(), source);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        try (var paths = Files.list(temp)) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void rejectsHashProvenanceMissingRowsAndStrictJson(@TempDir Path temp) throws Exception {
        var source = source();
        var file = temp.resolve("policy.json");
        SixMaxConnectedPolicyCheckpoint.write(file, snapshot(source), source);
        var valid = Files.readString(file);
        var mapper = new ObjectMapper();
        var nullIterations = (ObjectNode) mapper.readTree(valid);
        ((ObjectNode) nullIterations.path("policy")).putNull("iterations");
        for (String field : new String[] {"solutionHash", "sourcePackHash", "sourceSpotHash"}) {
            var node = (ObjectNode) mapper.readTree(valid);
            node.put(field, "0".repeat(64));
            Files.writeString(file, node.toString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxConnectedPolicyCheckpoint.read(file, source));
        }
        var missing = (ObjectNode) mapper.readTree(valid);
        var rows = (ObjectNode) missing.path("policy").path("strategy");
        rows.remove(rows.fieldNames().next());
        Files.writeString(file, missing.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyCheckpoint.read(file, source));
        for (var invalid :
                new String[] {
                    valid + "{}",
                    valid.replaceFirst("\\{", "{\"unexpected\":true,"),
                    valid.replaceFirst("\\{", "{\"schemaVersion\":\"duplicate\","),
                    nullIterations.toString()
                }) {
            Files.writeString(file, invalid);
            assertThrows(Exception.class, () -> SixMaxConnectedPolicyCheckpoint.read(file, source));
        }
    }

    @Test
    void rejectsOversizeCheckpointBeforeParsing(@TempDir Path temp) throws Exception {
        var file = temp.resolve("oversize.json");
        try (var data = new java.io.RandomAccessFile(file.toFile(), "rw")) {
            data.setLength(SixMaxConnectedPolicyCheckpoint.MAX_BYTES + 1);
        }
        var source = source();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyCheckpoint.read(file, source));
    }

    @Test
    void invalidWritePreservesExistingCheckpoint(@TempDir Path temp) throws Exception {
        var source = source();
        var good = snapshot(source);
        var invalid =
                new SixMaxConnectedPolicyCheckpoint.Snapshot(
                        good.schemaVersion(),
                        good.publicationStatus(),
                        good.sourcePackHash(),
                        good.sourceSpotHash(),
                        good.budget(),
                        good.selections(),
                        good.solutionHash(),
                        new CfrSolution(1, Map.of()));
        var file = temp.resolve("keep.json");
        Files.writeString(file, "keep me");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyCheckpoint.write(file, invalid, source));
        assertEquals("keep me", Files.readString(file));
    }
}
