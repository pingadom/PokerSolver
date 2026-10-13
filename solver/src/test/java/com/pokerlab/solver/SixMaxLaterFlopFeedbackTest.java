package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxLaterFlopFeedbackTest {
    static SixMaxLaterFlopFeedback.Request request;
    static SixMaxLaterFlopFeedback.Result result;

    static SixMaxLaterFlopFeedback.Request request(double lossLimit) throws Exception {
        return new SixMaxLaterFlopFeedback.Request(
                SixMaxLaterFlopFeedback.REQUEST_SCHEMA,
                SixMaxSuppliedFlopBettingStudy.readRequest(
                        Path.of(
                                "../docs/data/supplied-four-button-hand-connected-flop-request.json")),
                FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM,
                5,
                "7d 7h",
                "|BB:call",
                List.of("check", "bet"),
                lossLimit);
    }

    @BeforeAll
    static void solve() throws Exception {
        request = request(.01);
        result = SixMaxLaterFlopFeedback.solve(request);
    }

    @Test
    void completeOwnedLaterQuestionQualifiesWithBothLegalActionsAndOriginalParent()
            throws Exception {
        var report = result.report();
        assertTrue(report.qualifiedForOfflineDrill());
        assertTrue(report.rejectionReasons().isEmpty());
        assertFalse(report.trainerAdmission());
        assertTrue(result.qualifiedFeedback().isPresent());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(report),
                result.qualifiedFeedback().orElseThrow().reportHash());
        var original =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-report.json.gz"),
                                        SixMaxSuppliedFlopBettingStudy.MAX_BYTES),
                                SixMaxSuppliedFlopBettingStudy.Report.class);
        assertEquals(original, report.parent());
        assertEquals(9.75, report.context().potBb());
        assertEquals(3.25, report.context().toCallBb());
        assertEquals(
                List.of("check", "bet"),
                report.context().flopHistory().stream().map(m -> m.action()).toList());
        assertEquals(List.of("fold", "call"), report.context().legalActions());
        assertEquals(
                List.of("fold", "call"), report.actions().stream().map(a -> a.action()).toList());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                report.moves().getFirst().classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                report.moves().getLast().classification());
        assertTrue(report.moves().getFirst().upperLossBb() < 1e-7);
        assertTrue(report.moves().getLast().lowerLossBb() > .44);
        for (var action : report.actions()) {
            assertEquals(request.intervalAlgorithm(), action.diagnostic().algorithm());
            assertEquals(
                    BoundedLinearProgram.EXACT_POWER_TWO_ALGORITHM,
                    action.diagnostic().lowerWitness().certificate().algorithm());
        }
    }

    @Test
    void fullPathReachAndFreshReferencesAreExplicitAndInsideTheOriginalFace() {
        var report = result.report();
        assertEquals(
                List.of(2000, 4000),
                report.references().stream().map(r -> r.policy().iterations()).toList());
        assertNotEquals(
                report.references().getFirst().check().solutionHash(),
                report.references().getLast().check().solutionHash());
        var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
        checks.add(report.primary());
        report.references().forEach(r -> checks.add(r.check()));
        for (var check : checks) {
            assertTrue(check.insideDeclaredFace());
            assertTrue(check.boundsChecked());
            assertEquals(0, check.maximumBoundViolationBb());
            assertTrue(check.failures().isEmpty());
            assertEquals(
                    check.actualQuestionReachGivenPhysicalFlop() / 9880,
                    check.actualQuestionRootReach(),
                    1e-20);
            assertEquals(-3, check.actionUtilitiesBb().get("fold"), 1e-12);
            assertEquals(-3.451388888888889, check.actionUtilitiesBb().get("call"), 1e-12);
            assertEquals(.451388888888889, check.actionLossBb().get("call"), 1e-12);
        }
        var fresh = report.references().getFirst().check();
        assertEquals(.3333319836392781, fresh.actualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertEquals(
                .3333332500416459, fresh.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertTrue(
                fresh.actualQuestionReachGivenPhysicalFlop()
                        < fresh.counterfactualQuestionReachGivenPhysicalFlop());
    }

    @Test
    void outsideFacePolicyLeavesBoundsUncheckedAndRetainsBothPathReachUnits() throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var weak = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        var check = SixMaxLaterFlopFeedback.assess(game, request, result.report().actions(), weak);
        assertFalse(check.insideDeclaredFace());
        assertFalse(check.boundsChecked());
        assertNull(check.maximumBoundViolationBb());
        assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        assertTrue(check.failures().contains("SELECTED_DECISION_REGRET"));
        assertEquals(1.0 / 36, check.actualQuestionReachGivenPhysicalFlop(), 1e-12);
        assertEquals(1.0 / 6, check.counterfactualQuestionReachGivenPhysicalFlop(), 1e-12);
    }

    @Test
    void aTighterEducationalLimitCannotManufactureARobustMove() throws Exception {
        var rejected = SixMaxLaterFlopFeedback.solve(request(1e-9));
        assertFalse(rejected.report().qualifiedForOfflineDrill());
        assertTrue(rejected.qualifiedFeedback().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SixMaxLaterFlopTrainer(rejected));
        assertTrue(rejected.report().rejectionReasons().contains("NO_ROBUST_MOVE_WITHIN_LIMIT"));
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                rejected.report().moves().getFirst().classification());
    }

    @Test
    void requestScopeIsExplicitAndUnsupportedHistoriesFailClosed() {
        for (String algorithm :
                List.of(
                        "automatic",
                        FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_ALGORITHM))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxLaterFlopFeedback.Request(
                                    request.schemaVersion(),
                                    request.connected(),
                                    algorithm,
                                    5,
                                    request.heroCards(),
                                    request.publicPreflopHistory(),
                                    request.flopActions(),
                                    .01));
        for (var path : List.of(List.<String>of(), List.of("bet"), List.of("check", "check")))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxLaterFlopFeedback.Request(
                                    request.schemaVersion(),
                                    request.connected(),
                                    request.intervalAlgorithm(),
                                    5,
                                    request.heroCards(),
                                    request.publicPreflopHistory(),
                                    path,
                                    .01));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxLaterFlopFeedback.Request(
                                request.schemaVersion(),
                                request.connected(),
                                request.intervalAlgorithm(),
                                3,
                                request.heroCards(),
                                request.publicPreflopHistory(),
                                request.flopActions(),
                                .01));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxLaterFlopFeedback.Request(
                                request.schemaVersion(),
                                request.connected(),
                                request.intervalAlgorithm(),
                                5,
                                "2c 2d",
                                request.publicPreflopHistory(),
                                request.flopActions(),
                                .01));
        var unknown =
                new SixMaxLaterFlopFeedback.Request(
                        request.schemaVersion(),
                        request.connected(),
                        request.intervalAlgorithm(),
                        5,
                        request.heroCards(),
                        "unknown",
                        request.flopActions(),
                        .01);
        assertThrows(IllegalArgumentException.class, () -> SixMaxLaterFlopFeedback.solve(unknown));
    }

    @Test
    void completePhysicalReplayRejectsChangedContextAndPreservesExclusiveOutput(
            @TempDir Path folder) throws Exception {
        var input = folder.resolve("request.json");
        Files.writeString(input, SixMaxTextureStudy.json(request));
        var report = folder.resolve("report.json.gz");
        SixMaxLaterFlopFeedback.write(report, result);
        byte[] original = Files.readAllBytes(report);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxLaterFlopFeedback.write(report, result));
        assertArrayEquals(original, Files.readAllBytes(report));
        assertEquals(result.report(), SixMaxLaterFlopFeedback.replay(input, report).report());
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(result.report());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("context")).put("toCallBb", 0);
        var changed = folder.resolve("changed.json");
        Files.writeString(changed, tree.toString());
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxLaterFlopFeedback.replay(input, changed));
        assertTrue(failure.getMessage().contains("Physical later feedback replay differs"));
    }

    @Test
    void parsedEvidenceCannotConstructQualificationAuthorityAndCollectionsAreImmutable() {
        for (var type :
                List.of(
                        SixMaxLaterFlopFeedback.Result.class,
                        SixMaxLaterFlopFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> result.report().actions().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> result.report().references().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().primary().actionLossBb().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.flopActions().clear());
    }

    @Test
    void savedReportAndIndependentReceiptBindPhysicalReplayAndCheckerSources() throws Exception {
        var input =
                Path.of(
                        "../docs/data/supplied-four-button-hand-later-bb-flop-feedback-request.json");
        var saved =
                Path.of(
                        "../docs/data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz");
        assertEquals(request, SixMaxLaterFlopFeedback.readRequest(input));
        assertEquals(result.report(), SixMaxLaterFlopFeedback.readReport(saved));
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-later-bb-flop-feedback-independent-check.json")));
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(8, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(result.report()), receipt.get("reportHash").asText());
        assertEquals(sha(input), receipt.get("requestStoredBytesSha256").asText());
        assertEquals(sha(saved), receipt.get("reportStoredBytesSha256").asText());
        assertEquals(
                sha(Path.of("../scripts/check-later-bb-flop-feedback.py")),
                receipt.get("checkerSourceSha256").asText());
        assertEquals(
                sha(Path.of("../scripts/check-supplied-flop-action-bounds.py")),
                receipt.get("boundsHelperSourceSha256").asText());
        assertEquals(
                sha(Path.of("../scripts/check-supplied-connected-flop.py")),
                receipt.get("physicalHelperSourceSha256").asText());
    }

    @Test
    void cachedLaterQuestionGradesOnlyItsOwnedMovesAndExposesPublicHistory() {
        var trainer = new SixMaxLaterFlopTrainer(result);
        var q = trainer.question();
        assertEquals("BB", q.hero());
        assertEquals("7d 7h", q.heroCards());
        assertEquals(result.report().context(), q.context());
        assertEquals(SixMaxSuppliedFlopBettingGame.MODEL, q.model());
        assertEquals(SixMaxSuppliedFlopBettingGame.CHANCE, q.chanceModel());
        assertEquals(SixMaxSuppliedRangePreflopGame.BELIEFS, q.beliefs());
        assertEquals("UNAVAILABLE_NO_SOURCE_POLICY", q.sourceHistoryReachStatus());
        for (String action : q.context().legalActions()) {
            var feedback = trainer.grade(q, action);
            assertEquals(result.report().moves(), feedback.alternatives());
            assertEquals(action, feedback.selected().action());
            assertFalse(feedback.trainerAdmission());
        }
        for (String action : List.of("check", "bet", "CALL", "", "raise:100"))
            assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, action));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, null));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(null, "fold"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> trainer.grade(q, "fold").alternatives().clear());
    }

    @Test
    void exactLaterReviewsRejectAlteredGradesCountsContextAndAttemptOrder(@TempDir Path dir)
            throws Exception {
        var trainer = new SixMaxLaterFlopTrainer(result);
        var q = trainer.question();
        var review = trainer.review(q, List.of("fold", "call"));
        assertEquals(new SixMaxLaterFlopTrainer.Counts(1, 1, 0), review.counts());
        assertEquals(review, trainer.replay(review));
        assertEquals(
                review,
                trainer.replay(
                        Path.of(
                                "../docs/data/supplied-four-button-hand-later-bb-flop-drill.json")));
        assertEquals(0, trainer.review(q, List.of()).attempts().size());
        assertEquals(
                100, trainer.review(q, Collections.nCopies(100, "fold")).counts().withinLimit());
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(q, Collections.nCopies(101, "fold")));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(q, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(q, Arrays.asList("fold", null)));
        assertThrows(UnsupportedOperationException.class, () -> review.attempts().clear());
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("grade", "counts", "context", "pin", "index", "order")) {
            var tree = mapper.valueToTree(review);
            switch (field) {
                case "grade" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        tree.at("/attempts/0/feedback/selected"))
                                .put("upperLossBb", .5);
                case "counts" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("counts"))
                                .put("withinLimit", 2);
                case "context" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        tree.at("/question/context"))
                                .put("toCallBb", 0);
                case "pin" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("question"))
                                .put("reportHash", "0".repeat(64));
                case "index" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/attempts/0"))
                                .put("index", 1);
                case "order" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/attempts/0"))
                                .put("action", "call");
            }
            var changed = mapper.treeToValue(tree, SixMaxLaterFlopTrainer.Review.class);
            assertThrows(IllegalArgumentException.class, () -> trainer.replay(changed), field);
        }
        for (String name : List.of("review.json", "review.json.gz")) {
            var path = dir.resolve(name);
            trainer.write(path, review);
            assertEquals(review, trainer.replay(path));
            assertThrows(FileAlreadyExistsException.class, () -> trainer.write(path, review));
        }
        var oversized = dir.resolve("oversized.json");
        Files.writeString(oversized, " ".repeat(SixMaxLaterFlopTrainer.MAX_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> SixMaxLaterFlopTrainer.read(oversized));
    }

    @Test
    void laterStartupRequiresExactPinAndPhysicalReplayEvenAfterRehashingTampering(@TempDir Path dir)
            throws Exception {
        var input =
                Path.of(
                        "../docs/data/supplied-four-button-hand-later-bb-flop-feedback-request.json");
        var saved =
                Path.of(
                        "../docs/data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz");
        var pin = result.qualifiedFeedback().orElseThrow().reportHash();
        var trainer = SixMaxLaterFlopTrainerLoader.load(input, saved, pin);
        assertEquals(new SixMaxLaterFlopTrainer(result).question(), trainer.question());
        for (String wrong : List.of("bad", "0".repeat(64)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxLaterFlopTrainerLoader.load(input, saved, wrong));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxLaterFlopTrainerLoader.load(saved, saved, pin));
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(result.report());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("context")).put("toCallBb", 0);
        var changed = dir.resolve("changed.json");
        Files.writeString(changed, tree.toString());
        var report = SixMaxLaterFlopFeedback.readReport(changed);
        var changedPin = SixMaxHeadsUpPreflopGame.hash(report);
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxLaterFlopTrainerLoader.load(input, changed, changedPin));
        assertTrue(failure.getMessage().contains("Physical later feedback replay differs"));
    }

    @Test
    void independentLaterDrillReceiptBindsExactQuestionGradesAndCheckerBytes() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-later-bb-flop-drill-independent-check.json")));
        assertEquals(2, receipt.get("attemptsChecked").asInt());
        assertEquals(8, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(3, receipt.get("policiesChecked").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(
                result.qualifiedFeedback().orElseThrow().reportHash(),
                receipt.get("reportHash").asText());
        assertEquals(
                sha(Path.of("../scripts/check-later-bb-flop-drill.py")),
                receipt.get("checkerSourceSha256").asText());
        assertEquals(
                sha(Path.of("../docs/data/supplied-four-button-hand-later-bb-flop-drill.json")),
                receipt.get("drillStoredBytesSha256").asText());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }
}
