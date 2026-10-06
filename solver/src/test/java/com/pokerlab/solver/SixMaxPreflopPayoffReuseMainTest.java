package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxPreflopPayoffReuseMainTest {
    private static List<Path> inputs(Path temp) throws Exception {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var target =
                SixMaxPreflopPayoffReuseTest.spot(source.spot().ranges(), 9, CashRakeRule.none());
        var sourcePath = temp.resolve("source.json");
        var spotPath = temp.resolve("spot.json");
        Files.writeString(sourcePath, MultiwayPackJson.writeFullRound(source));
        Files.writeString(spotPath, MultiwayPackJson.writeFullRoundSpot(target));
        return List.of(
                sourcePath,
                spotPath,
                temp.resolve("outputs/pack.json"),
                temp.resolve("outputs/report.json"));
    }

    private static String[] args(List<Path> paths) {
        return new String[] {
            paths.get(0).toString(),
            paths.get(1).toString(),
            paths.get(2).toString(),
            paths.get(3).toString(),
            "2",
            SixMaxPreflopPayoffReuseTest.TIME
        };
    }

    @Test
    void deterministicOutputsBindBothModelsAndPreserveInputs(@TempDir Path temp) throws Exception {
        var paths = inputs(temp);
        var beforeSource = Files.readAllBytes(paths.get(0));
        var beforeSpot = Files.readAllBytes(paths.get(1));
        SixMaxPreflopPayoffReuseMain.main(args(paths));
        var packBytes = Files.readAllBytes(paths.get(2));
        var reportBytes = Files.readAllBytes(paths.get(3));
        var report =
                new ObjectMapper()
                        .readValue(reportBytes, SixMaxPreflopPayoffReuseMain.Artifact.class);
        var pack = MultiwayPackJson.readFullRound(Files.readString(paths.get(2)));
        assertEquals("six-max-preflop-payoff-reuse/v1", report.schemaVersion());
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), report.targetPackHash());
        assertEquals(pack.spotHash(), report.provenance().targetSpotHash());
        assertTrue(report.sourceTree().allInTerminals() > 0);
        assertEquals(0, report.targetTree().allInTerminals());
        assertEquals(
                1,
                report.targetReach().terminalProbability().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-12);
        assertTrue(report.targetReach().headsUpContinuationProbability() > 0);
        assertNull(report.targetMenuSearch());
        SixMaxPreflopPayoffReuseMain.main(args(paths));
        assertArrayEquals(packBytes, Files.readAllBytes(paths.get(2)));
        assertArrayEquals(reportBytes, Files.readAllBytes(paths.get(3)));
        assertArrayEquals(beforeSource, Files.readAllBytes(paths.get(0)));
        assertArrayEquals(beforeSpot, Files.readAllBytes(paths.get(1)));
        try (var files = Files.list(paths.get(2).getParent())) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void searchIsBoundAndExplicitAndDoesNotAdmitOneHandOpponents(@TempDir Path temp)
            throws Exception {
        var paths = inputs(temp);
        var arguments = new java.util.ArrayList<>(List.of(args(paths)));
        arguments.addAll(List.of("--search", "711", "2", "1", "1"));
        SixMaxPreflopPayoffReuseMain.main(arguments.toArray(String[]::new));
        var report =
                new ObjectMapper()
                        .readValue(
                                paths.get(3).toFile(), SixMaxPreflopPayoffReuseMain.Artifact.class);
        assertNotNull(report.targetMenuSearch());
        assertEquals(2, report.targetMenuSearch().settings().seedCount());
        assertNotEquals("MENU_FOUND", report.targetMenuSearch().status());
        assertTrue(report.targetMenuSearch().proposedSelections().isEmpty());
    }

    @Test
    void rejectsAliasesOfEveryInputAndOutputIncludingHardLinks(@TempDir Path temp)
            throws Exception {
        var paths = inputs(temp);
        Files.createDirectories(paths.get(2).getParent());
        Files.writeString(paths.get(2), "keep pack");
        Files.writeString(paths.get(3), "keep report");
        for (int first = 0; first < 4; first++)
            for (int second = first + 1; second < 4; second++) {
                var alias = temp.resolve("alias-" + first + "-" + second);
                Files.createLink(alias, paths.get(first));
                var directArgs = args(paths);
                directArgs[second] = paths.get(first).toString();
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxPreflopPayoffReuseMain.main(directArgs));
                var linkArgs = args(paths);
                linkArgs[second] = alias.toString();
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxPreflopPayoffReuseMain.main(linkArgs));
            }
        assertEquals("keep pack", Files.readString(paths.get(2)));
        assertEquals("keep report", Files.readString(paths.get(3)));
    }

    @Test
    void invalidSettingsSourceSupportAndOversizeInputsPreserveOutputs(@TempDir Path temp)
            throws Exception {
        var paths = inputs(temp);
        Files.createDirectories(paths.get(2).getParent());
        Files.writeString(paths.get(2), "keep pack");
        Files.writeString(paths.get(3), "keep report");
        for (var setting : List.of("0", "3001", "NaN")) {
            var arguments = args(paths);
            arguments[4] = setting;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxPreflopPayoffReuseMain.main(arguments));
        }
        for (var options :
                List.of(
                        List.of("--unknown", "711", "2", "1", "1"),
                        List.of("--search", "711", "17", "1", "1"),
                        List.of("--search", "711"))) {
            var arguments = new java.util.ArrayList<>(List.of(args(paths)));
            arguments.addAll(options);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxPreflopPayoffReuseMain.main(arguments.toArray(String[]::new)));
        }
        var source = SixMaxPreflopPayoffReuseTest.source();
        Files.writeString(
                paths.get(1),
                MultiwayPackJson.writeFullRoundSpot(
                        SixMaxPreflopPayoffReuseTest.spot(
                                source.spot().ranges(), 9, new CashRakeRule(.05, 1, true))));
        var rakeFailure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxPreflopPayoffReuseMain.main(args(paths)));
        assertTrue(rakeFailure.getMessage().contains("zero applied rake"));
        var ranges = new java.util.ArrayList<>(source.spot().ranges());
        ranges.set(3, List.of(ranges.get(3).getFirst()));
        Files.writeString(
                paths.get(1),
                MultiwayPackJson.writeFullRoundSpot(
                        SixMaxPreflopPayoffReuseTest.spot(ranges, 9, CashRakeRule.none())));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopPayoffReuseMain.main(args(paths)));
        for (int input = 0; input < 2; input++) {
            try (var file = new java.io.RandomAccessFile(paths.get(input).toFile(), "rw")) {
                file.setLength(16L * 1024 * 1024 + 1);
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxPreflopPayoffReuseMain.main(args(paths)));
        }
        assertEquals("keep pack", Files.readString(paths.get(2)));
        assertEquals("keep report", Files.readString(paths.get(3)));
    }
}
