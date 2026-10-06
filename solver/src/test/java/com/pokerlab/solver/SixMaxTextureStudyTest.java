package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxTextureStudyTest {
    @Test
    void freshJointSolveReplaysIdenticallyWithoutTrainingAndRejectsIncompletePolicies(
            @TempDir Path temp) throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxTextureFlopGameTest.table(source);
        var selections =
                List.of(
                        new SixMaxTextureFlopGame.Selection(
                                SixMaxConnectedPreflopGameTest.HISTORY, .5));
        var result = SixMaxTextureStudy.solve(source, table, selections, 3);
        var report = result.report();
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals("CFR_PLUS", report.algorithm());
        assertEquals("EXHAUSTIVE", report.chanceTraversal());
        assertEquals(source.solution().strategy().size(), report.preflopInformationSets());
        assertEquals(24, report.postflopInformationSets());
        assertTrue(result.traversal().visitedNodes() > 0);
        assertEquals(0, result.traversal().sampledChanceNodes());
        assertEquals(
                0,
                report.jointlySolvedInTextureGame().profileUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-12);
        assertTrue(report.checkdownRecoveryErrorBb().stream().allMatch(v -> Math.abs(v) < 1e-10));
        var checkpoint = temp.resolve("checkpoint.json");
        Files.writeString(checkpoint, SixMaxTextureStudy.json(result.checkpoint()));
        var bytes = Files.readAllBytes(checkpoint);
        var loaded = SixMaxTextureStudy.read(checkpoint, source, table);
        assertEquals(
                SixMaxTextureStudy.json(report),
                SixMaxTextureStudy.json(SixMaxTextureStudy.assess(source, table, loaded)));
        assertArrayEquals(bytes, Files.readAllBytes(checkpoint));
        var gzip = temp.resolve("checkpoint.json.gz");
        SixMaxTextureStudy.write(gzip, loaded, source, table);
        assertEquals(loaded, SixMaxTextureStudy.read(gzip, source, table));
        var compressedBytes = Files.readAllBytes(gzip);
        SixMaxTextureStudy.write(gzip, loaded, source, table);
        assertArrayEquals(compressedBytes, Files.readAllBytes(gzip));
        assertTrue(compressedBytes.length < bytes.length);
        Files.write(gzip, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> SixMaxTextureStudy.read(gzip, source, table));
        try (var zipped = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzip))) {
            zipped.write(new byte[16 * 1024 * 1024 + 1]);
        }
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTextureStudy.read(gzip, source, table));
        var game = SixMaxTextureStudy.rebuild(source, table, loaded);
        var partial = SixMaxTextureStudy.checkpoint(source, table, game, source.solution());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureStudy.rebuild(source, table, partial));
        var tree = SixMaxTexturePayoffTable.mapper().readTree(SixMaxTextureStudy.json(loaded));
        var selected =
                (com.fasterxml.jackson.databind.node.ObjectNode) tree.get("selections").get(0);
        selected.put("potFraction", .25);
        Files.writeString(checkpoint, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureStudy.read(checkpoint, source, table));
        selected.put("potFraction", .5);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                .put("completeTreeStates", loaded.completeTreeStates() - 1);
        Files.writeString(checkpoint, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureStudy.read(checkpoint, source, table));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                .put("completeTreeStates", loaded.completeTreeStates());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("solutionHash", "0".repeat(64));
        Files.writeString(checkpoint, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureStudy.read(checkpoint, source, table));
    }

    @Test
    void strictTableLoaderRejectsWrongSupportCountsMarginalsAndJsonCoercions(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxTextureFlopGameTest.table(source);
        var input = temp.resolve("table.json");
        var json = SixMaxTexturePayoffTable.json(table);
        Files.writeString(input, json);
        assertEquals(table, SixMaxTexturePayoffTable.read(input, source));
        var changed = new ArrayList<>(table.deals());
        java.util.Collections.reverse(changed);
        var reordered =
                new SixMaxTexturePayoffTable.Artifact(
                        table.schemaVersion(),
                        table.publicationStatus(),
                        table.classifier(),
                        table.sourcePackHash(),
                        table.sourceSpotHash(),
                        changed);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTexturePayoffTable.validate(reordered, source));
        for (String corrupt :
                List.of(
                        json + "{}",
                        json.replaceFirst("\"activeMask\" : 3", "\"activeMask\" : 3.5"),
                        json.replaceFirst("\"activeMask\" : 3", "\"activeMask\" : \"3\""),
                        json.replaceFirst("\"activeMask\" : 3", "\"activeMask\" : null"),
                        json.replaceFirst(
                                "\"activeMask\" : 3", "\"activeMask\" : 3, \"activeMask\" : 3"))) {
            assertNotEquals(json, corrupt);
            Files.writeString(input, corrupt);
            assertThrows(Exception.class, () -> SixMaxTexturePayoffTable.read(input, source));
        }
        var tree = SixMaxTexturePayoffTable.mapper().readTree(json);
        var counts =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        tree.get("deals").get(0).get("flopCounts");
        counts.set(
                0,
                SixMaxTexturePayoffTable.mapper()
                        .getNodeFactory()
                        .numberNode(counts.get(0).asLong() + 1));
        Files.writeString(input, tree.toString());
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTexturePayoffTable.read(input, source));
        tree = SixMaxTexturePayoffTable.mapper().readTree(json);
        var wins =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        tree.get("deals").get(0).get("pairs").get(0).get("firstWins");
        wins.set(0, SixMaxTexturePayoffTable.mapper().getNodeFactory().numberNode(Long.MAX_VALUE));
        Files.writeString(input, tree.toString());
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTexturePayoffTable.read(input, source));
        tree = SixMaxTexturePayoffTable.mapper().readTree(json);
        var ties =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        tree.get("deals").get(0).get("pairs").get(0).get("ties");
        ties.set(
                0,
                SixMaxTexturePayoffTable.mapper()
                        .getNodeFactory()
                        .numberNode(ties.get(0).asLong() - 2));
        Files.writeString(input, tree.toString());
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTexturePayoffTable.read(input, source));
        try (var file = new java.io.RandomAccessFile(input.toFile(), "rw")) {
            file.setLength(4L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTexturePayoffTable.read(input, source));
    }

    @Test
    void menuRanksSizingAndSolveCapsAreValidated() {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxTextureFlopGameTest.table(source);
        for (var ranks :
                List.of(
                        List.<Integer>of(),
                        List.of(1, 1),
                        List.of(0),
                        List.of(21),
                        List.of(1, 2, 3, 4, 5, 6, 7)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxTextureStudy.select(source, ranks, .5));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureStudy.select(source, List.of(1), Double.NaN));
        for (int iterations : List.of(0, 3001))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxTextureStudy.solve(source, table, List.of(), iterations));
    }
}
