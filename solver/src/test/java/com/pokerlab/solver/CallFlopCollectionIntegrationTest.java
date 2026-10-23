package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CallFlopCollectionIntegrationTest {
    static final Path MANIFEST =
            Path.of("../docs/data/supplied-fourteen-question-flop-collection-manifest.json");
    static final Path REVIEW =
            Path.of("../docs/data/supplied-fourteen-question-flop-collection-review.json");
    static final String PIN = "62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619";
    static SixMaxCallFlopCollection collection;

    @BeforeAll
    static void completeSixSourcePhysicalStartup() throws Exception {
        // One explicit full reservation per fixture: six sources, twelve fresh runs, 972k
        // iterations.
        collection = SixMaxCallFlopCollectionLoader.load(MANIFEST, PIN);
    }

    @Test
    void allFourteenTypedQuestionsPreserveEveryOriginalQuestion() throws Exception {
        var old =
                SixMaxQualifiedFlopCollection.read(
                        Path.of(
                                "../docs/data/supplied-eleven-question-flop-collection-review.json"));
        assertEquals(14, collection.questions().size());
        for (int i = 0; i < 14; i++) {
            var q = collection.question(i);
            assertEquals(i, q.index());
            assertEquals(14, q.totalQuestions());
            assertEquals(i < 11 ? i : i - 11, q.sourceQuestionIndex());
            if (i < 11) {
                assertEquals(old.questions().get(i), q.decision().legacy());
                assertNull(q.decision().shared());
            } else {
                assertNull(q.decision().legacy());
                assertEquals(
                        List.of("Ah Kh", "As Qs", "Qh Th").get(i - 11),
                        q.decision().shared().decision().heroCards());
                assertEquals(SixMaxCallFlopCollection.BATCH_HASH, q.decision().reportHash());
            }
            assertEquals(
                    i == 1 || i >= 6 ? List.of("fold", "call") : List.of("check", "bet"),
                    q.decision().legalActions());
        }
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
        assertThrows(IllegalArgumentException.class, () -> collection.question(14));
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
    }

    @Test
    void allLegalGradesAndCanonicalReviewMatchSavedEvidence(@TempDir Path temp) throws Exception {
        var saved = SixMaxCallFlopCollection.read(REVIEW);
        assertEquals(saved, collection.replay(saved));
        assertEquals(new SixMaxCallFlopCollection.Counts(14, 14, 14, 0), saved.counts());
        for (var a : saved.attempts())
            assertEquals(
                    a.feedback(),
                    collection.grade(collection.question(a.questionIndex()), a.action()));
        var path = temp.resolve("review.json");
        collection.write(path, saved);
        assertArrayEquals(Files.readAllBytes(REVIEW), Files.readAllBytes(path));
        assertThrows(FileAlreadyExistsException.class, () -> collection.write(path, saved));
        var robust = new TreeMap<String, Integer>();
        for (var a : saved.attempts())
            if (a.feedback().decision().selected().classification()
                    == SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT)
                robust.merge(a.action(), 1, Integer::sum);
        assertEquals(Map.of("fold", 2, "check", 1, "call", 7, "bet", 4), robust);
    }

    @Test
    void retriesAndSubsetsUseSeparateAttemptAndCoverageCounts() {
        var choice =
                new SixMaxCallFlopCollection.Choice(
                        12, collection.question(12).decision().reportHash(), "call");
        assertEquals(
                new SixMaxCallFlopCollection.Counts(1, 100, 0, 0),
                collection.review(Collections.nCopies(100, choice)).counts());
        assertEquals(
                new SixMaxCallFlopCollection.Counts(0, 0, 0, 0),
                collection.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxCallFlopCollection.Choice(
                                                12, "0".repeat(64), "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(12), "bet"));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(0), "call"));
    }

    @Test
    void forgedQuestionMappingsAndReviewsDoNotSupplyTheirOwnValues() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of("collectionHash", "sourceQuestionIndex", "totalQuestions", "index")) {
            var tree = (ObjectNode) mapper.valueToTree(collection.question(11));
            if (field.equals("collectionHash")) tree.put(field, "0".repeat(64));
            else tree.put(field, 99);
            if (field.equals("collectionHash")) {
                var changed = mapper.treeToValue(tree, SixMaxCallFlopCollection.Question.class);
                assertThrows(
                        IllegalArgumentException.class, () -> collection.grade(changed, "call"));
            } else
                assertThrows(
                        com.fasterxml.jackson.databind.JsonMappingException.class,
                        () -> mapper.treeToValue(tree, SixMaxCallFlopCollection.Question.class),
                        field);
        }
        var saved =
                collection.review(
                        List.of(
                                new SixMaxCallFlopCollection.Choice(
                                        11, SixMaxCallFlopCollection.BATCH_HASH, "fold")));
        for (String kind : List.of("counts", "value", "local-index", "pin")) {
            var tree = (ObjectNode) mapper.valueToTree(saved);
            switch (kind) {
                case "counts" ->
                        ((ObjectNode) tree.path("counts")).put("distinctQuestionsAttempted", 14);
                case "value" ->
                        ((ObjectNode)
                                        tree.path("attempts")
                                                .get(0)
                                                .path("feedback")
                                                .path("decision")
                                                .path("shared")
                                                .path("selected"))
                                .put("lowerEvBb", 99.);
                case "local-index" ->
                        ((ObjectNode) tree.path("attempts").get(0).path("feedback"))
                                .put("sourceQuestionIndex", 1);
                case "pin" -> tree.put("collectionHash", "0".repeat(64));
            }
            var changed = mapper.treeToValue(tree, SixMaxCallFlopCollection.Review.class);
            assertThrows(IllegalArgumentException.class, () -> collection.replay(changed), kind);
        }
    }

    @Test
    void malformedManifestsCannotReachOptimization(@TempDir Path temp) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind :
                List.of("budget", "traversal", "missing-pin", "wrong-source", "shared-game")) {
            var tree =
                    (ObjectNode)
                            mapper.valueToTree(
                                    SixMaxCallFlopCollectionLoader.readManifest(MANIFEST));
            switch (kind) {
                case "budget" ->
                        ((ObjectNode) tree.path("startupBudget")).put("freshIterations", 732000);
                case "traversal" -> tree.put("batchRequestFile", "../request.json");
                case "missing-pin" -> tree.remove("batchReportHash");
                case "wrong-source" ->
                        tree.put("batchReportHash", SixMaxWeakHandFlopTrainer.REPORT_HASH);
                case "shared-game" -> tree.put("sharedInputHash", "0".repeat(64));
            }
            Path path = temp.resolve("manifest.json");
            Files.writeString(path, tree.toString());
            if (kind.equals("shared-game"))
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxCallFlopCollectionLoader.load(path, PIN));
            else
                assertThrows(
                        com.fasterxml.jackson.databind.JsonMappingException.class,
                        () -> SixMaxCallFlopCollectionLoader.load(path, PIN),
                        kind);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxCallFlopCollectionLoader.load(MANIFEST, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxCallFlopCollectionLoader.load(MANIFEST, null));
    }

    @Test
    void byteChangesAndHardlinkAliasesAreRejectedBeforeReplay(@TempDir Path temp) throws Exception {
        var manifest = SixMaxCallFlopCollectionLoader.readManifest(MANIFEST);
        for (Path source : SixMaxCallFlopCollectionLoader.dependencies(MANIFEST, manifest))
            Files.copy(source, temp.resolve(source.getFileName()));
        Path copied = temp.resolve(MANIFEST.getFileName());
        Path request = temp.resolve(manifest.batchRequestFile());
        Files.writeString(request, Files.readString(request) + " ");
        assertEquals(
                "Shared batch bytes, input, report or aggregate reservation differs",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxCallFlopCollectionLoader.load(copied, PIN))
                        .getMessage());
        Files.delete(request);
        Files.createLink(request, temp.resolve("supplied-weak-hand-qualified-flop-request.json"));
        assertEquals(
                "Inputs and outputs must be distinct files",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxCallFlopCollectionLoader.load(copied, PIN))
                        .getMessage());
    }

    @Test
    void nestedManifestPathChangesCannotEscapeTheOuterSnapshotInventory(@TempDir Path temp)
            throws Exception {
        var manifest = SixMaxCallFlopCollectionLoader.readManifest(MANIFEST);
        for (Path source : SixMaxCallFlopCollectionLoader.dependencies(MANIFEST, manifest))
            Files.copy(source, temp.resolve(source.getFileName()));
        Path copied = temp.resolve(MANIFEST.getFileName());
        var original = SixMaxCallFlopCollectionLoader.dependencies(copied, manifest);
        Path nested = temp.resolve("supplied-ten-question-flop-collection-manifest.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.readTree(Files.readString(nested));
        var source = (ObjectNode) tree.path("sources").get(0);
        Files.copy(
                temp.resolve(source.path("requestFile").asText()),
                temp.resolve("renamed-request.json"));
        source.put("requestFile", "renamed-request.json");
        Files.writeString(nested, tree.toString());
        assertEquals(
                "Collection dependency inventory changed before replay",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        SixMaxCallFlopCollectionLoader.verifyDependencies(
                                                copied, manifest, original))
                        .getMessage());
    }
}
