package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigBlindPairwiseStudyTest {
    static final Path REQUEST = BigBlindFlopCoverageStudyTest.REQUEST;
    static final Path REPORT = Path.of("../docs/data/supplied-unpaired-bb-pairwise-study.json.gz");

    @Test
    void fullOwnedReplayKeepsAllSixSourcesAndOnlyTwoUnqualifiedCandidates(@TempDir Path temp)
            throws Exception {
        var owned = SixMaxBigBlindPairwiseStudy.replay(REQUEST, REPORT);
        var r = owned.report();
        assertEquals(
                SixMaxBigBlindFlopCoverageStudy.readReport(BigBlindFlopCoverageStudyTest.REPORT),
                r.source());
        assertEquals(List.of(1, 3), r.candidateIndices());
        assertFalse(r.trainerAdmission());
        assertEquals(6, r.cases().size());
        for (int i = 0; i < 6; i++) {
            var item = r.cases().get(i);
            assertEquals(i, item.index());
            assertEquals(i == 1 || i == 3, item.actionSeparated());
            for (int a = 0; a < 2; a++) {
                var original = r.source().cases().get(i).conditional().actions().get(a);
                assertEquals(original.lowerUtilityBb(), item.moves().get(a).lowerEvBb());
                assertEquals(original.upperUtilityBb(), item.moves().get(a).upperEvBb());
            }
        }
        for (int i : r.candidateIndices())
            assertEquals(0, r.cases().get(i).moves().get(1).upperLossBb());
        for (String name : List.of("study.json", "study.json.gz")) {
            var path = temp.resolve(name);
            SixMaxBigBlindPairwiseStudy.write(path, owned);
            assertEquals(r, SixMaxBigBlindPairwiseStudy.readReport(path));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxBigBlindPairwiseStudy.write(path, owned));
        }
        assertTrue(
                Arrays.stream(SixMaxBigBlindPairwiseStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void editedLossesSwappedContextsAndAdmissionReject() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved = SixMaxBigBlindPairwiseStudy.readReport(REPORT);
        for (String kind :
                List.of("admission", "loss", "ev", "drop", "order", "candidate", "mode")) {
            var node = (ObjectNode) mapper.valueToTree(saved);
            switch (kind) {
                case "admission" -> node.put("trainerAdmission", true);
                case "loss" ->
                        ((ObjectNode) node.path("cases").get(1).path("moves").get(1))
                                .put("upperLossBb", .5);
                case "ev" ->
                        ((ObjectNode) node.path("cases").get(1).path("moves").get(1))
                                .put("lowerEvBb", 0);
                case "drop" -> ((ArrayNode) node.path("cases")).remove(0);
                case "order" -> ((ObjectNode) node.path("cases").get(1)).put("index", 3);
                case "candidate" -> ((ArrayNode) node.path("candidateIndices")).add(5);
                case "mode" -> node.put("lossAlgorithm", "old");
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxBigBlindPairwiseStudy.Report.class),
                    kind);
        }
    }

    @Test
    void cliOutputAndVersionGuardsPrecedeSolving(@TempDir Path temp) throws Exception {
        var output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseStudyMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseStudy.solve(
                                SixMaxBigBlindFlopCoverageStudy.readRequest(
                                        BigBlindFlopCoverageStudyTest.OLD_REQUEST)));
        assertEquals("keep", Files.readString(output));
        var saved = SixMaxBigBlindPairwiseStudy.readReport(REPORT);
        assertThrows(UnsupportedOperationException.class, () -> saved.cases().clear());
        assertThrows(UnsupportedOperationException.class, () -> saved.candidateIndices().clear());
    }
}
