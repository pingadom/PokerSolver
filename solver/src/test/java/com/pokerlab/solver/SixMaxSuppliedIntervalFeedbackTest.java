package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedIntervalFeedbackTest {
    static SixMaxSuppliedRangePreflopGame game;
    static SixMaxSuppliedIntervalFeedback.Result result;

    static Path data(String suffix) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-conditional-five-target-99-interval-feedback-" + suffix + ".json");
    }

    @BeforeAll
    static void solve() throws Exception {
        var request = SixMaxSuppliedIntervalFeedback.readRequest(data("request"));
        game = new SixMaxSuppliedRangePreflopGame(request.input());
        result = SixMaxSuppliedIntervalFeedback.solve(game, request);
        assertEquals(
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Files.readAllBytes(data("report")),
                                SixMaxSuppliedIntervalFeedback.Report.class),
                result.report());
    }

    @Test
    void selectedIntervalQuestionQualifiesWhileOldScalarStudyStillRejects() {
        var r = result.report();
        assertTrue(r.qualifiedForIntervalFeedback());
        assertTrue(r.rejectionReasons().isEmpty());
        assertFalse(r.trainerAdmission());
        assertFalse(r.legacySelectedDecision().stable());
        assertTrue(r.legacyWholeStudyRejections().contains("UNSTABLE_MATERIAL_DECISIONS"));
        assertTrue(
                r.legacySelectedDecision().comparisons().stream()
                        .anyMatch(c -> c.failures().contains("ACTION_EV_DRIFT")));
        assertTrue(
                r.legacySelectedDecision().comparisons().stream()
                        .flatMap(c -> c.failures().stream())
                        .allMatch(f -> f.equals("ACTION_EV_DRIFT")));
        assertEquals(
                List.of(
                        SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                        SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT),
                r.moves().stream()
                        .map(SixMaxSuppliedIntervalFeedback.Move::classification)
                        .toList());
        assertEquals(.00045, r.moves().get(1).upperLossBb(), 1e-10);
        assertEquals(.48455764344770685, r.moves().get(2).upperLossBb(), 2e-8);
        assertEquals(r.moves(), result.qualifiedFeedback().orElseThrow().moves());
    }

    @Test
    void exactPhysicalAndSuppliedBeliefLineageRemainExplicit() {
        var r = result.report();
        assertEquals(9, r.heroCommittedBb());
        assertEquals(27, r.prior().size());
        assertEquals(17766216, r.binding().budget().enumeratedBoards());
        assertEquals(
                SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                r.binding().sourceHistoryReachStatus());
        assertEquals(-51, r.diagnostic().upperObjectiveShift());
        assertEquals(25, r.diagnostic().joint().work().intervalLpSolves());
        assertEquals(
                1,
                r.diagnostic().joint().actions().stream()
                        .map(a -> a.interval().securityFaceHash())
                        .distinct()
                        .count());
    }

    @Test
    void bothFreshReferenceStrategiesAreIncludedBeforeAnyEndpointComparison() {
        var r = result.report();
        assertEquals(
                List.of(500, 1000),
                r.referenceChecks().stream()
                        .map(SixMaxSuppliedIntervalFeedback.ReferenceCheck::iterations)
                        .toList());
        for (var ref : r.referenceChecks()) {
            assertTrue(ref.insideDeclaredFace());
            assertTrue(ref.boundsChecked());
            assertEquals(0, ref.maximumBoundViolationBb());
            assertTrue(ref.requiredSecuritySlackBb() > 1e-8);
            assertTrue(ref.requiredSecuritySlackBb() < r.request().securitySlack());
            assertEquals(
                    ref.globalHeroUpperBb()
                            + r.request().securitySlack()
                            - ref.globalHeroBestResponseBb(),
                    ref.inclusionMarginBb());
            assertTrue(ref.failures().isEmpty());
            for (var m : r.moves()) {
                double ev = ref.decision().values().actionEvBb().get(m.action());
                double loss = ref.actionLossBb().get(m.action());
                assertTrue(ev >= m.lowerEvBb() - 1e-8 && ev <= m.upperEvBb() + 1e-8);
                assertTrue(loss >= m.lowerLossBb() - 1e-8 && loss <= m.upperLossBb() + 1e-8);
            }
        }
    }

    static SixMaxSuppliedIntervalFeedback.Request limits(double slack, double limit) {
        var r = result.report().request();
        return new SixMaxSuppliedIntervalFeedback.Request(
                r.schemaVersion(),
                r.input(),
                r.hero(),
                r.informationSet(),
                slack,
                r.minimumReach(),
                limit);
    }

    @Test
    void nonMaterialSelectedQuestionCannotAcquireFeedbackEvenWithCertifiedIntervals()
            throws Exception {
        var r = result.report().request();
        var request =
                new SixMaxSuppliedIntervalFeedback.Request(
                        r.schemaVersion(),
                        r.input(),
                        r.hero(),
                        r.informationSet().replace("9d 9h", "7d 7h"),
                        r.securitySlack(),
                        r.minimumReach(),
                        r.maximumLossBb());
        var other = SixMaxSuppliedIntervalFeedback.solve(game, request);
        assertFalse(other.report().legacySelectedDecision().material());
        assertTrue(other.report().rejectionReasons().contains("NON_MATERIAL_SELECTED_DECISION"));
        assertTrue(other.qualifiedFeedback().isEmpty());
    }

    @Test
    void referencesOutsideNarrowerFaceCannotProduceQualifiedFeedbackOrFakeBoundChecks()
            throws Exception {
        var narrow = SixMaxSuppliedIntervalFeedback.solve(game, limits(1e-8, .01));
        assertFalse(narrow.report().qualifiedForIntervalFeedback());
        assertTrue(narrow.qualifiedFeedback().isEmpty());
        for (var ref : narrow.report().referenceChecks()) {
            assertFalse(ref.insideDeclaredFace());
            assertFalse(ref.boundsChecked());
            assertNull(ref.maximumBoundViolationBb());
            assertTrue(ref.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        }
    }

    @Test
    void stricterEducationalLimitCannotBeSilentlyRelaxedToCertifyCall() throws Exception {
        var strict = SixMaxSuppliedIntervalFeedback.solve(game, limits(1e-4, 1e-5));
        assertTrue(strict.qualifiedFeedback().isEmpty());
        assertTrue(strict.report().rejectionReasons().contains("NO_ROBUST_MOVE_WITHIN_LIMIT"));
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                strict.report().moves().get(1).classification());
        assertThrows(IllegalArgumentException.class, () -> limits(1e-4, .02));
    }

    @Test
    void classificationHandlesBoundaryAndAmbiguityWithoutNumericalToleranceInTheLossLimit() {
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                SixMaxSuppliedIntervalFeedback.classify(0, .01, .01));
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                SixMaxSuppliedIntervalFeedback.classify(.01, .02, .01));
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                SixMaxSuppliedIntervalFeedback.classify(Math.nextUp(.01), .02, .01));
        for (double v : List.of(Double.NaN, Double.POSITIVE_INFINITY, -.01))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedIntervalFeedback.classify(v, .01, .01));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalFeedback.classify(.02, .01, .01));
    }

    @Test
    void gzipReplayRecomputesQualificationAndCapabilityIsOpaqueAndImmutable(@TempDir Path dir)
            throws Exception {
        Path gzip = dir.resolve("feedback.json.gz");
        SixMaxSuppliedIntervalFeedback.write(gzip, result);
        var replay = SixMaxSuppliedIntervalFeedback.replay(data("request"), gzip);
        assertEquals(result.report(), replay.report());
        assertEquals(result.report().request(), replay.qualifiedFeedback().orElseThrow().request());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(result.report()),
                replay.qualifiedFeedback().orElseThrow().reportHash());
        assertEquals(
                result.qualifiedFeedback().orElseThrow().moves(),
                replay.qualifiedFeedback().orElseThrow().moves());
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedIntervalFeedback.write(gzip, result));
        assertThrows(
                UnsupportedOperationException.class,
                () -> replay.qualifiedFeedback().orElseThrow().moves().clear());
        for (var type :
                List.of(
                        SixMaxSuppliedIntervalFeedback.Result.class,
                        SixMaxSuppliedIntervalFeedback.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void tamperedQualificationReferenceAndShiftCannotSurviveFullReplay(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        ((ObjectNode) tree.path("diagnostic")).put("upperObjectiveShift", -100);
        ((ObjectNode) tree.path("referenceChecks").get(0)).put("globalHeroBestResponseBb", -999);
        ((ObjectNode) tree.path("moves").get(2)).put("classification", "WITHIN_LIMIT");
        Path poisoned = dir.resolve("poisoned.json");
        Files.writeString(poisoned, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxSuppliedIntervalFeedback.replay(data("request"), poisoned));
        assertTrue(error.getMessage().contains("Exact interval feedback replay differs"));
    }

    @Test
    void strictRequestsLineageAndCliPathsRejectBeforeSolving(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report().request());
        tree.put("callerQualification", true);
        Path request = dir.resolve("request.json");
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(Exception.class, () -> SixMaxSuppliedIntervalFeedback.readRequest(request));
        tree.remove("callerQualification");
        tree.put("maximumLossBb", .005);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalFeedback.replay(request, data("report")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalFeedbackMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedIntervalFeedbackMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("request").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedIntervalFeedbackMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("report").toString()
                                }));
    }

    @Test
    void rehashedShowdownCountsStillRequirePhysicalBoardReenumeration(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        var counts = (ObjectNode) tree.path("payoffs").get(0).path("counts");
        counts.put("firstWins", counts.path("firstWins").asLong() + 1);
        counts.put("secondWins", counts.path("secondWins").asLong() - 1);
        var payoffs =
                mapper.convertValue(
                        tree.path("payoffs"),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                List<SixMaxSuppliedRangePreflopGame.Payoff>>() {});
        ((ObjectNode) tree.path("binding"))
                .put("payoffHash", SixMaxHeadsUpPreflopGame.hash(payoffs));
        Path poisoned = dir.resolve("counts.json");
        Files.writeString(poisoned, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxSuppliedIntervalFeedback.replay(data("request"), poisoned));
        assertTrue(error.getMessage().contains("Exact interval feedback replay differs"));
    }
}
