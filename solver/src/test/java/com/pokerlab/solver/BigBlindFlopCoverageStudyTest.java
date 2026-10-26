package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigBlindFlopCoverageStudyTest {
    @Test
    void explicitNewModeStillCertifiesAnUnreachableContextInsteadOfInventingABound()
            throws Exception {
        var q = SixMaxBigBlindFlopCoverageStudy.readRequest(REQUEST);
        var request =
                new SixMaxRefinedConditionalStudy.Request(
                        SixMaxRefinedConditionalStudy.REQUEST_SCHEMA,
                        q.parent(),
                        q.intervalAlgorithm(),
                        3,
                        "2c 3c",
                        q.publicPreflopHistory(),
                        List.of("check"));
        var failure =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () -> SixMaxRefinedConditionalStudy.solve(request));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                failure.reason());
        var audit = failure.reachFailure().orElseThrow();
        assertEquals(0, audit.minimumReachWitness().questionReach(), 1e-8);
        assertFalse(audit.trainerAdmission());
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.CERTIFIED_REACH_FAILURE_ALGORITHM,
                audit.algorithm());
        assertEquals(
                BoundedLinearProgram.PHASE_ONE_REFINED_BASIS_ALGORITHM,
                audit.minimumReachWitness().certificate().algorithm());
        assertDoesNotThrow(
                () ->
                        new SixMaxRefinedFlopCoverageStudy.Case(
                                0,
                                request,
                                SixMaxRefinedFlopCoverageStudy.Outcome.CONDITIONAL_REJECTED,
                                null,
                                List.of(),
                                new SixMaxRefinedFlopCoverageStudy.Rejection(
                                        "CONDITIONAL",
                                        failure.reason().name(),
                                        failure.getMessage(),
                                        audit)));
    }

    static final Path REQUEST =
            Path.of("../docs/data/supplied-unpaired-bb-refined-flop-coverage-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-unpaired-bb-refined-flop-coverage-report.json.gz");
    static final Path OLD_REQUEST =
            Path.of("../docs/data/supplied-unpaired-bb-flop-coverage-request.json");
    static final Path OLD_REPORT =
            Path.of("../docs/data/supplied-unpaired-bb-flop-coverage-report.json.gz");

    @Test
    void completeOwnedReplayPreservesAmbiguityAndEveryRealBbContext(@TempDir Path folder)
            throws Exception {
        var owned = SixMaxBigBlindFlopCoverageStudy.replay(REQUEST, REPORT);
        var r = owned.report();
        assertFalse(r.trainerAdmission());
        assertEquals(new SixMaxRefinedFlopCoverageStudy.Counts(6, 6, 0, 0, 0), r.counts());
        for (var item : r.cases()) {
            assertEquals(
                    SixMaxRefinedFlopCoverageStudy.Outcome.NO_ACTION_SEPARATION, item.outcome());
            assertEquals(5, item.request().hero());
            assertEquals("BB", item.conditional().context().actingSeat().name());
            assertEquals(List.of("6d", "Qc", "Td"), item.conditional().context().board());
            boolean first = item.index() % 2 == 0;
            assertEquals(first ? List.of() : List.of("check", "bet"), item.request().flopActions());
            assertEquals(
                    first ? List.of("check", "bet") : List.of("fold", "call"),
                    item.conditional().context().legalActions());
            assertEquals(first ? 6.5 : 9.75, item.conditional().context().potBb());
            assertEquals(first ? 0 : 3.25, item.conditional().context().toCallBb());
            assertTrue(
                    item.moves().stream()
                            .anyMatch(
                                    m ->
                                            m.classification()
                                                    == SixMaxSuppliedIntervalFeedback.Classification
                                                            .STRATEGY_DEPENDENT));
            for (var action : item.conditional().actions()) {
                assertEquals(
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM,
                        action.diagnostic().algorithm());
                assertTrue(action.diagnostic().certifiedReachLowerBound() >= .0001);
                assertEquals(item.conditional().parent().solve(), action.diagnostic().baseline());
                assertEquals(
                        BoundedLinearProgram.PHASE_ONE_REFINED_BASIS_ALGORITHM,
                        action.diagnostic().lowerWitness().certificate().algorithm());
            }
        }
        for (String name : List.of("report.json", "report.json.gz")) {
            var path = folder.resolve(name);
            SixMaxBigBlindFlopCoverageStudy.write(path, owned);
            assertEquals(r, SixMaxBigBlindFlopCoverageStudy.readReport(path));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxBigBlindFlopCoverageStudy.write(path, owned));
        }
        assertTrue(
                Arrays.stream(
                                SixMaxBigBlindFlopCoverageStudy.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void fullOriginalModeReplayStillRetainsAllSixNumericalRejections() throws Exception {
        var r = SixMaxBigBlindFlopCoverageStudy.replay(OLD_REQUEST, OLD_REPORT).report();
        assertEquals(new SixMaxRefinedFlopCoverageStudy.Counts(6, 0, 0, 0, 6), r.counts());
        for (var item : r.cases()) {
            assertEquals("LP", item.rejection().layer());
            assertEquals("NUMERICAL_FAILURE", item.rejection().reason());
            assertEquals("Singular LP basis", item.rejection().detail());
            assertNull(item.conditional());
            assertTrue(item.moves().isEmpty());
        }
    }

    @Test
    void requestCannotSelectAHappySubsetSwapActorOrMixAlgorithmVersions() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var q = SixMaxBigBlindFlopCoverageStudy.readRequest(REQUEST);
        var plan = SixMaxBigBlindFlopCoverageStudy.plan(q);
        assertEquals(List.of("7d 7h", "9d 9h", "Ac Jc"), q.heroHands());
        assertEquals(
                new SixMaxRefinedFlopCoverageStudy.Reservation(1, 6, 12, 18, 0, 0),
                SixMaxBigBlindFlopCoverageStudy.reservation(q));
        assertThrows(UnsupportedOperationException.class, () -> q.heroHands().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.clear());
        for (String mutation :
                List.of("drop", "order", "duplicate", "actor", "mode", "schema", "loss")) {
            var node = (ObjectNode) mapper.valueToTree(q);
            switch (mutation) {
                case "drop" -> ((ArrayNode) node.path("heroHands")).remove(0);
                case "order" -> ((ArrayNode) node.path("heroHands")).set(0, new TextNode("Ac Jc"));
                case "duplicate" ->
                        ((ArrayNode) node.path("heroHands")).set(1, new TextNode("7d 7h"));
                case "actor" -> node.put("hero", 3);
                case "mode" ->
                        node.put(
                                "intervalAlgorithm",
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM);
                case "schema" ->
                        node.put("schemaVersion", SixMaxBigBlindFlopCoverageStudy.REQUEST_SCHEMA);
                case "loss" -> node.put("maximumLossBb", .02);
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxBigBlindFlopCoverageStudy.Request.class),
                    mutation);
        }
    }

    @Test
    void editedReportsCannotClaimAdmissionInventSeparationOrHideCases() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var r = SixMaxBigBlindFlopCoverageStudy.readReport(REPORT);
        for (String mutation :
                List.of(
                        "admission",
                        "count",
                        "reservation",
                        "drop",
                        "order",
                        "move",
                        "actor",
                        "schema")) {
            var node = (ObjectNode) mapper.valueToTree(r);
            switch (mutation) {
                case "admission" -> node.put("trainerAdmission", true);
                case "count" -> ((ObjectNode) node.path("counts")).put("actionSeparated", 6);
                case "reservation" ->
                        ((ObjectNode) node.path("reservation")).put("automaticRetries", 1);
                case "drop" -> ((ArrayNode) node.path("cases")).remove(0);
                case "order" -> ((ObjectNode) node.path("cases").get(0)).put("index", 1);
                case "move" ->
                        ((ObjectNode) node.path("cases").get(0).path("moves").get(0))
                                .put("classification", "OUTSIDE_LIMIT");
                case "actor" ->
                        ((ObjectNode) node.path("cases").get(0).path("request")).put("hero", 3);
                case "schema" ->
                        node.put("schemaVersion", SixMaxBigBlindFlopCoverageStudy.REPORT_SCHEMA);
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxBigBlindFlopCoverageStudy.Report.class),
                    mutation);
        }
        assertThrows(UnsupportedOperationException.class, () -> r.cases().clear());
    }

    @Test
    void cliGuardsStrictJsonAndCapsRejectBeforeAnySolverWork(@TempDir Path temp) throws Exception {
        var output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindFlopCoverageStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertEquals("keep", Files.readString(output));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindFlopCoverageStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindFlopCoverageStudyMain.main(
                                new String[] {"wrong", REQUEST.toString(), output.toString()}));
        String text = Files.readString(REQUEST);
        for (String changed :
                List.of(
                        text + "{}",
                        text.replace("\"hero\": 5", "\"hero\": 5,\"hero\": 5"),
                        text.replace("\"hero\": 5", "\"hero\": \"5\""),
                        text.replace("\"hero\": 5", "\"unexpected\": 5"))) {
            var path = temp.resolve(UUID.randomUUID() + ".json");
            Files.writeString(path, changed);
            assertThrows(Exception.class, () -> SixMaxBigBlindFlopCoverageStudy.readRequest(path));
        }
        var large = temp.resolve("large.json");
        Files.write(large, new byte[SixMaxBigBlindFlopCoverageStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBigBlindFlopCoverageStudy.readRequest(large));
    }

    @Test
    void inspectionCannotBeConfusedWithQualificationOrFreshReplay() throws Exception {
        var text =
                SixMaxBigBlindFlopCoverageStudyMain.summary(
                        SixMaxBigBlindFlopCoverageStudy.readReport(REPORT), true);
        assertTrue(
                text.startsWith(
                        "storedSnapshotOnly=true trainerAdmission=false freshReferenceRuns=0"));
        assertEquals(6, text.lines().filter(l -> l.contains("NO_ACTION_SEPARATION")).count());
        assertTrue(text.contains("BB 7d 7h first to act"));
        assertTrue(text.contains("BB Ac Jc after BB check / BTN bet"));
    }
}
