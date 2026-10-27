package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigBlindPotSizeDrillTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-unpaired-bb-pot-size-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-unpaired-bb-pot-size-feedback-report.json.gz");
    static final Path REVIEW = Path.of("../docs/data/supplied-unpaired-bb-pot-size-review.json");

    @Test
    void freshQualificationServesOnlyTwoCorrectlyPricedQuestionsAndCachedGrades(@TempDir Path temp)
            throws Exception {
        var owned = SixMaxBigBlindPairwiseFeedback.replay(REQUEST, REPORT);
        assertEquals(List.of(3, 5), owned.report().qualifiedIndices());
        for (int i = 0; i < 2; i++) {
            var q = SixMaxBigBlindPairwiseDrill.question(owned, i);
            assertEquals(i == 0 ? "9d 9h" : "Ac Jc", q.heroCards());
            assertEquals(i == 0 ? 3 : 5, q.sourceIndex());
            assertEquals("BB", q.table().actingSeat().name());
            assertEquals(13, q.table().potBb());
            assertEquals(6.5, q.table().toCallBb());
            assertEquals(6.5, q.table().betSizeBb());
            assertEquals(2, q.totalQuestions());
            assertEquals(6, q.table().players().size());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    SixMaxBigBlindPairwiseDrill.grade(owned, i, i == 0 ? "call" : "fold")
                            .selected()
                            .classification());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                    SixMaxBigBlindPairwiseDrill.grade(owned, i, i == 0 ? "fold" : "call")
                            .selected()
                            .classification());
        }
        for (int i : List.of(0, 1, 2, 4))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxBigBlindPairwiseFeedback.grade(owned, i, "call"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBigBlindPairwiseDrill.question(owned, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBigBlindPairwiseDrill.question(owned, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBigBlindPairwiseDrill.grade(owned, 0, "check"));
        var choices = SixMaxBigBlindPairwiseDrill.canonicalChoices(owned);
        var review = SixMaxBigBlindPairwiseDrill.review(owned, choices);
        assertEquals(SixMaxBigBlindPairwiseDrill.read(REVIEW), review);
        assertEquals(new SixMaxBigBlindPairwiseDrill.Summary(4, 2, 2, 2, 0), review.summary());
        SixMaxBigBlindPairwiseDrill.verify(owned, review);
        String json = SixMaxTextureStudy.json(review);
        for (String hidden :
                List.of("\"strategy\"", "originalJointPrior", "dealtCombos", "Ah Kh", "2c 3c"))
            assertFalse(json.contains(hidden));
        for (String name : List.of("review.json", "review.json.gz")) {
            var path = temp.resolve(name);
            SixMaxBigBlindPairwiseDrill.write(path, owned, choices);
            assertEquals(review, SixMaxBigBlindPairwiseDrill.read(path));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxBigBlindPairwiseDrill.write(path, owned, choices));
        }
        var maximum = Collections.nCopies(100, new SixMaxBigBlindPairwiseDrill.Choice(0, "call"));
        assertEquals(
                100, SixMaxBigBlindPairwiseDrill.review(owned, maximum).summary().withinLimit());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseDrill.review(
                                owned, Collections.nCopies(101, maximum.getFirst())));
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String mutation : List.of("ev", "price", "pin")) {
            var node = (ObjectNode) mapper.valueToTree(review);
            if (mutation.equals("ev"))
                ((ObjectNode) node.path("attempts").get(0).path("feedback").path("selected"))
                        .put("lowerEvBb", 0);
            if (mutation.equals("price"))
                ((ObjectNode)
                                node.path("attempts")
                                        .get(0)
                                        .path("feedback")
                                        .path("question")
                                        .path("table"))
                        .put("betSizeBb", 3.25);
            if (mutation.equals("pin")) {
                node.put("reportHash", "0".repeat(64));
                for (var attempt : node.path("attempts"))
                    ((ObjectNode) attempt.path("feedback").path("question"))
                            .put("reportHash", "0".repeat(64));
            }
            var edited = mapper.treeToValue(node, SixMaxBigBlindPairwiseDrill.Review.class);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxBigBlindPairwiseDrill.verify(owned, edited),
                    mutation);
        }
    }

    @Test
    void reviewCapsStrictJsonAndCountsReject(@TempDir Path temp) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved = SixMaxBigBlindPairwiseDrill.read(REVIEW);
        var node = (ObjectNode) mapper.valueToTree(saved);
        ((ObjectNode) node.path("summary")).put("withinLimit", 4);
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.treeToValue(node, SixMaxBigBlindPairwiseDrill.Review.class));
        String text = Files.readString(REVIEW);
        for (String changed :
                List.of(
                        text + "{}",
                        text.replace("\"schemaVersion\"", "\"unexpected\""),
                        text.replace(
                                "\"trainerAdmission\" : false", "\"trainerAdmission\" : true"))) {
            var p = temp.resolve(UUID.randomUUID() + ".json");
            Files.writeString(p, changed);
            assertThrows(Exception.class, () -> SixMaxBigBlindPairwiseDrill.read(p));
        }
        var huge = temp.resolve("huge.json");
        Files.write(huge, new byte[SixMaxBigBlindPairwiseDrill.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> SixMaxBigBlindPairwiseDrill.read(huge));
    }

    @Test
    void reviewCliGuardsOutputsChoicesAndAliasesBeforeFreshTraining(@TempDir Path temp)
            throws Exception {
        var report = temp.resolve("new-report.json.gz");
        var review = temp.resolve("keep.json");
        Files.writeString(review, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseFeedbackMain.main(
                                new String[] {
                                    "solve-review",
                                    REQUEST.toString(),
                                    report.toString(),
                                    review.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseFeedbackMain.main(
                                new String[] {
                                    "replay-review",
                                    REQUEST.toString(),
                                    REPORT.toString(),
                                    REPORT.toString()
                                }));
        var fresh = temp.resolve("new-review.json");
        for (String bad : List.of("-1:call", "7:call", "x:fold", "0:raise", "0:call:fold"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxBigBlindPairwiseFeedbackMain.main(
                                    new String[] {
                                        "replay-review",
                                        REQUEST.toString(),
                                        REPORT.toString(),
                                        fresh.toString(),
                                        bad
                                    }));
        assertEquals("keep", Files.readString(review));
        assertFalse(Files.exists(report));
        assertFalse(Files.exists(fresh));
    }

    @Test
    void qualifiedSourceKeepsAllSixOutcomesAndTheMatchingScreen() throws Exception {
        var r = SixMaxBigBlindPairwiseFeedback.readReport(REPORT);
        assertFalse(r.trainerAdmission());
        assertEquals(6, r.cases().size());
        assertEquals(
                SixMaxBigBlindPrimaryScreen.readReport(BigBlindPrimaryScreenTest.report("pot-size"))
                        .study(),
                r.study());
        assertEquals(
                List.of(80000, 160000),
                r.references().stream().map(x -> x.policy().iterations()).toList());
        for (var c : r.cases())
            assertEquals(c.index() == 3 || c.index() == 5, c.qualifiedForOfflineDrill());
    }
}
