package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedIntervalTrainerTest {
    static SixMaxSuppliedIntervalFeedback.Result owned;
    static SixMaxSuppliedIntervalTrainer trainer;

    @BeforeAll
    static void solve() throws Exception {
        owned =
                SixMaxSuppliedIntervalFeedback.solve(
                        SixMaxSuppliedIntervalFeedback.readRequest(
                                SixMaxSuppliedIntervalFeedbackTest.data("request")));
        trainer = new SixMaxSuppliedIntervalTrainer(owned);
    }

    @Test
    void actualSixSeatSituationIncludesSourceHistoryDeadBlindAndOnlyHeroCards() throws Exception {
        var q = trainer.question();
        assertEquals(Seat.BB, q.hero());
        assertEquals("9d 9h", q.heroCards());
        assertEquals(owned.report().request().informationSet(), q.informationSet());
        assertEquals(SixMaxHeadsUpPreflopGame.hash(owned.report()), q.reportHash());
        assertEquals(7, q.history().size());
        assertEquals(
                List.of("fold", "fold", "fold", "raise:3.0", "fold", "raise:9.0", "raise:22.0"),
                q.history().stream()
                        .map(SixMaxPreflopResearchTrainer.PublicAction::action)
                        .toList());
        assertEquals(
                SixMaxPreflopPublicTable.replay(
                        owned.report().request().input().specification().rules(), q.history()),
                q.table());
        assertEquals(Seat.BB, q.table().actingSeat());
        assertEquals(31.5, q.table().potBb());
        assertEquals(13, q.table().toCallBb());
        assertEquals(6, q.table().players().size());
        assertEquals(
                4,
                q.table().players().stream()
                        .filter(p -> p.status() == SixMaxPreflopPublicTable.PlayerStatus.FOLDED)
                        .count());
        var bb = q.table().players().get(Seat.BB.ordinal());
        var btn = q.table().players().get(Seat.BTN.ordinal());
        assertEquals(SixMaxPreflopPublicTable.PlayerStatus.ACTING, bb.status());
        assertEquals(9, bb.committedBb());
        assertEquals(91, bb.remainingStackBb());
        assertEquals(SixMaxPreflopPublicTable.PlayerStatus.ACTIVE, btn.status());
        assertEquals(22, btn.committedBb());
        assertEquals(78, btn.remainingStackBb());
        assertEquals(.5, q.table().players().get(Seat.SB.ordinal()).committedBb());
        assertEquals(List.of("fold", "call", "raise:50.0"), q.legalActions());
        var json = SixMaxTextureStudy.json(q);
        for (String field :
                List.of(
                        "prior",
                        "payoffs",
                        "posterior",
                        "dealtCombos",
                        "diagnostic",
                        "solutionHash",
                        "responseActions")) assertFalse(json.contains('"' + field + '"'), field);
        for (var world : owned.report().request().input().worlds())
            for (int i = 0; i < Seat.values().length; i++)
                if (i != q.hero().ordinal()) assertFalse(json.contains(world.dealtCombos().get(i)));
    }

    @Test
    void threeGradesRetainOriginalIntervalsAndAllAlternativesWithoutScalarScores()
            throws Exception {
        var q = trainer.question();
        assertEquals(SixMaxSuppliedIntervalTrainer.SELECTION, q.selection());
        assertEquals(SixMaxSuppliedIntervalTrainer.STATUS, q.publicationStatus());
        assertFalse(q.trainerAdmission());
        assertEquals(SixMaxSuppliedRangePreflopGame.MODEL, q.model());
        assertEquals(SixMaxSuppliedRangePreflopGame.BELIEFS, q.beliefs());
        assertEquals(SixMaxSuppliedRangePreflopGame.HISTORY_REACH, q.sourceHistoryReachStatus());
        assertEquals(.01, q.maximumLossBb());
        assertEquals(1e-4, q.securitySlackBb());
        assertEquals(.01, q.minimumReach());
        for (int i = 0; i < q.legalActions().size(); i++) {
            var feedback = trainer.grade(q, q.legalActions().get(i));
            assertEquals(owned.report().moves().get(i), feedback.selected());
            assertEquals(owned.report().moves(), feedback.alternatives());
            assertFalse(feedback.trainerAdmission());
            assertEquals(q.reportHash(), feedback.reportHash());
            assertEquals(q.publicationStatus(), feedback.publicationStatus());
            assertEquals(q.maximumLossBb(), feedback.maximumLossBb());
            assertEquals(SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE, feedback.evScope());
            var json = SixMaxTextureStudy.json(feedback);
            for (String field : List.of("selectedActionEvBb", "evLossBb", "accuracy", "frequency"))
                assertFalse(json.contains('"' + field + '"'));
        }
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                trainer.grade(q, "raise:50.0").selected().classification());
    }

    @Test
    void everyQuestionFieldAndNestedTableHistoryAreBoundToOwnedQualification() throws Exception {
        List<Consumer<ObjectNode>> changes =
                List.of(
                        t -> t.put("reportHash", "0".repeat(64)),
                        t -> t.put("publicationStatus", "OTHER"),
                        t -> t.put("trainerAdmission", true),
                        t -> t.put("selection", "UNIFORM"),
                        t -> t.put("model", "OTHER"),
                        t -> t.put("beliefs", "OTHER"),
                        t -> t.put("sourceHistoryReachStatus", "AVAILABLE"),
                        t -> t.put("informationSet", "other"),
                        t -> t.put("hero", "BTN"),
                        t -> t.put("heroCards", "7d 7h"),
                        t -> t.putArray("history"),
                        t -> ((ObjectNode) t.path("table")).put("potBb", 32),
                        t ->
                                ((ObjectNode) t.path("table").path("players").get(0))
                                        .put("remainingStackBb", 99),
                        t -> t.putArray("legalActions").add("fold").add("call"),
                        t -> t.put("maximumLossBb", .02),
                        t -> t.put("securitySlackBb", .001),
                        t -> t.put("minimumReach", .1));
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (var change : changes) {
            ObjectNode tree = mapper.valueToTree(trainer.question());
            change.accept(tree);
            var altered = mapper.treeToValue(tree, SixMaxSuppliedIntervalTrainer.Question.class);
            assertThrows(IllegalArgumentException.class, () -> trainer.grade(altered, "call"));
            assertThrows(IllegalArgumentException.class, () -> trainer.review(altered, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(null, "call"));
        for (String illegal : List.of("raise:100.0", "check", "Call", ""))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> trainer.grade(trainer.question(), illegal));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(trainer.question(), null));
    }

    @Test
    void reviewsCountRepeatedAttemptsAtOneQuestionAndKeepUncertaintySeparate() {
        var review =
                trainer.review(trainer.question(), List.of("fold", "call", "raise:50.0", "call"));
        assertEquals(new SixMaxSuppliedIntervalTrainer.Counts(2, 1, 1), review.counts());
        assertEquals(
                List.of(0, 1, 2, 3),
                review.attempts().stream()
                        .map(SixMaxSuppliedIntervalTrainer.Attempt::index)
                        .toList());
        assertEquals(review, trainer.replay(review));
        assertEquals(
                new SixMaxSuppliedIntervalTrainer.Counts(0, 0, 0),
                trainer.review(trainer.question(), List.of()).counts());
        assertThrows(
                IllegalArgumentException.class, () -> trainer.review(trainer.question(), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(trainer.question(), Arrays.asList("call", null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(trainer.question(), Collections.nCopies(101, "call")));
    }

    @Test
    void alteredSavedActionsGradesRangesCountsIndicesAndAlternativesFailReplay() throws Exception {
        List<Consumer<ObjectNode>> changes =
                List.of(
                        t -> ((ObjectNode) t.path("attempts").get(0)).put("action", "call"),
                        t -> ((ObjectNode) t.path("attempts").get(0)).put("index", 4),
                        t -> ((ObjectNode) t.path("counts")).put("outsideLimit", 0),
                        t ->
                                ((ObjectNode) t.path("attempts").get(0).path("feedback"))
                                        .put("reportHash", "0".repeat(64)),
                        t ->
                                ((ObjectNode) t.path("attempts").get(0).path("feedback"))
                                        .put("trainerAdmission", true),
                        t ->
                                ((ObjectNode) t.path("attempts").get(0).path("feedback"))
                                        .put("evScope", "OTHER"),
                        t ->
                                ((ObjectNode) t.path("attempts").get(0).path("feedback"))
                                        .put("maximumLossBb", .02),
                        t ->
                                ((ObjectNode)
                                                t.path("attempts")
                                                        .get(0)
                                                        .path("feedback")
                                                        .path("selected"))
                                        .put("lowerLossBb", 0),
                        t ->
                                ((ObjectNode)
                                                t.path("attempts")
                                                        .get(0)
                                                        .path("feedback")
                                                        .path("selected"))
                                        .put("classification", "WITHIN_LIMIT"),
                        t ->
                                ((ObjectNode)
                                                t.path("attempts")
                                                        .get(0)
                                                        .path("feedback")
                                                        .path("alternatives")
                                                        .get(1))
                                        .put("upperEvBb", 100));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var review = trainer.review(trainer.question(), trainer.question().legalActions());
        for (var change : changes) {
            ObjectNode tree = mapper.valueToTree(review);
            change.accept(tree);
            var altered = mapper.treeToValue(tree, SixMaxSuppliedIntervalTrainer.Review.class);
            assertThrows(IllegalArgumentException.class, () -> trainer.replay(altered));
        }
    }

    @Test
    void savedSampleReproducesFromUnchangedFullReportAndCachedOwnedContext() throws Exception {
        var savedReport =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Files.readAllBytes(
                                        SixMaxSuppliedIntervalFeedbackTest.data("report")),
                                SixMaxSuppliedIntervalFeedback.Report.class);
        assertEquals(savedReport, owned.report());
        assertEquals(
                trainer.review(trainer.question(), trainer.question().legalActions()),
                trainer.replay(
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-conditional-five-target-99-interval-drill.json")));
        assertEquals(
                List.of(SixMaxSuppliedIntervalFeedback.Result.class),
                Arrays.asList(
                        SixMaxSuppliedIntervalTrainer.class.getConstructors()[0]
                                .getParameterTypes()));
        assertTrue(
                Arrays.stream(SixMaxSuppliedIntervalFeedback.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        // The runtime adapter retains only immutable public question and move evidence, no game,
        // solver, payoff oracle or owned result through which it could start another solve.
        assertEquals(
                Set.of(SixMaxSuppliedIntervalTrainer.Question.class, List.class),
                Arrays.stream(SixMaxSuppliedIntervalTrainer.class.getDeclaredFields())
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .map(f -> f.getType())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void publicSnapshotsAlternativesAndAttemptsAreImmutable() {
        var q = trainer.question();
        assertSame(q, trainer.question());
        assertThrows(UnsupportedOperationException.class, () -> q.history().clear());
        assertThrows(UnsupportedOperationException.class, () -> q.legalActions().clear());
        assertThrows(UnsupportedOperationException.class, () -> q.table().players().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.questionContext().history().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> trainer.grade(q, "call").alternatives().clear());
        var actions = new ArrayList<>(List.of("call"));
        var review = trainer.review(q, actions);
        actions.clear();
        assertEquals(1, review.attempts().size());
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
    }

    @Test
    void jsonAndGzipRoundTripsNeverOverwriteAndHundredAttemptsFitTheByteCap(@TempDir Path dir)
            throws Exception {
        for (String suffix : List.of(".json", ".json.gz")) {
            var path = dir.resolve("review" + suffix);
            var review = trainer.review(trainer.question(), Collections.nCopies(100, "raise:50.0"));
            trainer.write(path, review);
            assertEquals(review, trainer.replay(path));
            assertThrows(FileAlreadyExistsException.class, () -> trainer.write(path, review));
        }
    }

    @Test
    void strictLoadingRejectsUnknownDuplicateTrailingNullCoercedAndOversizedArtifacts(
            @TempDir Path dir) throws Exception {
        String json = SixMaxTextureStudy.json(trainer.review(trainer.question(), List.of("call")));
        var path = dir.resolve("bad.json");
        for (String bad :
                List.of(
                        json + "{}",
                        json.replaceFirst("\\{", "{\"unknown\":true,"),
                        json.replace("\"index\" : 0", "\"index\" : 0, \"index\" : 0"),
                        json.replace("\"index\" : 0", "\"index\" : null"),
                        json.replace("\"index\" : 0", "\"index\" : 0.5"),
                        json.replace(
                                "\"trainerAdmission\" : false",
                                "\"trainerAdmission\" : \"false\""))) {
            assertNotEquals(json, bad);
            Files.writeString(path, bad);
            assertThrows(Exception.class, () -> SixMaxSuppliedIntervalTrainer.read(path));
        }
        byte[] large = new byte[SixMaxSuppliedIntervalTrainer.MAX_BYTES + 1];
        Files.write(path, large);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxSuppliedIntervalTrainer.read(path));
        var gzip = dir.resolve("large.json.gz");
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzip))) {
            zip.write(large);
        }
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxSuppliedIntervalTrainer.read(gzip));
    }

    @Test
    void cliRejectsInvalidModesAliasesExistingOutputsAndAttemptOverflowBeforePhysicalReplay(
            @TempDir Path dir) throws Exception {
        Path missing = dir.resolve("missing"), output = dir.resolve("existing");
        Files.writeString(output, "preserve");
        for (String[] args :
                List.of(
                        new String[0],
                        new String[] {"serve", "a", "b", "c"},
                        new String[] {"replay", "a", "b", "c", "call"},
                        new String[] {
                            "create", missing.toString(), missing.toString(), output.toString()
                        },
                        new String[] {
                            "create",
                            missing.toString(),
                            dir.resolve("report").toString(),
                            output.toString()
                        }))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedIntervalTrainerMain.main(args));
        var overflow = new String[105];
        Arrays.fill(overflow, "call");
        overflow[0] = "create";
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalTrainerMain.main(overflow));
        assertEquals("preserve", Files.readString(output));
    }
}
