package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizingFlopCollectionIntegrationTest {
    static final Path MANIFEST =
            Path.of("../docs/data/supplied-twenty-question-flop-collection-manifest.json");
    static final Path REVIEW =
            Path.of("../docs/data/supplied-twenty-question-flop-collection-review.json");
    static final String PIN = "eeeadc2a2addf1e6878af533fe51251a42f3f2afa2f1a1fb7f0dd6e6bb01b9a5";
    static SixMaxSizingFlopCollection collection;

    @BeforeAll
    static void completeEightSourceStartup() throws Exception {
        // One declared startup: 8 sources, 16 fresh references, 1,452,000 iterations.
        collection = SixMaxSizingFlopCollectionLoader.load(MANIFEST, PIN);
    }

    @Test
    void allTwentyQuestionsPreserveOldInventoryAndSeparatePotSizeGame() throws Exception {
        var old =
                SixMaxUnpairedFlopCollection.read(
                        Path.of(
                                "../docs/data/supplied-eighteen-question-flop-collection-review.json"));
        assertEquals(20, collection.questions().size());
        for (int i = 0; i < 20; i++) {
            var q = collection.question(i);
            assertEquals(i, q.index());
            assertEquals(20, q.totalQuestions());
            assertEquals(i < 18 ? i : i - 18, q.sourceQuestionIndex());
            if (i < 18) {
                assertEquals(old.questions().get(i), q.decision().legacy());
                assertNull(q.decision().pot());
            } else {
                assertNull(q.decision().legacy());
                var pot = q.decision().pot();
                assertEquals(i == 18 ? 3 : 5, pot.sourceIndex());
                assertEquals(i == 18 ? "9d 9h" : "Ac Jc", pot.heroCards());
                assertEquals(13, pot.table().potBb());
                assertEquals(6.5, pot.table().toCallBb());
                assertEquals(6.5, pot.table().betSizeBb());
                assertEquals(6, pot.table().players().size());
                assertEquals(SixMaxSizingFlopCollection.POT_HASH, pot.reportHash());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> collection.questions().clear());
        assertThrows(IllegalArgumentException.class, () -> collection.question(20));
        assertThrows(IllegalArgumentException.class, () -> collection.question(-1));
    }

    @Test
    void everyLegalGradeAndExportMatchesTheCanonicalOwnedReview(@TempDir Path temp)
            throws Exception {
        var saved = SixMaxSizingFlopCollection.read(REVIEW);
        assertEquals(saved, collection.replay(saved));
        assertEquals(new SixMaxSizingFlopCollection.Counts(20, 20, 20, 0), saved.counts());
        var robust = new TreeMap<String, Integer>();
        for (var a : saved.attempts()) {
            assertEquals(
                    a.feedback(),
                    collection.grade(collection.question(a.questionIndex()), a.action()));
            if (a.feedback().decision().selected().classification()
                    == SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT)
                robust.merge(a.action(), 1, Integer::sum);
        }
        assertEquals(Map.of("fold", 4, "check", 1, "call", 11, "bet", 4), robust);
        var path = temp.resolve("review.json");
        collection.write(path, saved);
        assertArrayEquals(Files.readAllBytes(REVIEW), Files.readAllBytes(path));
        assertThrows(FileAlreadyExistsException.class, () -> collection.write(path, saved));
    }

    @Test
    void attemptsHaveSeparateCoverageAndNoForeignAuthority() {
        var choice =
                new SixMaxSizingFlopCollection.Choice(
                        18, SixMaxSizingFlopCollection.POT_HASH, "call");
        assertEquals(
                new SixMaxSizingFlopCollection.Counts(1, 100, 0, 0),
                collection.review(Collections.nCopies(100, choice)).counts());
        assertEquals(
                new SixMaxSizingFlopCollection.Counts(0, 0, 0, 0),
                collection.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.review(Collections.nCopies(101, choice)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxSizingFlopCollection.Choice(
                                                18,
                                                SixMaxSizingFlopCollection.LEGACY_HASH,
                                                "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> collection.grade(collection.question(18), "bet"));
    }

    @Test
    void editedQuestionsPricesValuesAndSummariesCannotBecomeGrades() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var node = (ObjectNode) mapper.valueToTree(collection.question(18));
        ((ObjectNode) node.path("decision").path("pot").path("table")).put("toCallBb", 3.25);
        var foreign = mapper.treeToValue(node, SixMaxSizingFlopCollection.Question.class);
        assertThrows(IllegalArgumentException.class, () -> collection.grade(foreign, "call"));
        var saved =
                collection.review(
                        List.of(
                                new SixMaxSizingFlopCollection.Choice(
                                        18, SixMaxSizingFlopCollection.POT_HASH, "fold")));
        for (String mutation : List.of("value", "price", "source-index", "counts", "action")) {
            var tree = (ObjectNode) mapper.valueToTree(saved);
            var feedback = (ObjectNode) tree.path("attempts").get(0).path("feedback");
            if (mutation.equals("value"))
                ((ObjectNode) feedback.path("decision").path("pot").path("selected"))
                        .put("lowerEvBb", 99);
            if (mutation.equals("price"))
                ((ObjectNode)
                                tree.path("questions")
                                        .get(18)
                                        .path("decision")
                                        .path("pot")
                                        .path("table"))
                        .put("potBb", 6.5);
            if (mutation.equals("source-index")) feedback.put("sourceQuestionIndex", 1);
            if (mutation.equals("counts")) ((ObjectNode) tree.path("counts")).put("withinLimit", 1);
            if (mutation.equals("action"))
                ((ObjectNode) tree.path("attempts").get(0)).put("action", "call");
            var changed = mapper.treeToValue(tree, SixMaxSizingFlopCollection.Review.class);
            assertThrows(
                    IllegalArgumentException.class, () -> collection.replay(changed), mutation);
        }
    }

    @Test
    void publicQuestionsAndFeedbackExcludePrivateSourceData() throws Exception {
        var text =
                SixMaxTextureStudy.json(
                        collection.review(
                                List.of(
                                        new SixMaxSizingFlopCollection.Choice(
                                                19, SixMaxSizingFlopCollection.POT_HASH, "fold"))));
        for (String hidden :
                List.of(
                        "originalJointPrior",
                        "dealtCombos",
                        "strategy",
                        "lowerWitness",
                        "candidate",
                        "opponentCards")) assertFalse(text.contains("\"" + hidden + "\""), hidden);
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                collection
                        .grade(collection.question(19), "fold")
                        .decision()
                        .selected()
                        .classification());
    }
}
