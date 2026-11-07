package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SevensPreflopDrillLoaderGuardsTest {
    static final String MANIFEST = "supplied-sevens-preflop-drill-manifest.json";
    static final String REQUEST = "supplied-sevens-root-study-request.json";
    static final String REPORT = "supplied-sevens-root-study-report.json.gz";
    @TempDir Path temp;

    Path copy() throws Exception {
        for (String name :
                List.of(MANIFEST, REQUEST, REPORT, "supplied-flop-size-menu-report.json.gz"))
            Files.copy(Path.of("../docs/data", name), temp.resolve(name));
        return temp.resolve(MANIFEST);
    }

    ObjectNode manifest() throws Exception {
        return (ObjectNode)
                SixMaxTexturePayoffTable.mapper()
                        .readTree(Files.readAllBytes(temp.resolve(MANIFEST)));
    }

    void write(ObjectNode node) throws Exception {
        Files.write(
                temp.resolve(MANIFEST), SixMaxTexturePayoffTable.mapper().writeValueAsBytes(node));
    }

    void repin(ObjectNode node, String key, Path file) throws Exception {
        node.put(
                key,
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(file))));
        write(node);
    }

    void rejects(Path path) {
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSevensPreflopDrillLoader.load(
                                path, SixMaxSevensPreflopDrillLoader.STUDY_HASH));
    }

    @Test
    void typedManifestRequiresExactSchemaPinFilesAndCompleteBudget() throws Exception {
        var path = copy();
        var valid = manifest();
        assertEquals(
                SixMaxSevensPreflopDrillLoader.StartupBudget.bounded(),
                SixMaxSevensPreflopDrillLoader.readManifest(path).startupBudget());
        for (String field :
                List.of(
                        "schemaVersion",
                        "studyHash",
                        "requestFile",
                        "reportFile",
                        "parentFile",
                        "parentStoredSha256",
                        "requestSha256",
                        "reportStoredSha256",
                        "startupBudget")) {
            var node = valid.deepCopy();
            node.putNull(field);
            write(node);
            rejects(path);
        }
        for (String field :
                List.of(
                        "physicalGames",
                        "maximumParentSolveCalls",
                        "maximumParentLpSolves",
                        "maximumEnvelopeLpSolves",
                        "freshReferenceRuns",
                        "freshIterations")) {
            var node = valid.deepCopy();
            ((ObjectNode) node.path("startupBudget")).put(field, 0);
            write(node);
            rejects(path);
        }
    }

    @Test
    void escapingAbsoluteAndNonBasenameDependenciesFailBeforeReplay() throws Exception {
        var path = copy();
        var valid = manifest();
        for (String name :
                List.of(
                        "../escape.json",
                        "sub/file.json",
                        "sub\\file.json",
                        "/tmp/escape.json",
                        "C:\\escape.json",
                        "",
                        ".")) {
            for (String field : List.of("requestFile", "reportFile", "parentFile")) {
                var node = valid.deepCopy();
                node.put(field, name);
                write(node);
                rejects(path);
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSevensPreflopDrillLoader.load(
                                null, SixMaxSevensPreflopDrillLoader.STUDY_HASH));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSevensPreflopDrillLoader.load(path, "0".repeat(64)));
    }

    @Test
    void changedStoredBytesFailEvenWithOtherwiseValidManifest() throws Exception {
        var path = copy();
        Files.writeString(temp.resolve(REQUEST), " ", StandardOpenOption.APPEND);
        rejects(path);
        Files.copy(
                Path.of("../docs/data", REQUEST),
                temp.resolve(REQUEST),
                StandardCopyOption.REPLACE_EXISTING);
        Files.write(temp.resolve(REPORT), new byte[] {1, 2, 3});
        rejects(path);
    }

    @Test
    void aliasesAndDirectoriesCannotBecomeStartupSources() throws Exception {
        var path = copy();
        var valid = manifest();
        var node = valid.deepCopy();
        node.put("requestFile", MANIFEST);
        write(node);
        rejects(path);
        write(valid);
        Files.delete(temp.resolve(REPORT));
        Files.createLink(temp.resolve(REPORT), temp.resolve(REQUEST));
        rejects(path);
        Files.delete(temp.resolve(REPORT));
        Files.createDirectory(temp.resolve(REPORT));
        rejects(path);
    }

    @Test
    void malformedAndInflatedGzipCannotPassEvenWithRefreshedStoredPins() throws Exception {
        var path = copy();
        var node = manifest();
        Files.write(temp.resolve(REPORT), new byte[] {31, -117, 0});
        repin(node, "reportStoredSha256", temp.resolve(REPORT));
        rejects(path);
        try (var raw = Files.newOutputStream(temp.resolve(REPORT));
                var gzip = new GZIPOutputStream(raw)) {
            gzip.write(new byte[SixMaxSevensRootStudy.MAX_BYTES + 1]);
        }
        repin(node, "reportStoredSha256", temp.resolve(REPORT));
        rejects(path);
    }

    @Test
    void storedAndManifestByteCapsAreEnforced() throws Exception {
        var path = copy();
        Files.write(temp.resolve(REPORT), new byte[SixMaxSevensRootStudy.MAX_BYTES + 1]);
        rejects(path);
        Files.write(path, new byte[SixMaxSevensPreflopDrillLoader.MAX_MANIFEST_BYTES + 1]);
        rejects(path);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSevensPreflopDrillLoader.readManifest(path));
    }

    @Test
    void strictManifestAndRequestParsingRejectDuplicateUnknownAndCoercedFields() throws Exception {
        var path = copy();
        String original = Files.readString(path);
        for (String changed :
                List.of(
                        original + " {}",
                        original.replaceFirst("\\{", "{\"unknown\":0,"),
                        original.replaceFirst("\\{", "{\"studyHash\":\"0\","),
                        original.replace("240000", "\"240000\""),
                        original.replace("240000", "240000.5"))) {
            Files.writeString(path, changed);
            rejects(path);
        }
        Files.writeString(path, original);
        var node = manifest();
        String request = Files.readString(temp.resolve(REQUEST));
        Files.writeString(temp.resolve(REQUEST), request + " {}");
        repin(node, "requestSha256", temp.resolve(REQUEST));
        rejects(path);
    }

    @Test
    void reportMutationCannotSelfAuthorizeByRefreshingStoredSha() throws Exception {
        var path = copy();
        var node = manifest();
        var mapper = SixMaxTexturePayoffTable.mapper();
        var report =
                (ObjectNode)
                        mapper.readTree(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        temp.resolve(REPORT), SixMaxSevensRootStudy.MAX_BYTES));
        // Editing the report and refreshing a manifest SHA cannot authorize a new source.
        report.put("publicationStatus", "EDITED_REPORT");
        try (var raw = Files.newOutputStream(temp.resolve(REPORT));
                var gzip = new GZIPOutputStream(raw)) {
            gzip.write(mapper.writeValueAsBytes(report));
        }
        repin(node, "reportStoredSha256", temp.resolve(REPORT));
        var changed = report;
        assertNotEquals(
                SixMaxSevensPreflopDrillLoader.STUDY_HASH, SixMaxHeadsUpPreflopGame.hash(changed));
        rejects(path);
    }

    @Test
    void originalParentCannotBeChangedOrRepinned() throws Exception {
        var path = copy();
        var valid = manifest();
        var file = temp.resolve("supplied-flop-size-menu-report.json.gz");
        byte[] changed = Files.readAllBytes(file);
        changed[0] ^= 1;
        Files.write(file, changed);
        rejects(path);
        repin(valid, "parentStoredSha256", file);
        rejects(path);
    }

    @Test
    void actualLocalHardLinkIsRejectedAsAnAliasBeforePinOrSolverWork() throws Exception {
        var path = copy();
        Files.delete(temp.resolve(REPORT));
        Files.createLink(temp.resolve(REPORT), temp.resolve(REQUEST));
        assertTrue(Files.isSameFile(temp.resolve(REPORT), temp.resolve(REQUEST)));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSevensPreflopDrillLoader.load(
                                        path, SixMaxSevensPreflopDrillLoader.STUDY_HASH));
        assertEquals("Aliased sevens preflop startup sources", error.getMessage());
    }
}
