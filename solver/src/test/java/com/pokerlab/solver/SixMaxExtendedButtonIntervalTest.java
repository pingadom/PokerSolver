package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxExtendedButtonIntervalTest {
    static final String PIN = "776fcc01e70027b5584dbe3232d19e1670bbd8e8e087879ae020c510dccc6a00";
    static final List<String> TAGS = List.of("js", "aks", "qts");
    static SixMaxSuppliedIntervalCollection collection;
    static List<SixMaxSuppliedIntervalFeedback.Report> reports;
    static SixMaxSuppliedRangePreflopGame game;

    static Path data(String name) {
        return SixMaxSuppliedRangePreflopGameTest.data(name);
    }

    @BeforeAll
    static void physicallyReplayAllSevenMixedVersionSources() throws Exception {
        collection =
                SixMaxSuppliedIntervalCollectionLoader.load(
                        data("supplied-seven-question-interval-collection-manifest.json"), PIN);
        var loaded = new ArrayList<SixMaxSuppliedIntervalFeedback.Report>();
        for (var tag : TAGS)
            loaded.add(
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(
                                            data(
                                                    "supplied-button-five-target-"
                                                            + tag
                                                            + "-extended-interval-feedback-report.json.gz"),
                                            SixMaxSuppliedIntervalFeedback.MAX_BYTES),
                                    SixMaxSuppliedIntervalFeedback.Report.class));
        reports = List.copyOf(loaded);
        game = new SixMaxSuppliedRangePreflopGame(reports.getFirst().request().input());
    }

    @Test
    void independentReceiptBindsAllThreePhysicalArtifactsAndOracleSource() throws Exception {
        var directory =
                data("supplied-seven-question-interval-collection-manifest.json").getParent();
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        directory.resolve(
                                                "supplied-button-five-target-extended-feedback-independent-check.json")));
        assertEquals(71, receipt.get("freshLpSolves").intValue());
        assertEquals(0, receipt.get("cachedEndpointCases").intValue());
        assertEquals(3, receipt.get("rows").size());
        assertFalse(receipt.get("trainerAdmission").booleanValue());
        assertFalse(receipt.get("qualificationAttempted").booleanValue());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < TAGS.size(); i++) {
            var row = receipt.get("rows").get(i);
            var path =
                    data(
                            "supplied-button-five-target-"
                                    + TAGS.get(i)
                                    + "-extended-interval-feedback-report.json.gz");
            assertEquals(
                    row.get("storedBytesSha256").textValue(),
                    HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))));
            assertEquals(
                    row.get("expandedBytesSha256").textValue(),
                    HexFormat.of()
                            .formatHex(
                                    digest.digest(
                                            SixMaxRankTexturePayoffTable.readBytes(
                                                    path,
                                                    SixMaxSuppliedIntervalFeedback.MAX_BYTES))));
            assertEquals(
                    reports.get(i).request().informationSet(), row.get("selection").textValue());
            assertEquals(15, row.get("checkedJointWitnesses").intValue());
            assertEquals(2, row.get("recordedReferenceRowsChecked").intValue());
        }
        assertEquals(
                receipt.get("sourceScriptBytesSha256").textValue(),
                HexFormat.of()
                        .formatHex(
                                digest.digest(
                                        Files.readAllBytes(
                                                directory
                                                        .getParent()
                                                        .getParent()
                                                        .resolve(
                                                                "scripts/check-supplied-extended-button-feedback.py")))));
    }

    @Test
    void strongerReferencesFitUnchangedFaceAndAllActionBounds() {
        for (var report : reports) {
            assertTrue(report.qualifiedForIntervalFeedback());
            assertFalse(report.trainerAdmission());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.EXTENDED_REPORT_SCHEMA, report.schemaVersion());
            assertEquals(.0001, report.request().securitySlack());
            assertEquals(.01, report.request().minimumReach());
            assertEquals(.01, report.request().maximumLossBb());
            assertEquals(
                    List.of(2000, 4000),
                    report.referenceChecks().stream()
                            .map(SixMaxSuppliedIntervalFeedback.ReferenceCheck::iterations)
                            .toList());
            assertEquals(
                    List.of(2000, 4000),
                    report.legacySelectedDecision().comparisons().stream()
                            .map(SixMaxHeadsUpPreflopStudy.Comparison::iterations)
                            .toList());
            assertTrue(report.legacySelectedDecision().material());
            for (var ref : report.referenceChecks()) {
                assertTrue(ref.insideDeclaredFace());
                assertTrue(ref.boundsChecked());
                assertTrue(ref.failures().isEmpty());
                assertTrue(ref.inclusionMarginBb() > 0);
                assertTrue(ref.maximumBoundViolationBb() <= 1e-8);
                assertTrue(ref.quality().nashConvBb() <= .001);
            }
        }
    }

    @Test
    void oldFormatStillRejectsTheseQuestionsWithItsOriginalReferenceBudgets() throws Exception {
        for (var report : reports) {
            var r = report.request();
            var old =
                    SixMaxSuppliedIntervalFeedback.solve(
                            game,
                            new SixMaxSuppliedIntervalFeedback.Request(
                                    SixMaxSuppliedIntervalFeedback.L1_REQUEST_SCHEMA,
                                    r.input(),
                                    r.hero(),
                                    r.informationSet(),
                                    r.securitySlack(),
                                    r.minimumReach(),
                                    r.maximumLossBb()));
            assertFalse(old.report().qualifiedForIntervalFeedback());
            assertTrue(old.qualifiedFeedback().isEmpty());
            assertEquals(
                    List.of(
                            "1000:REFERENCE_OUTSIDE_DECLARED_FACE",
                            "500:REFERENCE_OUTSIDE_DECLARED_FACE"),
                    old.report().rejectionReasons());
            assertEquals(
                    List.of(500, 1000),
                    old.report().referenceChecks().stream()
                            .map(SixMaxSuppliedIntervalFeedback.ReferenceCheck::iterations)
                            .toList());
            assertEquals(report.diagnostic(), old.report().diagnostic());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedIntervalCollection.identity(List.of(report, old.report())));
        }
        assertEquals(List.of(500, 1000), SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS);
        assertThrows(
                UnsupportedOperationException.class,
                () -> SixMaxHeadsUpDecisionScreen.ReferenceBudget.EXTENDED.iterations.add(8000));
    }

    @Test
    void strongerReferencesDoNotBypassPrimaryReachGate() throws Exception {
        var r = reports.getFirst().request();
        var result =
                SixMaxSuppliedIntervalFeedback.solve(
                        game,
                        new SixMaxSuppliedIntervalFeedback.Request(
                                r.schemaVersion(),
                                r.input(),
                                5,
                                "5:supplied-conditional-preflop:7d 7h:|BB:raise:9.0|BTN:raise:22.0",
                                r.securitySlack(),
                                r.minimumReach(),
                                r.maximumLossBb()));
        assertFalse(result.report().qualifiedForIntervalFeedback());
        assertTrue(result.qualifiedFeedback().isEmpty());
        assertEquals(
                "ZERO_POLICY_REACH", result.report().legacySelectedDecision().primary().status());
        assertTrue(result.report().rejectionReasons().contains("NON_MATERIAL_SELECTED_DECISION"));
    }

    @Test
    void reportVersionCannotMisrepresentItsReferenceBudgets() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode tree = mapper.valueToTree(reports.getFirst());
        ((ObjectNode) tree.get("referenceChecks").get(0)).put("iterations", 500);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, SixMaxSuppliedIntervalFeedback.Report.class));
        ObjectNode downgraded = mapper.valueToTree(reports.getFirst());
        downgraded.put("schemaVersion", SixMaxSuppliedIntervalFeedback.L1_REPORT_SCHEMA);
        ((ObjectNode) downgraded.get("request"))
                .put("schemaVersion", SixMaxSuppliedIntervalFeedback.L1_REQUEST_SCHEMA);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(downgraded, SixMaxSuppliedIntervalFeedback.Report.class));
    }

    @Test
    void sevenQuestionInventorySwitchesHeroSeatAndGradesBothPositions() {
        assertEquals(7, collection.questions().size());
        for (int i = 0; i < 7; i++) {
            var q = collection.question(i);
            assertEquals(
                    i < 4 ? PreflopAllInSpot.Seat.BB : PreflopAllInSpot.Seat.BTN,
                    q.decision().hero());
            assertEquals(q.decision().hero(), q.decision().table().actingSeat());
            assertEquals(7, q.totalQuestions());
        }
        for (int i = 4; i < 7; i++) {
            var q = collection.question(i);
            var action = i == 4 ? "call" : "raise:22.0";
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    collection.grade(q, action).decision().selected().classification());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                    collection.grade(q, "fold").decision().selected().classification());
        }
    }

    @Test
    void savedReviewReplaysMixedFormatsAndDistinctCoverage(@TempDir Path temp) throws Exception {
        var saved =
                SixMaxSuppliedIntervalCollection.read(
                        data("supplied-seven-question-interval-collection-review.json"));
        assertEquals(new SixMaxSuppliedIntervalCollection.Counts(7, 6, 1, 1), saved.counts());
        assertEquals(saved, collection.replay(saved));
        var output = temp.resolve("review.json.gz");
        collection.write(output, saved);
        assertEquals(saved, collection.replay(SixMaxSuppliedIntervalCollection.read(output)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.review(
                                List.of(
                                        new SixMaxSuppliedIntervalCollection.Choice(
                                                4,
                                                collection.question(5).decision().reportHash(),
                                                "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        collection.replay(
                                SixMaxSuppliedIntervalCollection.read(
                                        data(
                                                "supplied-four-question-interval-collection-review.json"))));
    }
}
