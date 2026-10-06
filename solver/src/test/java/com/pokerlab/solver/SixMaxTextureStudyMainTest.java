package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxTextureStudyMainTest {
    @Test
    void deterministicSolveAndReadOnlyAuditProtectInputsAndOldOutputs(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var sourcePath = temp.resolve("source.json");
        var tablePath = temp.resolve("table.json");
        var checkpoint = temp.resolve("output/policy.json");
        var report = temp.resolve("output/report.json");
        var replay = temp.resolve("replay.json");
        Files.writeString(sourcePath, MultiwayPackJson.writeFullRound(source));
        Files.writeString(
                tablePath, SixMaxTexturePayoffTable.json(SixMaxTextureFlopGameTest.table(source)));
        var sourceBytes = Files.readAllBytes(sourcePath);
        var tableBytes = Files.readAllBytes(tablePath);
        String[] solve = {
            "solve",
            sourcePath.toString(),
            tablePath.toString(),
            checkpoint.toString(),
            report.toString(),
            "2",
            "1",
            ".5"
        };
        SixMaxTextureStudyMain.main(solve);
        var policyBytes = Files.readAllBytes(checkpoint);
        var reportBytes = Files.readAllBytes(report);
        SixMaxTextureStudyMain.main(solve);
        assertArrayEquals(policyBytes, Files.readAllBytes(checkpoint));
        assertArrayEquals(reportBytes, Files.readAllBytes(report));
        SixMaxTextureStudyMain.main(
                new String[] {
                    "audit",
                    sourcePath.toString(),
                    tablePath.toString(),
                    checkpoint.toString(),
                    replay.toString()
                });
        assertArrayEquals(reportBytes, Files.readAllBytes(replay));
        var compressed = temp.resolve("repacked.json.gz");
        SixMaxTextureStudyMain.main(
                new String[] {
                    "repack",
                    sourcePath.toString(),
                    tablePath.toString(),
                    checkpoint.toString(),
                    compressed.toString()
                });
        SixMaxTextureStudyMain.main(
                new String[] {
                    "audit",
                    sourcePath.toString(),
                    tablePath.toString(),
                    compressed.toString(),
                    replay.toString()
                });
        assertArrayEquals(reportBytes, Files.readAllBytes(replay));
        var alias = temp.resolve("hardlink.json");
        Files.createLink(alias, sourcePath);
        var collision = solve.clone();
        collision[4] = alias.toString();
        assertThrows(IllegalArgumentException.class, () -> SixMaxTextureStudyMain.main(collision));
        var invalid = solve.clone();
        invalid[5] = "3001";
        assertThrows(IllegalArgumentException.class, () -> SixMaxTextureStudyMain.main(invalid));
        invalid[5] = "2";
        invalid[6] = "1,1";
        assertThrows(IllegalArgumentException.class, () -> SixMaxTextureStudyMain.main(invalid));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxTexturePayoffTableMain.main(
                                new String[] {sourcePath.toString(), alias.toString()}));
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxTextureStudyMain.main(new String[0]));
        assertArrayEquals(sourceBytes, Files.readAllBytes(sourcePath));
        assertArrayEquals(tableBytes, Files.readAllBytes(tablePath));
        Files.writeString(tablePath, "{}");
        assertThrows(Exception.class, () -> SixMaxTextureStudyMain.main(solve));
        assertArrayEquals(policyBytes, Files.readAllBytes(checkpoint));
        assertArrayEquals(reportBytes, Files.readAllBytes(report));
        try (var files = Files.list(checkpoint.getParent())) {
            assertEquals(2, files.count());
        }
    }
}
