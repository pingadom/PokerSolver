package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RootPreflopDrillLoaderGuardsTest {
    static final String MANIFEST = "supplied-root-preflop-drill-manifest.json";
    static final String REQUEST = "supplied-root-envelope-feedback-request.json";
    static final String REPORT = "supplied-root-envelope-feedback-report.json.gz";
    @TempDir Path temp;

    Path copy() throws Exception {
        for (String name : List.of(MANIFEST, REQUEST, REPORT))
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
                        SixMaxRootPreflopDrillLoader.load(
                                path, SixMaxRootPreflopDrillLoader.STUDY_HASH));
    }

    @Test
    void typedManifestRequiresExactSchemaPinFilesAndCompleteBudget() throws Exception {
        var path = copy();
        var valid = manifest();
        assertEquals(
                SixMaxRootPreflopDrillLoader.StartupBudget.bounded(),
                SixMaxRootPreflopDrillLoader.readManifest(path).startupBudget());
        for (String field :
                List.of(
                        "schemaVersion",
                        "studyHash",
                        "requestFile",
                        "reportFile",
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
            for (String field : List.of("requestFile", "reportFile")) {
                var node = valid.deepCopy();
                node.put(field, name);
                write(node);
                rejects(path);
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootPreflopDrillLoader.load(
                                null, SixMaxRootPreflopDrillLoader.STUDY_HASH));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootPreflopDrillLoader.load(path, "0".repeat(64)));
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
            gzip.write(new byte[SixMaxRootEnvelopeFeedback.MAX_BYTES + 1]);
        }
        repin(node, "reportStoredSha256", temp.resolve(REPORT));
        rejects(path);
    }

    @Test
    void storedAndManifestByteCapsAreEnforced() throws Exception {
        var path = copy();
        Files.write(temp.resolve(REPORT), new byte[SixMaxRootEnvelopeFeedback.MAX_BYTES + 1]);
        rejects(path);
        Files.write(path, new byte[SixMaxRootPreflopDrillLoader.MAX_MANIFEST_BYTES + 1]);
        rejects(path);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootPreflopDrillLoader.readManifest(path));
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
                                        temp.resolve(REPORT),
                                        SixMaxRootEnvelopeFeedback.MAX_BYTES));
        // A structurally valid assessment DTO mutation must still fail the canonical study
        // identity.
        ((ObjectNode) report.path("primary")).put("decisionRegretBb", 99.0);
        try (var raw = Files.newOutputStream(temp.resolve(REPORT));
                var gzip = new GZIPOutputStream(raw)) {
            gzip.write(mapper.writeValueAsBytes(report));
        }
        repin(node, "reportStoredSha256", temp.resolve(REPORT));
        var changed = mapper.treeToValue(report, SixMaxRootEnvelopeFeedback.Report.class);
        assertNotEquals(
                SixMaxRootPreflopDrillLoader.STUDY_HASH, SixMaxHeadsUpPreflopGame.hash(changed));
        rejects(path);
    }
}
