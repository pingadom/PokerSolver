package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedIntervalCollectionTest {
    static final String HASH = "227fd33b33ce8ed8be46af3d0d4515e4c7b017c3468ec6f9a025bf96d67070b2";
    static SixMaxSuppliedIntervalCollection collection;
    static SixMaxSuppliedIntervalCollectionLoader.Manifest manifest;

    static Path data(String name) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-four-question-interval-collection-" + name + ".json");
    }

    @BeforeAll
    static void physicallyLoadFourQualifiedSources() throws Exception {
        manifest =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Files.readAllBytes(data("manifest")),
                                SixMaxSuppliedIntervalCollectionLoader.Manifest.class);
        collection = SixMaxSuppliedIntervalCollectionLoader.load(data("manifest"), HASH);
    }

    static SixMaxSuppliedIntervalCollection.Choice choice(int index, String action) {
        return new SixMaxSuppliedIntervalCollection.Choice(
                index, collection.question(index).decision().reportHash(), action);
    }

    @Test
    void realDistinctQuestionNavigationIncludesCallsRaiseAndStrategyDependentFeedback() {
        assertEquals(HASH, collection.collectionHash());
        assertEquals(4, collection.questions().size());
        assertEquals(
                List.of("7d 7h", "9d 9h", "Ac Jc", "9d 9h"),
                collection.questions().stream().map(q -> q.decision().heroCards()).toList());
        assertNotEquals(
                collection.question(1).decision().informationSet(),
                collection.question(3).decision().informationSet());
        for (int i = 0; i < 4; i++) {
            var q = collection.question(i);
            assertEquals(i, q.index());
            assertEquals(4, q.totalQuestions());
            assertEquals(HASH, q.collectionHash());
            assertEquals(SixMaxSuppliedIntervalCollection.SELECTION, q.selection());
            assertFalse(q.decision().trainerAdmission());
        }
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                collection
                        .grade(collection.question(1), "raise:9.0")
                        .decision()
                        .selected()
                        .classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                collection
                        .grade(collection.question(3), "raise:50.0")
                        .decision()
                        .selected()
                        .classification());
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
    }

    @Test
    void canonicalSavedReviewBindsQuestionOrderAllThreeVerdictsAndActualCoverage()
            throws Exception {
        var review =
                collection.review(
                        List.of(
                                choice(0, "call"),
                                choice(1, "raise:9.0"),
                                choice(2, "call"),
                                choice(3, "raise:50.0"),
                                choice(3, "fold")));
        assertEquals(new SixMaxSuppliedIntervalCollection.Counts(4, 3, 1, 1), review.counts());
        assertEquals(review, SixMaxSuppliedIntervalCollection.read(data("review")));
        assertEquals(
                review, collection.replay(SixMaxSuppliedIntervalCollection.read(data("review"))));
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
    }

    @Test
    void repeatedAttemptsDoNotInflateCoverageAndAllAttemptsRemainBounded() {
        var review = collection.review(Collections.nCopies(100, choice(0, "call")));
        assertEquals(new SixMaxSuppliedIntervalCollection.Counts(1, 100, 0, 0), review.counts());
        assertEquals(
                new SixMaxSuppliedIntervalCollection.Counts(0, 0, 0, 0),
                collection.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice(0, "call"))));
        assertThrows(IllegalArgumentException.class, () -> collection.review(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Arrays.asList(choice(0, "call"), null)));
    }

    @Test
    void stalePinsWrongIndicesForeignQuestionsAndIllegalActionsReject() {
        var q = collection.question(0);
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
        assertThrows(IllegalArgumentException.class, () -> collection.question(4));
        assertThrows(IllegalArgumentException.class, () -> collection.grade(q, "raise:50.0"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.grade(
                                new SixMaxSuppliedIntervalCollection.Question(
                                        "0".repeat(64),
                                        q.index(),
                                        q.totalQuestions(),
                                        q.selection(),
                                        q.decision()),
                                "call"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.grade(
                                new SixMaxSuppliedIntervalCollection.Question(
                                        q.collectionHash(),
                                        1,
                                        q.totalQuestions(),
                                        q.selection(),
                                        q.decision()),
                                "call"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxSuppliedIntervalCollection.Choice(
                                                0,
                                                collection.question(1).decision().reportHash(),
                                                "call"))));
    }

    @Test
    void callerCreatedGradesCountsAndReorderedQuestionsCannotReplaceOwnedFeedback()
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = collection.review(List.of(choice(0, "call")));
        for (String mutation : List.of("count", "pin", "grade", "order", "attempt-index")) {
            ObjectNode tree = mapper.valueToTree(original);
            switch (mutation) {
                case "count" ->
                        ((ObjectNode) tree.get("counts")).put("distinctQuestionsAttempted", 4);
                case "pin" ->
                        ((ObjectNode) tree.get("attempts").get(0).get("feedback"))
                                .put("collectionHash", "0".repeat(64));
                case "grade" ->
                        ((ObjectNode)
                                        tree.get("attempts")
                                                .get(0)
                                                .get("feedback")
                                                .get("decision")
                                                .get("selected"))
                                .put("upperLossBb", 99);
                case "order" -> {
                    var questions =
                            (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("questions");
                    var first = questions.get(0);
                    questions.set(0, questions.get(1));
                    questions.set(1, first);
                }
                case "attempt-index" -> ((ObjectNode) tree.get("attempts").get(0)).put("index", 3);
            }
            var altered = mapper.treeToValue(tree, SixMaxSuppliedIntervalCollection.Review.class);
            assertThrows(
                    IllegalArgumentException.class, () -> collection.replay(altered), mutation);
        }
    }

    @Test
    void reviewRoundTripsPlainAndGzipWithoutOverwritingArtifacts(@TempDir Path dir)
            throws Exception {
        var review = collection.review(List.of(choice(1, "raise:9.0")));
        for (String name : List.of("review.json", "review.json.gz")) {
            var path = dir.resolve(name);
            collection.write(path, review);
            assertEquals(review, collection.replay(SixMaxSuppliedIntervalCollection.read(path)));
            assertThrows(FileAlreadyExistsException.class, () -> collection.write(path, review));
        }
        var bad = dir.resolve("unknown.json");
        ObjectNode tree = SixMaxTexturePayoffTable.mapper().valueToTree(review);
        tree.put("qualified", true);
        Files.writeString(bad, tree.toPrettyString());
        assertThrows(Exception.class, () -> SixMaxSuppliedIntervalCollection.read(bad));
    }

    static Path copyManifestFiles(Path dir) throws Exception {
        for (var source : manifest.sources()) {
            Files.copy(
                    data("manifest").getParent().resolve(source.requestFile()),
                    dir.resolve(source.requestFile()));
            Files.copy(
                    data("manifest").getParent().resolve(source.reportFile()),
                    dir.resolve(source.reportFile()));
        }
        return dir.resolve("manifest.json");
    }

    @Test
    void loaderRejectsReorderedMissingDuplicateAndChangedSourcePinsBeforePhysicalReplay(
            @TempDir Path dir) throws Exception {
        var path = copyManifestFiles(dir);
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String mutation :
                List.of("order", "missing", "duplicate", "source-pin", "collection-pin")) {
            ObjectNode tree = mapper.valueToTree(manifest);
            var sources = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("sources");
            switch (mutation) {
                case "order" -> {
                    var first = sources.get(0);
                    sources.set(0, sources.get(1));
                    sources.set(1, first);
                }
                case "missing" -> sources.remove(3);
                case "duplicate" -> sources.set(1, sources.get(0));
                case "source-pin" ->
                        ((ObjectNode) sources.get(0)).put("reportHash", "0".repeat(64));
                case "collection-pin" -> tree.put("collectionHash", "0".repeat(64));
            }
            Files.writeString(path, tree.toPrettyString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedIntervalCollectionLoader.load(path, HASH),
                    mutation);
        }
    }

    @Test
    void differentLossLimitsAtTheSameTargetAreNotAdditionalQuestionCoverage() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var source = manifest.sources().getFirst();
        var report =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                data("manifest").getParent().resolve(source.reportFile()),
                                SixMaxSuppliedIntervalFeedback.MAX_BYTES),
                        SixMaxSuppliedIntervalFeedback.Report.class);
        ObjectNode tree = mapper.valueToTree(report);
        ((ObjectNode) tree.get("request")).put("maximumLossBb", .005);
        var altered = mapper.treeToValue(tree, SixMaxSuppliedIntervalFeedback.Report.class);
        assertNotEquals(
                SixMaxHeadsUpPreflopGame.hash(report), SixMaxHeadsUpPreflopGame.hash(altered));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalCollection.identity(List.of(report, altered)));
    }

    @Test
    void manifestPathsAndInventoryAreBounded() {
        for (String bad :
                List.of("../outside.json", "C:\\outside.json", "sub/report.json", ".", ".."))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxSuppliedIntervalCollectionLoader.Source(
                                    bad, "report.json", HASH));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSuppliedIntervalCollectionLoader.Manifest(
                                manifest.schemaVersion(),
                                HASH,
                                List.of(manifest.sources().getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSuppliedIntervalCollectionLoader.Manifest(
                                manifest.schemaVersion(),
                                HASH,
                                Collections.nCopies(17, manifest.sources().getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuppliedIntervalCollection(List.of()));
    }
}
