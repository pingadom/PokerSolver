package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefinedFlopCoverageStudyTest {
    @Test
    void inspectionLabelsUnverifiedSnapshotsAndKeepsEveryOutcomeVisible() throws Exception {
        var report = SixMaxRefinedFlopCoverageStudy.readReport(REPORT);
        var text = SixMaxRefinedFlopCoverageStudyMain.summary(report, true);
        assertTrue(
                text.startsWith(
                        "storedSnapshotOnly=true trainerAdmission=false freshReferenceRuns=0"));
        assertEquals(
                4,
                text.lines()
                        .filter(line -> line.contains("CONDITIONAL_REJECTED NUMERICAL_FAILURE"))
                        .count());
        assertEquals(4, text.lines().filter(line -> line.contains("ACTION_SEPARATION")).count());
        assertTrue(
                text.contains(
                        "1 BTN 2c 3c after BB bet : ACTION_SEPARATION fold=WITHIN_LIMIT call=OUTSIDE_LIMIT"));
        assertTrue(
                text.contains(
                        "7 BTN Qh Th after BB bet : ACTION_SEPARATION fold=OUTSIDE_LIMIT call=WITHIN_LIMIT"));
    }

    static final Path REQUEST =
            Path.of("../docs/data/supplied-weak-range-flop-coverage-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-weak-range-flop-coverage-report.json.gz");

    @Test
    void requestFixesTheModeBoundsAndCompleteOrderedCartesianProduct() throws Exception {
        var r = SixMaxRefinedFlopCoverageStudy.readRequest(REQUEST);
        var plan = SixMaxRefinedFlopCoverageStudy.plan(r);
        assertEquals(8, plan.size());
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(r.heroHands().get(i / 2), plan.get(i).heroCards());
            assertEquals(List.of(i % 2 == 0 ? "check" : "bet"), plan.get(i).flopActions());
            assertEquals(3, plan.get(i).hero());
            assertEquals(r.parent(), plan.get(i).parent());
        }
        assertEquals(
                new SixMaxRefinedFlopCoverageStudy.Reservation(1, 8, 16, 24, 0, 0),
                SixMaxRefinedFlopCoverageStudy.Reservation.forRequest(r));
        assertThrows(UnsupportedOperationException.class, () -> r.heroHands().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.clear());
        for (var hands :
                List.of(
                        List.<String>of(),
                        List.of("2c 3c", "2c 3c"),
                        List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th", "9s Js"),
                        List.of("9s Js")))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxRefinedFlopCoverageStudy.Request(
                                    r.schemaVersion(),
                                    r.parent(),
                                    r.intervalAlgorithm(),
                                    hands,
                                    r.publicPreflopHistory(),
                                    r.maximumLossBb()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopCoverageStudy.Request(
                                r.schemaVersion(),
                                r.parent(),
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM,
                                r.heroHands(),
                                r.publicPreflopHistory(),
                                r.maximumLossBb()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopCoverageStudy.Request(
                                r.schemaVersion(),
                                r.parent(),
                                r.intervalAlgorithm(),
                                r.heroHands(),
                                r.publicPreflopHistory(),
                                Double.NaN));
    }

    @Test
    void reportCannotHideRejectionsDropCasesOrInventAdmissionAndCounters() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved = SixMaxRefinedFlopCoverageStudy.readReport(REPORT);
        for (String mutation :
                List.of(
                        "admission",
                        "count",
                        "reservation",
                        "drop",
                        "order",
                        "move",
                        "layer",
                        "missing")) {
            var node = (ObjectNode) mapper.valueToTree(saved);
            switch (mutation) {
                case "admission" -> node.put("trainerAdmission", true);
                case "count" -> ((ObjectNode) node.path("counts")).put("solved", 8);
                case "reservation" ->
                        ((ObjectNode) node.path("reservation")).put("automaticRetries", 1);
                case "drop" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode) node.path("cases"))
                                .remove(0);
                case "order" -> ((ObjectNode) node.path("cases").get(0)).put("index", 1);
                case "move" ->
                        ((ObjectNode) node.path("cases").get(1).path("moves").get(1))
                                .put("classification", "WITHIN_LIMIT");
                case "layer" ->
                        ((ObjectNode) node.path("cases").get(0).path("rejection"))
                                .put("layer", "LP");
                case "missing" -> ((ObjectNode) node.path("cases").get(0)).putNull("rejection");
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxRefinedFlopCoverageStudy.Report.class),
                    mutation);
        }
        assertThrows(UnsupportedOperationException.class, () -> saved.cases().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxRefinedFlopCoverageStudy.Rejection("LP", null, "x", null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopCoverageStudy.Rejection(
                                "LP", "NOT_A_REASON", "x", null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopCoverageStudy.Rejection(
                                "other", "NUMERICAL_FAILURE", "x", null));
    }

    @Test
    void exclusiveCliGuardsAndStrictJsonRunBeforeAnySolve(@TempDir Path folder) throws Exception {
        var output = folder.resolve("existing.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedFlopCoverageStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertEquals("keep", Files.readString(output));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedFlopCoverageStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedFlopCoverageStudyMain.main(
                                new String[] {"unknown", REQUEST.toString(), output.toString()}));
        var text = Files.readString(REQUEST);
        for (String changed :
                List.of(
                        text + "{}",
                        text.replace(
                                "\"maximumLossBb\"", "\"maximumLossBb\":0.01,\"maximumLossBb\""),
                        text.replace("\"maximumLossBb\": 0.01", "\"maximumLossBb\": \"0.01\""))) {
            var bad = folder.resolve(UUID.randomUUID() + ".json");
            Files.writeString(bad, changed);
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> SixMaxRefinedFlopCoverageStudy.readRequest(bad));
        }
    }
}
