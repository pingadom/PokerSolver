package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxPairedFlopTrainer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PairedFlopIntervalResearchController.class,
        properties = "pokerlab.trainer.paired-flop-interval-research-enabled=true")
@Import(PairedFlopIntervalResearchConfiguration.class)
class PairedFlopIntervalResearchControllerTest {
    static final String PIN = "c0e778afe5530740ee8d4dad3b9f9716a05fc45d8c1d141880d99212af99f61e";
    static final String BASE = "/api/v1/trainer/research/paired-flop-interval";
    @Autowired MockMvc mvc;
    @Autowired SixMaxPairedFlopTrainer trainer;
    final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        for (String kind : List.of("request", "report"))
            registry.add(
                    "pokerlab.trainer.paired-flop-interval-" + kind + "-path",
                    () ->
                            IntervalResearchControllerTest.fixture(
                                            "supplied-paired-four-hand-curriculum-"
                                                    + kind
                                                    + (kind.equals("report")
                                                            ? ".json.gz"
                                                            : ".json"))
                                    .toString());
        registry.add("pokerlab.trainer.paired-flop-interval-report-hash", () -> PIN);
    }

    Map<String, Object> choice(int index, String action) {
        return Map.of("questionIndex", index, "action", action);
    }

    Map<String, Object> grade(int index, String action) {
        var body = new HashMap<>(choice(index, action));
        body.put("reportHash", PIN);
        return body;
    }

    String postJson(String path, Object body) throws Exception {
        return mvc.perform(
                        post(BASE + path)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    void invalid(String path, String body) throws Exception {
        mvc.perform(post(BASE + path).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void realPinnedPhysicalStartupServesFourPublicQuestionsAndProvenance() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reportHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(4))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.maximumAttempts").value(100))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(
                        jsonPath("$.sourceHistoryReachStatus")
                                .value("UNAVAILABLE_NO_SOURCE_POLICY"));
        for (int i = 0; i < 4; i++) {
            var body =
                    mvc.perform(get(BASE + "/questions/" + i).param("reportHash", PIN))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.questionIndex").value(i))
                            .andExpect(jsonPath("$.hero").value("BTN"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(
                    trainer.question(i),
                    mapper.readValue(body, SixMaxPairedFlopTrainer.Question.class));
            for (String hidden :
                    List.of(
                            "opponentCards",
                            "worlds",
                            "prior",
                            "candidate",
                            "lowerWitness",
                            "strategy")) assertFalse(body.contains("\"" + hidden + "\""));
        }
    }

    @Test
    void allEightLegalGradesAndMixedReviewExactlyMatchOwnedSavedArtifact() throws Exception {
        var choices = new ArrayList<Map<String, Object>>();
        for (String action : List.of("fold", "call"))
            for (int i = 0; i < 4; i++) {
                var body = postJson("/grade", grade(i, action));
                assertEquals(
                        trainer.grade(trainer.question(i), action),
                        mapper.readValue(body, SixMaxPairedFlopTrainer.Feedback.class));
                choices.add(choice(i, action));
            }
        var review =
                mapper.readValue(
                        postJson("/review", Map.of("reportHash", PIN, "choices", choices)),
                        SixMaxPairedFlopTrainer.Review.class);
        assertEquals(
                SixMaxPairedFlopTrainer.read(
                        IntervalResearchControllerTest.fixture(
                                "supplied-paired-four-hand-flop-drill.json")),
                review);
        assertEquals(new SixMaxPairedFlopTrainer.Counts(4, 4, 4, 0), review.counts());
        assertEquals(
                0,
                mapper.readTree(
                                postJson(
                                        "/review", Map.of("reportHash", PIN, "choices", List.of())))
                        .at("/counts/distinctQuestionsAttempted")
                        .asInt());
    }

    @Test
    void currentPinBoundedIndicesAndExactLegalMenuAreRequired() throws Exception {
        for (int i : List.of(-1, 4))
            mvc.perform(get(BASE + "/questions/" + i).param("reportHash", PIN))
                    .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0").param("reportHash", "0".repeat(64)))
                .andExpect(status().isBadRequest());
        for (String field :
                List.of(
                        "reportHash",
                        "questionIndex",
                        "action",
                        "evLossBb",
                        "context",
                        "requestPath")) {
            var body = new HashMap<>(grade(0, "call"));
            body.put(
                    field,
                    switch (field) {
                        case "reportHash" -> "0".repeat(64);
                        case "questionIndex" -> 4;
                        case "action" -> "bet";
                        default -> 0;
                    });
            invalid("/grade", mapper.writeValueAsString(body));
        }
        for (Object index : Arrays.asList(null, -1, 4, 1.5, true, "1")) {
            var body = new HashMap<>(grade(0, "call"));
            body.put("questionIndex", index);
            invalid("/grade", mapper.writeValueAsString(body));
        }
        for (String action : Arrays.asList(null, "", "CALL", "check")) {
            var body = new HashMap<>(grade(0, "call"));
            body.put("action", action);
            invalid("/grade", mapper.writeValueAsString(body));
        }
    }

    @Test
    void duplicateUnknownTrailingMissingAndOversizedInputsFailClosed() throws Exception {
        var valid = mapper.writeValueAsString(grade(0, "call"));
        for (String body :
                List.of(
                        valid + " {}",
                        valid.substring(0, valid.length() - 1) + ",\"questionIndex\":1}",
                        "null",
                        "{}",
                        "[]",
                        " ".repeat(32769) + valid)) invalid("/grade", body);
        for (String field : List.of("reportHash", "questionIndex", "action")) {
            var body = new HashMap<>(grade(0, "call"));
            body.remove(field);
            invalid("/grade", mapper.writeValueAsString(body));
        }
        invalid("/review", "{\"reportHash\":\"" + PIN + "\",\"choices\":[],\"counts\":{}}");
        invalid(
                "/review",
                "{\"reportHash\":\""
                        + PIN
                        + "\",\"choices\":[{\"questionIndex\":0,\"action\":\"call\",\"ev\":1}]}");
    }

    @Test
    void globalHundredAttemptCapIncludesRetriesAndDoesNotInflateCoverage() throws Exception {
        var choice = choice(3, "call");
        var review =
                mapper.readValue(
                        postJson(
                                "/review",
                                Map.of(
                                        "reportHash",
                                        PIN,
                                        "choices",
                                        Collections.nCopies(100, choice))),
                        SixMaxPairedFlopTrainer.Review.class);
        assertEquals(new SixMaxPairedFlopTrainer.Counts(1, 100, 0, 0), review.counts());
        for (Object choices :
                Arrays.asList(
                        null,
                        Collections.singletonList(null),
                        Collections.nCopies(101, choice),
                        List.of(choice(4, "call")),
                        List.of(choice(1, "bet")))) {
            var body = new HashMap<String, Object>();
            body.put("reportHash", PIN);
            body.put("choices", choices);
            invalid("/review", mapper.writeValueAsString(body));
        }
        invalid(
                "/review",
                mapper.writeValueAsString(
                        Map.of("reportHash", "0".repeat(64), "choices", List.of(choice))));
    }

    @Test
    void canonicalExportExactlyMatchesOwnedDocumentBytesAndUsesTheSameStrictChoices()
            throws Exception {
        var choices = new ArrayList<Map<String, Object>>();
        for (String action : List.of("fold", "call"))
            for (int i = 0; i < 4; i++) choices.add(choice(i, action));
        var body = mapper.writeValueAsString(Map.of("reportHash", PIN, "choices", choices));
        var bytes =
                mvc.perform(
                                post(BASE + "/review/export")
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                "attachment; filename=\"pokerlab-paired-flop-review-c0e778afe553.json\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertArrayEquals(
                java.nio.file.Files.readAllBytes(
                        IntervalResearchControllerTest.fixture(
                                "supplied-paired-four-hand-flop-drill.json")),
                bytes);
        for (String bad :
                List.of(
                        "null",
                        "{}",
                        body + " {}",
                        body.substring(0, body.length() - 1) + ",\"grades\":[]}"))
            invalid("/review/export", bad);
        invalid(
                "/review/export",
                mapper.writeValueAsString(
                        Map.of("reportHash", "0".repeat(64), "choices", choices)));
        invalid(
                "/review/export",
                mapper.writeValueAsString(
                        Map.of(
                                "reportHash",
                                PIN,
                                "choices",
                                Collections.nCopies(101, choice(0, "fold")))));
        var empty = postJson("/review/export", Map.of("reportHash", PIN, "choices", List.of()));
        assertEquals(0, mapper.readTree(empty).get("attempts").size());
    }
}
