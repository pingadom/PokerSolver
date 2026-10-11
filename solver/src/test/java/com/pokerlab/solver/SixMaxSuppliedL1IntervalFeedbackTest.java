package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedL1IntervalFeedbackTest {
    static final List<String> TAGS = List.of("77", "99", "aj");
    static final List<String> HASHES =
            List.of(
                    "ee44745900b245dcdad36e6820b27553dff54c7370ec3dc8093fdf60415de014",
                    "959a0c07629234002d9249045caee0f9974e1468b8e6cfe04b374d5089e21039",
                    "213da44dd7dcad0f073a4e4ee9bbd34434d6cbd7254bf775933da4b57339f643");
    static List<SixMaxSuppliedIntervalFeedback.Result> results;
    static SixMaxSuppliedRangePreflopGame game;

    static Path data(String tag, String suffix) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-root-five-target-" + tag + "-l1-interval-feedback-" + suffix);
    }

    @BeforeAll
    static void physicallyReplayEverySavedQuestion() throws Exception {
        var loaded = new ArrayList<SixMaxSuppliedIntervalFeedback.Result>();
        for (String tag : TAGS)
            loaded.add(
                    SixMaxSuppliedIntervalFeedback.replay(
                            data(tag, "request.json"), data(tag, "report.json.gz")));
        results = List.copyOf(loaded);
        game = new SixMaxSuppliedRangePreflopGame(results.getFirst().report().request().input());
    }

    @Test
    void threeDistinctQualifiedQuestionsContainCallAndRaiseChoicesWithPinnedOwnedCapabilities()
            throws Exception {
        assertEquals(
                3,
                results.stream()
                        .map(r -> r.report().request().informationSet())
                        .distinct()
                        .count());
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            var r = result.report();
            assertTrue(r.qualifiedForIntervalFeedback());
            assertTrue(r.rejectionReasons().isEmpty());
            assertFalse(r.trainerAdmission());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.L1_REQUEST_SCHEMA, r.request().schemaVersion());
            assertEquals(SixMaxSuppliedIntervalFeedback.L1_REPORT_SCHEMA, r.schemaVersion());
            assertEquals(HASHES.get(i), result.qualifiedFeedback().orElseThrow().reportHash());
            assertEquals(HASHES.get(i), SixMaxHeadsUpPreflopGame.hash(r));
            assertEquals(
                    List.of("fold", "call", "raise:9.0"),
                    r.moves().stream().map(SixMaxSuppliedIntervalFeedback.Move::action).toList());
            assertEquals(
                    i == 1 ? "raise:9.0" : "call",
                    r.moves().stream()
                            .filter(
                                    m ->
                                            m.classification()
                                                    == SixMaxSuppliedIntervalFeedback.Classification
                                                            .WITHIN_LIMIT)
                            .map(SixMaxSuppliedIntervalFeedback.Move::action)
                            .findFirst()
                            .orElseThrow());
            assertEquals(
                    2,
                    r.moves().stream()
                            .filter(
                                    m ->
                                            m.classification()
                                                    == SixMaxSuppliedIntervalFeedback.Classification
                                                            .OUTSIDE_LIMIT)
                            .count());
            assertEquals(1, r.heroCommittedBb());
            assertEquals(27, r.prior().size());
            assertEquals(17766216, r.binding().budget().enumeratedBoards());
            assertEquals(
                    SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                    r.binding().sourceHistoryReachStatus());
            assertEquals(
                    FiniteTwoPlayerConditionedDecisionLoss.L1_REPRESENTATION,
                    r.diagnostic().representation());
            assertEquals(
                    FiniteTwoPlayerConditionalDecisionLoss.L1_CONDITIONED_ALGORITHM,
                    r.diagnostic().joint().algorithm());
            var table = result.questionContext().table();
            assertEquals(6, table.players().size());
            assertEquals(
                    2,
                    table.players().stream()
                            .filter(p -> !p.status().name().equals("FOLDED"))
                            .count());
            assertEquals(
                    r.moves().stream().map(SixMaxSuppliedIntervalFeedback.Move::action).toList(),
                    result.questionContext().legalActions());
        }
    }

    @Test
    void allFreshReferencePoliciesFitTheDeclaredFaceAndEveryEvAndJointLossBound() {
        for (var result : results) {
            var r = result.report();
            assertEquals(
                    List.of(500, 1000),
                    r.referenceChecks().stream()
                            .map(SixMaxSuppliedIntervalFeedback.ReferenceCheck::iterations)
                            .toList());
            for (var ref : r.referenceChecks()) {
                assertTrue(ref.insideDeclaredFace());
                assertTrue(ref.boundsChecked());
                assertTrue(ref.failures().isEmpty());
                assertTrue(ref.inclusionMarginBb() > 0);
                assertTrue(ref.maximumBoundViolationBb() <= 1e-8);
                for (var move : r.moves()) {
                    double ev = ref.decision().values().actionEvBb().get(move.action());
                    double loss = ref.actionLossBb().get(move.action());
                    assertTrue(ev >= move.lowerEvBb() - 1e-8 && ev <= move.upperEvBb() + 1e-8);
                    assertTrue(
                            loss >= move.lowerLossBb() - 1e-8 && loss <= move.upperLossBb() + 1e-8);
                }
            }
        }
    }

    @Test
    void theDefaultNarrowFacesStillRejectReferencesInsteadOfRetryingWithLargerSlack()
            throws Exception {
        for (var result : results) {
            var r = result.report().request();
            var narrow =
                    new SixMaxSuppliedIntervalFeedback.Request(
                            r.schemaVersion(),
                            r.input(),
                            r.hero(),
                            r.informationSet(),
                            1e-8,
                            r.minimumReach(),
                            r.maximumLossBb());
            var rejected = SixMaxSuppliedIntervalFeedback.solve(game, narrow);
            assertFalse(rejected.report().qualifiedForIntervalFeedback());
            assertTrue(rejected.qualifiedFeedback().isEmpty());
            assertTrue(
                    rejected.report().referenceChecks().stream()
                            .noneMatch(
                                    SixMaxSuppliedIntervalFeedback.ReferenceCheck
                                            ::insideDeclaredFace));
            assertTrue(
                    rejected.report()
                            .rejectionReasons()
                            .contains("500:REFERENCE_OUTSIDE_DECLARED_FACE"));
            assertTrue(
                    rejected.report()
                            .rejectionReasons()
                            .contains("1000:REFERENCE_OUTSIDE_DECLARED_FACE"));
        }
    }

    @Test
    void independentReceiptBindsAllReportBytesAndItsOracleSource() throws Exception {
        Path directory = data("77", "request.json").getParent();
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        directory.resolve(
                                                "supplied-root-five-target-l1-interval-feedback-independent-check.json")));
        assertEquals(83, receipt.get("freshLpSolves").intValue());
        assertEquals(0, receipt.get("cachedEndpointCases").intValue());
        assertEquals(3, receipt.get("rows").size());
        assertFalse(receipt.get("trainerAdmission").booleanValue());
        for (int i = 0; i < TAGS.size(); i++) {
            var row = receipt.get("rows").get(i);
            var path = data(TAGS.get(i), "report.json.gz");
            var digest = java.security.MessageDigest.getInstance("SHA-256");
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
                    results.get(i).report().request().informationSet(),
                    row.get("informationSet").textValue());
            assertEquals(24, row.get("checkedJointWitnesses").intValue());
        }
        var source =
                directory
                        .getParent()
                        .getParent()
                        .resolve("scripts/check-supplied-l1-root-feedback.py");
        assertEquals(
                receipt.get("scriptBytesSha256").textValue(),
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(source))));
    }

    @Test
    void narrowLaterQuestionStillRejectsAnInvalidBehavioralWitnessInsteadOfFallingBack() {
        var r = results.getFirst().report().request();
        var request =
                new SixMaxSuppliedIntervalFeedback.Request(
                        r.schemaVersion(),
                        r.input(),
                        5,
                        "5:supplied-conditional-preflop:9d 9h:|BB:raise:9.0|BTN:raise:22.0",
                        1e-8,
                        .01,
                        .01);
        var error =
                assertThrows(
                        IllegalStateException.class,
                        () -> SixMaxSuppliedIntervalFeedback.solve(game, request));
        assertEquals("Invalid behavioral ratio", error.getMessage());
    }

    @Test
    void versionAndRepresentationCannotBeRelabeledToReplayAnOldOrNewReport(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode tree = mapper.valueToTree(results.getFirst().report());
        tree.put("schemaVersion", SixMaxSuppliedIntervalFeedback.REPORT_SCHEMA);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, SixMaxSuppliedIntervalFeedback.Report.class));
        tree.put("schemaVersion", SixMaxSuppliedIntervalFeedback.L1_REPORT_SCHEMA);
        ((ObjectNode) tree.get("diagnostic"))
                .put("representation", FiniteTwoPlayerConditionedDecisionLoss.REPRESENTATION);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, SixMaxSuppliedIntervalFeedback.Report.class));
        ObjectNode request = mapper.valueToTree(results.getFirst().report().request());
        request.put("schemaVersion", SixMaxSuppliedIntervalFeedback.REQUEST_SCHEMA);
        Path requestPath = dir.resolve("old-mode.json");
        Files.writeString(requestPath, request.toPrettyString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedIntervalFeedback.replay(
                                requestPath, data("77", "report.json.gz")));
    }

    @Test
    void savedMoveTamperingCannotCreateAReplayCapability(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode tree = mapper.valueToTree(results.getFirst().report());
        ((ObjectNode) tree.get("moves").get(0)).put("upperLossBb", 0);
        Path altered = dir.resolve("altered.json");
        Files.writeString(altered, tree.toPrettyString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalFeedback.replay(data("77", "request.json"), altered));
    }
}
