package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class QualifiedFlopCollectionIntegrationTest {
    static final String PIN = "57abbb5f5bbc10b2227739299c1eec0f0092a7a136dc80e63c0830865cb7fd87";
    static final Path MANIFEST =
            Path.of("../docs/data/supplied-eleven-question-flop-collection-manifest.json");
    static SixMaxQualifiedFlopCollection collection;
    static SixMaxQualifiedFlopCollection.Review saved;

    @BeforeAll
    static void completelyReplayFiveSourcesOnce() throws Exception {
        collection = SixMaxQualifiedFlopCollectionLoader.load(MANIFEST, PIN);
        saved =
                SixMaxQualifiedFlopCollection.read(
                        Path.of(
                                "../docs/data/supplied-eleven-question-flop-collection-review.json"));
    }

    static SixMaxQualifiedFlopCollection.Choice choice(int index, String action) {
        return new SixMaxQualifiedFlopCollection.Choice(
                index, collection.question(index).decision().reportHash(), action);
    }

    @Test
    void fullCanonicalInventoryAndBothMovesReplayExactlyWithoutSolvingAgain(@TempDir Path folder)
            throws Exception {
        assertEquals(PIN, collection.collectionHash());
        assertEquals(11, collection.questions().size());
        assertEquals(saved, collection.replay(saved));
        assertEquals(22, saved.attempts().size());
        assertEquals(11, saved.counts().distinctQuestionsAttempted());
        for (var attempt : saved.attempts())
            assertEquals(
                    attempt.feedback(),
                    collection.grade(
                            collection.question(attempt.questionIndex()), attempt.action()));
        var weak = collection.question(10);
        assertEquals("2c 3c", weak.decision().weak().heroCards());
        assertEquals(List.of("8d", "Kc", "Kd"), weak.decision().weak().context().board());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                collection.grade(weak, "fold").decision().selected().classification());
        assertTrue(collection.grade(weak, "call").decision().selected().lowerLossBb() > 2.24);
        var output = folder.resolve("review.json");
        collection.write(output, saved);
        assertEquals(saved, collection.replay(SixMaxQualifiedFlopCollection.read(output)));
        assertThrows(FileAlreadyExistsException.class, () -> collection.write(output, saved));
    }

    @Test
    void zeroAndRepeatedChoicesDoNotInflateDistinctCoverage() {
        assertEquals(0, collection.review(List.of()).counts().distinctQuestionsAttempted());
        var review = collection.review(Collections.nCopies(100, choice(10, "fold")));
        assertEquals(new SixMaxQualifiedFlopCollection.Counts(1, 100, 0, 0), review.counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice(10, "fold"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(10), "check"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxQualifiedFlopCollection.Choice(
                                                10, "0".repeat(64), "fold"))));
        assertThrows(IllegalArgumentException.class, () -> collection.question(11));
    }

    @Test
    void forgedQuestionOrReviewCannotCreateGradingAuthority() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var q = (ObjectNode) mapper.valueToTree(collection.question(10));
        ((ObjectNode) q.path("decision").path("weak")).put("heroCards", "As Qs");
        var foreign = mapper.treeToValue(q, SixMaxQualifiedFlopCollection.Question.class);
        assertThrows(IllegalArgumentException.class, () -> collection.grade(foreign, "fold"));
        for (String mutation : List.of("ev", "counts", "order", "pin", "range")) {
            var node = (ObjectNode) mapper.valueToTree(saved);
            switch (mutation) {
                case "ev" ->
                        ((ObjectNode)
                                        node.path("attempts")
                                                .get(20)
                                                .path("feedback")
                                                .path("decision")
                                                .path("weak")
                                                .path("selected"))
                                .put("upperEvBb", 100);
                case "counts" ->
                        ((ObjectNode) node.path("counts")).put("distinctQuestionsAttempted", 22);
                case "order" -> ((ObjectNode) node.path("attempts").get(0)).put("index", 1);
                case "pin" -> node.put("collectionHash", "0".repeat(64));
                case "range" ->
                        ((ObjectNode) node.path("questions").get(10).path("decision").path("weak"))
                                .put("beliefs", "same-ranges");
            }
            var changed = mapper.treeToValue(node, SixMaxQualifiedFlopCollection.Review.class);
            assertThrows(
                    IllegalArgumentException.class, () -> collection.replay(changed), mutation);
        }
    }

    @Test
    void preflightRejectsChangedRangeIdentityBudgetAndInputOutputAliases(@TempDir Path folder)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String mutation : List.of("pin", "input", "budget", "path", "unknown")) {
            var node = (ObjectNode) mapper.readTree(Files.readString(MANIFEST));
            switch (mutation) {
                case "pin" -> node.put("weakReportHash", "0".repeat(64));
                case "input" -> node.put("weakInputHash", node.path("legacyInputHash").asText());
                case "budget" ->
                        ((ObjectNode) node.path("startupBudget")).put("freshIterations", 731999);
                case "path" -> node.put("legacyManifestFile", "../foreign.json");
                case "unknown" -> node.put("qualified", true);
            }
            Path file = folder.resolve(mutation + ".json");
            Files.writeString(file, node.toString());
            assertThrows(
                    Exception.class,
                    () -> SixMaxQualifiedFlopCollectionLoader.load(file, PIN),
                    mutation);
        }
        var files =
                SixMaxQualifiedFlopCollectionLoader.dependencies(
                        MANIFEST, SixMaxQualifiedFlopCollectionLoader.readManifest(MANIFEST));
        assertEquals(12, files.size());
        for (var file : files)
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxQualifiedFlopCollectionLoader.distinctOutput(MANIFEST, file));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxQualifiedFlopCollectionLoader.load(MANIFEST, "0".repeat(64)));
    }
}
