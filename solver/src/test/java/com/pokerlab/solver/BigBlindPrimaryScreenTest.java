package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigBlindPrimaryScreenTest {
    static Path request(String name) {
        return Path.of("../docs/data/supplied-unpaired-bb-" + name + "-screen-request.json");
    }

    static Path report(String name) {
        return Path.of("../docs/data/supplied-unpaired-bb-" + name + "-primary-screen.json.gz");
    }

    @Test
    void bothModelsReplayAllContextsWithoutFreshReferencesOrGradeAuthority(@TempDir Path temp)
            throws Exception {
        for (String name : List.of("quarter-pot", "pot-size")) {
            var owned = SixMaxBigBlindPrimaryScreen.replay(request(name), report(name));
            var r = owned.report();
            boolean pot = name.equals("pot-size");
            assertEquals(pot ? List.of(3, 5) : List.of(), r.primaryEligibleIndices());
            assertFalse(r.trainerAdmission());
            assertEquals(0, r.reservation().freshReferenceRuns());
            assertEquals(6, r.cases().size());
            assertEquals(6, r.study().source().counts().solved());
            for (int i = 0; i < 6; i++) {
                var conditional = r.study().source().cases().get(i).conditional();
                assertEquals("BB", conditional.context().actingSeat().name());
                assertEquals(pot ? 6.5 : 1.625, conditional.context().betSizeBb());
                assertEquals(i % 2 == 0 ? 0 : pot ? 6.5 : 1.625, conditional.context().toCallBb());
                assertEquals(
                        r.cases().get(i).primaryEligible(),
                        r.cases().get(i).rejectionReasons().isEmpty());
            }
            var output = temp.resolve(name + ".json.gz");
            SixMaxBigBlindPrimaryScreen.write(output, owned);
            assertEquals(r, SixMaxBigBlindPrimaryScreen.readReport(output));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxBigBlindPrimaryScreen.write(output, owned));
        }
        assertTrue(
                Arrays.stream(SixMaxBigBlindPrimaryScreen.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertFalse(
                Arrays.stream(SixMaxBigBlindPrimaryScreen.class.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals("grade")));
    }

    @Test
    void primaryEligibilityCannotDropCasesInventReachOrSkipFailures() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved = SixMaxBigBlindPrimaryScreen.readReport(report("quarter-pot"));
        for (String kind :
                List.of(
                        "admission",
                        "drop",
                        "candidate",
                        "reason",
                        "outcome",
                        "budget",
                        "primary")) {
            var node = (ObjectNode) mapper.valueToTree(saved);
            switch (kind) {
                case "admission" -> node.put("trainerAdmission", true);
                case "drop" -> ((ArrayNode) node.path("cases")).remove(0);
                case "candidate" -> ((ArrayNode) node.path("primaryEligibleIndices")).add(1);
                case "reason" ->
                        ((ArrayNode) node.path("cases").get(1).path("rejectionReasons"))
                                .removeAll();
                case "outcome" ->
                        ((ObjectNode) node.path("cases").get(1)).put("primaryEligible", true);
                case "budget" ->
                        ((ObjectNode) node.path("reservation")).put("freshReferenceRuns", 2);
                case "primary" -> ((ObjectNode) node.path("cases").get(1)).putNull("primary");
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxBigBlindPrimaryScreen.Report.class),
                    kind);
        }
    }

    @Test
    void incompatibleBettingGameCannotSupplyCoverageForAnotherModel() throws Exception {
        var quarter = SixMaxBigBlindFlopCoverageStudy.readRequest(request("quarter-pot"));
        var pot = SixMaxBigBlindFlopCoverageStudy.readRequest(request("pot-size"));
        // Identity rejection occurs before LP work; a small existing control game suffices.
        assertNotEquals(quarter.parent().connected(), pot.parent().connected());
        var r = SixMaxBigBlindPrimaryScreen.readReport(report("pot-size"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxBigBlindPrimaryScreen.Report(
                                r.schemaVersion(),
                                r.publicationStatus(),
                                false,
                                quarter,
                                r.reservation(),
                                r.study(),
                                r.cases(),
                                r.primaryEligibleIndices()));
    }

    @Test
    void outputAndOldVersionPreflightRejectBeforeAnySolve(@TempDir Path temp) throws Exception {
        var output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPrimaryScreenMain.main(
                                new String[] {
                                    "solve", request("pot-size").toString(), output.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPrimaryScreenMain.main(
                                new String[] {
                                    "solve",
                                    request("pot-size").toString(),
                                    request("pot-size").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPrimaryScreen.solve(
                                SixMaxBigBlindFlopCoverageStudy.readRequest(
                                        BigBlindFlopCoverageStudyTest.OLD_REQUEST)));
        assertEquals("keep", Files.readString(output));
    }
}
