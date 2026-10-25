package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class UnpairedFlopCollectionIntegrationTest {
    static final Path MANIFEST =
            Path.of("../docs/data/supplied-eighteen-question-flop-collection-manifest.json");
    static final Path REVIEW =
            Path.of("../docs/data/supplied-eighteen-question-flop-collection-review.json");
    static final String PIN = "b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2";
    static SixMaxUnpairedFlopCollection collection;

    @BeforeAll
    static void completeSevenSourcePhysicalStartup() throws Exception {
        // One explicit full reservation per fixture: seven sources, fourteen fresh runs, 1,212k
        // iterations.
        collection = SixMaxUnpairedFlopCollectionLoader.load(MANIFEST, PIN);
    }

    @Test
    void allEighteenTypedQuestionsPreserveEveryOriginalQuestion() throws Exception {
        var old =
                SixMaxCallFlopCollection.read(
                        Path.of(
                                "../docs/data/supplied-fourteen-question-flop-collection-review.json"));
        assertEquals(18, collection.questions().size());
        for (int i = 0; i < 18; i++) {
            var q = collection.question(i);
            assertEquals(i, q.index());
            assertEquals(18, q.totalQuestions());
            assertEquals(i < 14 ? i : i - 14, q.sourceQuestionIndex());
            if (i < 14) {
                assertEquals(old.questions().get(i), q.decision().legacy());
                assertNull(q.decision().shared());
            } else {
                assertNull(q.decision().legacy());
                assertEquals(
                        List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th").get(i - 14),
                        q.decision().shared().decision().heroCards());
                assertEquals(SixMaxUnpairedFlopCollection.BATCH_HASH, q.decision().reportHash());
            }
            assertEquals(
                    i == 1 || i >= 6 ? List.of("fold", "call") : List.of("check", "bet"),
                    q.decision().legalActions());
        }
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
        assertThrows(IllegalArgumentException.class, () -> collection.question(18));
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
    }

    @Test
    void allLegalGradesAndCanonicalReviewMatchSavedEvidence(@TempDir Path temp) throws Exception {
        var saved = SixMaxUnpairedFlopCollection.read(REVIEW);
        assertEquals(saved, collection.replay(saved));
        assertEquals(new SixMaxUnpairedFlopCollection.Counts(18, 18, 18, 0), saved.counts());
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
        assertEquals(Map.of("fold", 3, "check", 1, "call", 10, "bet", 4), robust);
    }

    @Test
    void retriesAndSubsetsUseSeparateAttemptAndCoverageCounts() {
        var choice =
                new SixMaxUnpairedFlopCollection.Choice(
                        16, collection.question(16).decision().reportHash(), "call");
        assertEquals(
                new SixMaxUnpairedFlopCollection.Counts(1, 100, 0, 0),
                collection.review(Collections.nCopies(100, choice)).counts());
        assertEquals(
                new SixMaxUnpairedFlopCollection.Counts(0, 0, 0, 0),
                collection.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxUnpairedFlopCollection.Choice(
                                                16, "0".repeat(64), "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(16), "bet"));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(0), "call"));
    }

    @Test
    void forgedQuestionMappingsAndReviewsDoNotSupplyTheirOwnValues() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of("collectionHash", "sourceQuestionIndex", "totalQuestions", "index")) {
            var tree = (ObjectNode) mapper.valueToTree(collection.question(14));
            if (field.equals("collectionHash")) tree.put(field, "0".repeat(64));
            else tree.put(field, 99);
            if (field.equals("collectionHash")) {
                var changed = mapper.treeToValue(tree, SixMaxUnpairedFlopCollection.Question.class);
                assertThrows(
                        IllegalArgumentException.class, () -> collection.grade(changed, "call"));
            } else
                assertThrows(
                        com.fasterxml.jackson.databind.JsonMappingException.class,
                        () -> mapper.treeToValue(tree, SixMaxUnpairedFlopCollection.Question.class),
                        field);
        }
        var saved =
                collection.review(
                        List.of(
                                new SixMaxUnpairedFlopCollection.Choice(
                                        14, SixMaxUnpairedFlopCollection.BATCH_HASH, "fold")));
        for (String kind : List.of("counts", "value", "local-index", "pin")) {
            var tree = (ObjectNode) mapper.valueToTree(saved);
            switch (kind) {
                case "counts" ->
                        ((ObjectNode) tree.path("counts")).put("distinctQuestionsAttempted", 18);
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
            var changed = mapper.treeToValue(tree, SixMaxUnpairedFlopCollection.Review.class);
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
                                    SixMaxUnpairedFlopCollectionLoader.readManifest(MANIFEST));
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
                        () -> SixMaxUnpairedFlopCollectionLoader.load(path, PIN));
            else
                assertThrows(
                        com.fasterxml.jackson.databind.JsonMappingException.class,
                        () -> SixMaxUnpairedFlopCollectionLoader.load(path, PIN),
                        kind);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxUnpairedFlopCollectionLoader.load(MANIFEST, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxUnpairedFlopCollectionLoader.load(MANIFEST, null));
    }

    @Test
    void byteChangesAndHardlinkAliasesAreRejectedBeforeReplay(@TempDir Path temp) throws Exception {
        var manifest = SixMaxUnpairedFlopCollectionLoader.readManifest(MANIFEST);
        for (Path source : SixMaxUnpairedFlopCollectionLoader.dependencies(MANIFEST, manifest))
            Files.copy(source, temp.resolve(source.getFileName()));
        Path copied = temp.resolve(MANIFEST.getFileName());
        Path request = temp.resolve(manifest.batchRequestFile());
        Files.writeString(request, Files.readString(request) + " ");
        assertEquals(
                "Shared batch bytes, input, report or aggregate reservation differs",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxUnpairedFlopCollectionLoader.load(copied, PIN))
                        .getMessage());
        Files.delete(request);
        Files.createLink(request, temp.resolve("supplied-weak-hand-qualified-flop-request.json"));
        assertEquals(
                "Inputs and outputs must be distinct files",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxUnpairedFlopCollectionLoader.load(copied, PIN))
                        .getMessage());
    }

    @Test
    void nestedManifestPathChangesCannotEscapeTheOuterSnapshotInventory(@TempDir Path temp)
            throws Exception {
        var manifest = SixMaxUnpairedFlopCollectionLoader.readManifest(MANIFEST);
        for (Path source : SixMaxUnpairedFlopCollectionLoader.dependencies(MANIFEST, manifest))
            Files.copy(source, temp.resolve(source.getFileName()));
        Path copied = temp.resolve(MANIFEST.getFileName());
        var original = SixMaxUnpairedFlopCollectionLoader.dependencies(copied, manifest);
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
                                        SixMaxUnpairedFlopCollectionLoader.verifyDependencies(
                                                copied, manifest, original))
                        .getMessage());
    }

    @Test
    void samePrivateCardsOnDifferentBoardsKeepDistinctQuestionAndGradeIdentity() {
        var old = collection.question(11);
        var added = collection.question(15);
        assertEquals("Ah Kh", old.decision().legacy().decision().shared().decision().heroCards());
        assertEquals("Ah Kh", added.decision().shared().decision().heroCards());
        assertNotEquals(old.decision().reportHash(), added.decision().reportHash());
        assertEquals(
                List.of("6d", "Qc", "Td"), added.decision().shared().decision().context().board());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxUnpairedFlopCollection.Choice(
                                                15, old.decision().reportHash(), "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.grade(
                                new SixMaxUnpairedFlopCollection.Question(
                                        collection.collectionHash(),
                                        15,
                                        18,
                                        SixMaxUnpairedFlopCollection.SELECTION,
                                        1,
                                        new SixMaxUnpairedFlopCollection.Decision(
                                                SixMaxUnpairedFlopCollection.Kind
                                                        .SHARED_REFERENCE_UNPAIRED_QUESTIONS,
                                                null,
                                                old.decision().legacy().decision().shared())),
                                "call"));
    }
}
