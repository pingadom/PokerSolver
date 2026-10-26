package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigBlindPairwiseFeedbackTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-unpaired-bb-pairwise-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-unpaired-bb-pairwise-feedback-report.json.gz");

    @Test
    void freshReplayRetainsEveryFailedGateAndCannotGradeAnyRejectedQuestion(@TempDir Path temp)
            throws Exception {
        var owned = SixMaxBigBlindPairwiseFeedback.replay(REQUEST, REPORT);
        var r = owned.report();
        assertFalse(r.trainerAdmission());
        assertTrue(r.qualifiedIndices().isEmpty());
        assertTrue(owned.qualifiedQuestions().isEmpty());
        assertEquals(
                SixMaxBigBlindPairwiseStudy.readReport(BigBlindPairwiseStudyTest.REPORT),
                r.study());
        assertEquals(6, r.cases().size());
        assertEquals(
                List.of(80000, 160000),
                r.references().stream().map(p -> p.policy().iterations()).toList());
        assertEquals(18, r.reservation().selectedPolicyAssessments());
        for (var item : r.cases()) {
            assertFalse(item.qualifiedForOfflineDrill());
            assertFalse(item.rejectionReasons().isEmpty());
            assertEquals(2, item.references().size());
            int index = item.index();
            for (String action : List.of("fold", "call", "check", "bet"))
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxBigBlindPairwiseFeedback.grade(owned, index, action));
        }
        for (int index : List.of(1, 3)) {
            var item = r.cases().get(index);
            assertTrue(r.study().cases().get(index).actionSeparated());
            assertTrue(
                    item.rejectionReasons().contains("PRIMARY:INSUFFICIENT_ACTUAL_QUESTION_REACH"));
            assertTrue(
                    item.rejectionReasons().contains("80000:INSUFFICIENT_ACTUAL_QUESTION_REACH"));
            assertTrue(
                    item.rejectionReasons().contains("160000:INSUFFICIENT_ACTUAL_QUESTION_REACH"));
            assertTrue(item.primary().counterfactualQuestionReachGivenPhysicalFlop() >= .0001);
        }
        var output = temp.resolve("feedback.json.gz");
        SixMaxBigBlindPairwiseFeedback.write(output, owned);
        assertEquals(r, SixMaxBigBlindPairwiseFeedback.readReport(output));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxBigBlindPairwiseFeedback.write(output, owned));
        assertTrue(
                Arrays.stream(SixMaxBigBlindPairwiseFeedback.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertTrue(
                Arrays.stream(
                                SixMaxBigBlindPairwiseFeedback.QualifiedQuestion.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void reportCannotDropOutcomesSkipReferencesOrMintSurvivors() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved = SixMaxBigBlindPairwiseFeedback.readReport(REPORT);
        for (String kind :
                List.of(
                        "admission",
                        "qualified",
                        "drop",
                        "reference",
                        "policy",
                        "reason",
                        "outcome",
                        "budget")) {
            var node = (ObjectNode) mapper.valueToTree(saved);
            switch (kind) {
                case "admission" -> node.put("trainerAdmission", true);
                case "qualified" -> ((ArrayNode) node.path("qualifiedIndices")).add(1);
                case "drop" -> ((ArrayNode) node.path("cases")).remove(1);
                case "reference" ->
                        ((ArrayNode) node.path("cases").get(1).path("references")).remove(0);
                case "policy" ->
                        ((ObjectNode) node.path("cases").get(1).path("references").get(0))
                                .put("solutionHash", "0".repeat(64));
                case "reason" ->
                        ((ArrayNode) node.path("cases").get(1).path("rejectionReasons")).remove(0);
                case "outcome" ->
                        ((ObjectNode) node.path("cases").get(1))
                                .put("qualifiedForOfflineDrill", true);
                case "budget" ->
                        ((ObjectNode) node.path("reservation")).put("freshReferenceRuns", 3);
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxBigBlindPairwiseFeedback.Report.class),
                    kind);
        }
    }

    @Test
    void strictRequestsAndCliPreflightRejectBeforeFreshTraining(@TempDir Path temp)
            throws Exception {
        var output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBigBlindPairwiseFeedbackMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertEquals("keep", Files.readString(output));
        var text = Files.readString(REQUEST);
        for (String changed :
                List.of(
                        text + "{}",
                        text.replace("\"coverage\":", "\"coverage\":null,\"coverage\":"),
                        text.replace("\"lossAlgorithm\"", "\"unexpected\""),
                        text.replace("80000", "80001"))) {
            var path = temp.resolve(UUID.randomUUID() + ".json");
            Files.writeString(path, changed);
            assertThrows(Exception.class, () -> SixMaxBigBlindPairwiseFeedback.readRequest(path));
        }
    }
}
