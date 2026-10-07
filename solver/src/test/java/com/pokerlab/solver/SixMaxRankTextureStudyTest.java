package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxRankTextureStudyTest {
    @Test
    void fullJointSolveRecordsItsOwnModelAndReplaysWithoutTraining(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxRankTextureFlopGameTest.table(source);
        var solved =
                SixMaxRankTextureStudy.solve(
                        source,
                        table,
                        SixMaxRankTextureFlopGameTest.menu(),
                        3,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        assertEquals(SixMaxRankTextureStudy.MODEL, solved.report().model());
        assertEquals(SixMaxTextureStudy.PRUNED_ALGORITHM, solved.report().algorithm());
        assertEquals("PARENT_INFORMATION_SET_BEST_RESPONSES_ONLY", solved.report().qualityScope());
        assertEquals("VALIDATION_ONLY", solved.report().publicationStatus());
        assertTrue(solved.report().publicSignals() > 1000);
        assertTrue(solved.inactiveUtilityPrunedNodes() > 0);
        assertEquals(0, solved.traversal().sampledChanceNodes());
        assertEquals(source.solution().strategy().size(), solved.report().preflopInformationSets());
        assertTrue(
                solved.report().checkdownRecoveryErrorBb().stream()
                        .allMatch(e -> Math.abs(e) < 1e-9));
        assertEquals(
                0,
                solved.report().jointlySolvedInRankTextureGame().profileUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-9);
        var path = temp.resolve("cp.json.gz");
        SixMaxRankTextureStudy.write(path, solved.checkpoint(), source, table);
        var bytes = Files.readAllBytes(path);
        var loaded = SixMaxRankTextureStudy.read(path, source, table);
        assertEquals(solved.checkpoint(), loaded);
        assertEquals(solved.report(), SixMaxRankTextureStudy.assess(source, table, loaded));
        SixMaxRankTextureStudy.write(path, loaded, source, table);
        assertArrayEquals(bytes, Files.readAllBytes(path));
        var complete = SixMaxRankTextureStudy.rebuild(source, table, loaded);
        var missing =
                SixMaxRankTextureStudy.checkpoint(
                        source,
                        table,
                        complete,
                        source.solution(),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureStudy.rebuild(source, table, missing));
        var plain = temp.resolve("bad.json");
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().readTree(SixMaxTextureStudy.json(loaded));
        tree.put("model", SixMaxTextureStudy.MODEL);
        Files.writeString(plain, tree.toString());
        assertThrows(Exception.class, () -> SixMaxRankTextureStudy.read(plain, source, table));
        tree.put("model", SixMaxRankTextureStudy.MODEL);
        var selection =
                (com.fasterxml.jackson.databind.node.ObjectNode) tree.get("selections").get(0);
        selection.put("potFraction", .25);
        Files.writeString(plain, tree.toString());
        assertThrows(Exception.class, () -> SixMaxRankTextureStudy.read(plain, source, table));
        selection.put("potFraction", .5);
        tree.put("solutionHash", "0".repeat(64));
        Files.writeString(plain, tree.toString());
        assertThrows(Exception.class, () -> SixMaxRankTextureStudy.read(plain, source, table));
        Files.write(path, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> SixMaxRankTextureStudy.read(path, source, table));
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(path))) {
            zip.write(new byte[SixMaxRankTextureStudy.MAX_CHECKPOINT_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureStudy.read(path, source, table));
    }

    @Test
    void pruningPreservesPoliciesAndCapsAreExplicit() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxRankTextureFlopGameTest.table(source);
        var original =
                SixMaxRankTextureStudy.solve(
                        source,
                        table,
                        SixMaxRankTextureFlopGameTest.menu(),
                        2,
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        var pruned =
                SixMaxRankTextureStudy.solve(
                        source,
                        table,
                        SixMaxRankTextureFlopGameTest.menu(),
                        2,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        assertEquals(original.checkpoint().gameHash(), pruned.checkpoint().gameHash());
        assertEquals("CFR_PLUS", original.checkpoint().algorithm());
        MultiPlayerInactivePruningTest.samePolicy(
                original.checkpoint().solution(), pruned.checkpoint().solution(), 1e-12);
        assertEquals(0, original.inactiveUtilityPrunedNodes());
        assertTrue(pruned.traversal().visitedNodes() < original.traversal().visitedNodes());
        for (int invalid : java.util.List.of(0, 1001))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxRankTextureStudy.solve(
                                    source,
                                    table,
                                    SixMaxRankTextureFlopGameTest.menu(),
                                    invalid,
                                    MultiPlayerCfrSolver.InactivePruning.NONE));
    }

    @Test
    void guardedCliIsDeterministicAndRejectsAliasesBeforeChangingOutputs(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxRankTextureFlopGameTest.table(source);
        var input = temp.resolve("source.json");
        var equity = temp.resolve("table.json.gz");
        var cp = temp.resolve("cp.json.gz");
        var report = temp.resolve("report.json");
        Files.writeString(input, MultiwayPackJson.writeFullRound(source));
        SixMaxRankTexturePayoffTable.writeBytes(
                equity,
                SixMaxRankTexturePayoffTable.json(table)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                4 * 1024 * 1024);
        String[] args = {
            "solve",
            input.toString(),
            equity.toString(),
            cp.toString(),
            report.toString(),
            "2",
            "1",
            ".5",
            "FIXED_UTILITY"
        };
        SixMaxRankTextureStudyMain.main(args);
        var cpBytes = Files.readAllBytes(cp);
        var reportBytes = Files.readAllBytes(report);
        SixMaxRankTextureStudyMain.main(args);
        assertArrayEquals(cpBytes, Files.readAllBytes(cp));
        assertArrayEquals(reportBytes, Files.readAllBytes(report));
        var replay = temp.resolve("replay.json");
        SixMaxRankTextureStudyMain.main(
                new String[] {
                    "audit", input.toString(), equity.toString(), cp.toString(), replay.toString()
                });
        assertArrayEquals(reportBytes, Files.readAllBytes(replay));
        var plain = temp.resolve("repacked.json");
        SixMaxRankTextureStudyMain.main(
                new String[] {
                    "repack", input.toString(), equity.toString(), cp.toString(), plain.toString()
                });
        assertEquals(
                SixMaxRankTextureStudy.read(cp, source, table),
                SixMaxRankTextureStudy.read(plain, source, table));
        var alias = temp.resolve("alias.json");
        Files.createLink(alias, input);
        var bad = args.clone();
        bad[4] = alias.toString();
        assertThrows(IllegalArgumentException.class, () -> SixMaxRankTextureStudyMain.main(bad));
        bad[4] = report.toString();
        bad[5] = "1001";
        assertThrows(IllegalArgumentException.class, () -> SixMaxRankTextureStudyMain.main(bad));
        bad[5] = "2";
        bad[8] = "ESTIMATE";
        assertThrows(IllegalArgumentException.class, () -> SixMaxRankTextureStudyMain.main(bad));
        bad[8] = "NONE";
        bad[6] = "1,1";
        assertThrows(IllegalArgumentException.class, () -> SixMaxRankTextureStudyMain.main(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTextureStudyMain.main(new String[0]));
        assertArrayEquals(cpBytes, Files.readAllBytes(cp));
        assertArrayEquals(reportBytes, Files.readAllBytes(report));
    }
}
