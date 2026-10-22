package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SharedFlopFeedbackTest {
    static final Path INPUT = Path.of("../docs/data/supplied-shared-call-flop-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-shared-call-flop-report.json.gz");
    static SixMaxSharedFlopTrainer trainer;

    @BeforeAll
    static void replayEveryCertificateAndBothFreshReferences() throws Exception {
        // One complete real replay for the test class. Cached grading never calls an optimizer.
        var pin = SixMaxHeadsUpPreflopGame.hash(SixMaxSharedFlopFeedback.readReport(REPORT));
        trainer = SixMaxSharedFlopTrainerLoader.load(INPUT, REPORT, pin);
    }

    @Test
    void sourceSharesExactlyTwoReferencesAndKeepsEveryOriginalConditional() throws Exception {
        var saved = SixMaxSharedFlopFeedback.readReport(REPORT);
        assertTrue(saved.qualifiedForOfflineDrill());
        assertFalse(saved.trainerAdmission());
        assertEquals(
                new SixMaxSharedFlopFeedback.Reservation(1, 3, 9, 6, 2, 240000, 9, 0),
                saved.reservation());
        assertEquals(2, saved.references().size());
        var coverage =
                SixMaxRefinedFlopCoverageStudy.readReport(
                        Path.of("../docs/data/supplied-weak-range-flop-coverage-report.json.gz"));
        for (int i = 0; i < 3; i++) {
            var item = saved.cases().get(i);
            assertEquals(coverage.cases().get(2 * i + 3).conditional(), item.conditional());
            assertEquals(
                    List.of(80000, 160000),
                    item.references().stream()
                            .map(SixMaxSuppliedFlopFeedback.Check::iterations)
                            .toList());
            assertTrue(item.rejectionReasons().isEmpty());
            for (int j = 0; j < 2; j++)
                assertEquals(
                        SixMaxConnectedPostflopAudit.solutionHash(
                                saved.references().get(j).policy()),
                        item.references().get(j).solutionHash());
        }
        var old =
                SixMaxRefinedFlopFeedback.readReport(
                        Path.of("../docs/data/supplied-weak-hand-qualified-flop-report.json.gz"));
        for (int j = 0; j < 2; j++) {
            assertEquals(old.references().get(j).policy(), saved.references().get(j).policy());
            assertEquals(old.references().get(j).work(), saved.references().get(j).work());
        }
        assertThrows(UnsupportedOperationException.class, () -> saved.cases().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> saved.request().heroHands().clear());
    }

    @Test
    void preflightRejectsUnboundedDuplicateMissingOrWrongQuestions() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = (ObjectNode) mapper.valueToTree(SixMaxSharedFlopFeedback.readRequest(INPUT));
        for (String kind :
                List.of(
                        "duplicate",
                        "empty",
                        "too-many",
                        "foreign-hand",
                        "wrong-mode",
                        "budget",
                        "threshold",
                        "missing-history")) {
            var tree = original.deepCopy();
            switch (kind) {
                case "duplicate" -> tree.putArray("heroHands").add("Ah Kh").add("Ah Kh");
                case "empty" -> tree.putArray("heroHands");
                case "too-many" ->
                        tree.putArray("heroHands")
                                .add("Ah Kh")
                                .add("As Qs")
                                .add("Qh Th")
                                .add("2c 3c")
                                .add("Ac Ad");
                case "foreign-hand" -> tree.putArray("heroHands").add("Ac Ad");
                case "wrong-mode" ->
                        tree.put(
                                "intervalAlgorithm",
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM);
                case "budget" ->
                        ((ObjectNode) tree.get("referenceBudget")).put("totalIterations", 720000);
                case "threshold" -> tree.put("maximumLossBb", .1);
                case "missing-history" -> tree.remove("publicPreflopHistory");
            }
            assertThrows(
                    com.fasterxml.jackson.databind.JsonMappingException.class,
                    () -> mapper.treeToValue(tree, SixMaxSharedFlopFeedback.Request.class),
                    kind);
        }
        var request = SixMaxSharedFlopFeedback.readRequest(INPUT);
        var unknown =
                new SixMaxSharedFlopFeedback.Request(
                        request.schemaVersion(),
                        request.parent(),
                        request.intervalAlgorithm(),
                        request.heroHands(),
                        "|BB:fold",
                        request.referenceBudget(),
                        request.maximumLossBb());
        assertThrows(IllegalArgumentException.class, () -> SixMaxSharedFlopFeedback.solve(unknown));
    }

    @Test
    void reportsAreDataAndCannotMintOwnedCapability() throws Exception {
        for (var type :
                List.of(
                        SixMaxSharedFlopFeedback.Result.class,
                        SixMaxSharedFlopFeedback.QualifiedBatch.class)) {
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
            assertEquals(0, type.getConstructors().length);
        }
        assertEquals(
                List.of(SixMaxSharedFlopFeedback.Result.class),
                Arrays.asList(
                        SixMaxSharedFlopTrainer.class.getConstructors()[0].getParameterTypes()));
        assertThrows(NullPointerException.class, () -> new SixMaxSharedFlopTrainer(null));
    }

    @Test
    void incompleteReorderedAndMismatchedEvidenceCannotParse() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = (ObjectNode) mapper.valueToTree(SixMaxSharedFlopFeedback.readReport(REPORT));
        for (String kind :
                List.of(
                        "missing",
                        "duplicate",
                        "reorder",
                        "hand",
                        "key",
                        "policy",
                        "missing-check",
                        "work",
                        "rejection",
                        "qualification",
                        "reservation",
                        "action")) {
            var tree = original.deepCopy();
            var cases = (ArrayNode) tree.get("cases");
            var item = (ObjectNode) cases.get(0);
            switch (kind) {
                case "missing" -> cases.remove(2);
                case "duplicate" -> cases.set(1, item.deepCopy());
                case "reorder" -> {
                    var first = cases.get(0);
                    cases.set(0, cases.get(1));
                    cases.set(1, first);
                }
                case "hand" ->
                        ((ObjectNode) item.path("conditional").path("request"))
                                .put("heroCards", "As Qs");
                case "key" ->
                        ((ObjectNode) item.path("conditional")).put("informationSet", "foreign");
                case "policy" ->
                        ((ObjectNode) item.path("references").get(0))
                                .put("solutionHash", "0".repeat(64));
                case "missing-check" -> ((ArrayNode) item.path("references")).remove(1);
                case "work" ->
                        ((ObjectNode) tree.path("references").get(0).path("work"))
                                .put("prunedSuffixRoots", 0);
                case "rejection" ->
                        item.putArray("rejectionReasons").add("PRIMARY:FULL_PARENT_GAP");
                case "qualification" -> tree.put("qualifiedForOfflineDrill", false);
                case "reservation" ->
                        ((ObjectNode) tree.path("reservation")).put("freshReferenceRuns", 6);
                case "action" -> ((ObjectNode) item.path("moves").get(0)).put("action", "raise");
            }
            assertThrows(
                    com.fasterxml.jackson.databind.JsonMappingException.class,
                    () -> mapper.treeToValue(tree, SixMaxSharedFlopFeedback.Report.class),
                    kind);
        }
    }

    @Test
    void cachedGradesCoverAllHandsAndRepeatedAttemptsAreNotExtraCoverage(@TempDir Path temp)
            throws Exception {
        assertEquals(
                List.of("Ah Kh", "As Qs", "Qh Th"),
                trainer.questions().stream().map(q -> q.decision().heroCards()).toList());
        for (var q : trainer.questions()) {
            assertEquals(3, q.totalQuestions());
            assertEquals("BTN", q.decision().hero());
            assertEquals(3.25, q.decision().context().toCallBb());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    trainer.grade(q, "call").selected().classification());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                    trainer.grade(q, "fold").selected().classification());
            assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, "raise"));
        }
        var review =
                trainer.review(
                        List.of(
                                new SixMaxSharedFlopTrainer.Choice(0, "call"),
                                new SixMaxSharedFlopTrainer.Choice(0, "fold"),
                                new SixMaxSharedFlopTrainer.Choice(2, "call")));
        assertEquals(new SixMaxSharedFlopTrainer.Counts(2, 1, 0, 2, 3), review.counts());
        assertEquals(0, trainer.review(List.of()).counts().distinctQuestions());
        assertEquals(
                SixMaxSharedFlopTrainer.read(
                        Path.of("../docs/data/supplied-shared-call-flop-review.json")),
                trainer.review(
                        List.of(
                                new SixMaxSharedFlopTrainer.Choice(0, "fold"),
                                new SixMaxSharedFlopTrainer.Choice(0, "call"),
                                new SixMaxSharedFlopTrainer.Choice(1, "fold"),
                                new SixMaxSharedFlopTrainer.Choice(1, "call"),
                                new SixMaxSharedFlopTrainer.Choice(2, "fold"),
                                new SixMaxSharedFlopTrainer.Choice(2, "call"),
                                new SixMaxSharedFlopTrainer.Choice(0, "call"))));
        assertEquals(
                1,
                trainer.review(
                                Collections.nCopies(
                                        100, new SixMaxSharedFlopTrainer.Choice(1, "call")))
                        .counts()
                        .distinctQuestions());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        trainer.review(
                                Collections.nCopies(
                                        101, new SixMaxSharedFlopTrainer.Choice(1, "call"))));
        assertThrows(IllegalArgumentException.class, () -> trainer.question(-1));
        assertThrows(IllegalArgumentException.class, () -> trainer.question(3));
        var path = temp.resolve("review.json.gz");
        trainer.write(path, review);
        assertEquals(review, trainer.replay(path));
        assertThrows(FileAlreadyExistsException.class, () -> trainer.write(path, review));
    }

    @Test
    void forgedQuestionsAndReviewsCannotSupplyValuesOrMoveBetweenHands() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSharedFlopTrainer.Review(
                                SixMaxSharedFlopTrainer.SCHEMA,
                                trainer.reportHash(),
                                List.of(
                                        new SixMaxSharedFlopTrainer.Attempt(
                                                0,
                                                null,
                                                "call",
                                                trainer.grade(trainer.question(0), "call"))),
                                new SixMaxSharedFlopTrainer.Counts(1, 0, 0, 1, 3)));
        for (String kind : List.of("index", "total", "hand", "pin", "reach", "pot")) {
            var tree = (ObjectNode) mapper.valueToTree(trainer.question(0));
            var d = (ObjectNode) tree.path("decision");
            switch (kind) {
                case "index" -> tree.put("index", 1);
                case "total" -> tree.put("totalQuestions", 2);
                case "hand" -> d.put("heroCards", "As Qs");
                case "pin" -> d.put("reportHash", "0".repeat(64));
                case "reach" -> d.put("minimumReach", 0.);
                case "pot" -> ((ObjectNode) d.path("context")).put("potBb", 100.);
            }
            var changed = mapper.treeToValue(tree, SixMaxSharedFlopTrainer.Question.class);
            assertThrows(
                    IllegalArgumentException.class, () -> trainer.grade(changed, "call"), kind);
        }
        var original =
                (ObjectNode)
                        mapper.valueToTree(
                                trainer.review(
                                        List.of(new SixMaxSharedFlopTrainer.Choice(0, "fold"))));
        for (String kind :
                List.of("counts", "coverage", "grade", "attempt-index", "question", "pin")) {
            var tree = original.deepCopy();
            switch (kind) {
                case "counts" -> ((ObjectNode) tree.path("counts")).put("withinLimit", 99);
                case "coverage" -> ((ObjectNode) tree.path("counts")).put("distinctQuestions", 3);
                case "grade" ->
                        ((ObjectNode)
                                        tree.path("attempts")
                                                .get(0)
                                                .path("feedback")
                                                .path("selected"))
                                .put("lowerEvBb", 99.);
                case "attempt-index" -> ((ObjectNode) tree.path("attempts").get(0)).put("index", 2);
                case "question" ->
                        ((ObjectNode) tree.path("attempts").get(0).path("question"))
                                .put("index", 1);
                case "pin" -> tree.put("reportHash", "0".repeat(64));
            }
            var changed = mapper.treeToValue(tree, SixMaxSharedFlopTrainer.Review.class);
            assertThrows(IllegalArgumentException.class, () -> trainer.replay(changed), kind);
        }
    }

    @Test
    void loaderRejectsWrongPinsAliasesAndOversizeBeforeOptimization(@TempDir Path temp)
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopTrainerLoader.load(INPUT, REPORT, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopTrainerLoader.load(INPUT, REPORT, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopTrainerLoader.load(INPUT, INPUT, trainer.reportHash()));
        var huge = temp.resolve("huge.json");
        Files.write(huge, new byte[SixMaxSharedFlopFeedback.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSharedFlopTrainerLoader.load(INPUT, huge, trainer.reportHash()));
    }

    @Test
    void rehashingAnEditedReportStillRequiresCompleteFreshPhysicalReplay(@TempDir Path temp)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(SixMaxSharedFlopFeedback.readReport(REPORT));
        ((ObjectNode) tree.path("cases").get(0).path("primary").path("actionUtilitiesBb"))
                .put("call", 99.);
        var path = temp.resolve("edited.json");
        Files.writeString(path, tree.toString());
        var pin = SixMaxHeadsUpPreflopGame.hash(SixMaxSharedFlopFeedback.readReport(path));
        assertEquals(
                "Complete shared feedback replay differs",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxSharedFlopTrainerLoader.load(INPUT, path, pin))
                        .getMessage());
    }
}
