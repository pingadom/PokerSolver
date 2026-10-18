package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxBtnFlopTrainer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = BtnFlopIntervalResearchController.class,
        properties = "pokerlab.trainer.btn-flop-interval-research-enabled=true")
@Import(BtnFlopIntervalResearchConfiguration.class)
class BtnFlopIntervalResearchControllerTest {
    static final String PIN = "9686d76c4a9d3b1a0875f7fcaed3edecf4491e0c24698892b516f91a69c89cd1";
    static final String BASE = "/api/v1/trainer/research/btn-flop-interval";
    @Autowired MockMvc mvc;
    @Autowired SixMaxBtnFlopTrainer trainer;
    final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        for (String kind : List.of("request", "report"))
            registry.add(
                    "pokerlab.trainer.btn-flop-interval-" + kind + "-path",
                    () ->
                            IntervalResearchControllerTest.fixture(
                                            "supplied-four-button-hand-btn-curriculum-"
                                                    + kind
                                                    + (kind.equals("report")
                                                            ? ".json.gz"
                                                            : ".json"))
                                    .toString());
        registry.add("pokerlab.trainer.btn-flop-interval-report-hash", () -> PIN);
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
                    mapper.readValue(body, SixMaxBtnFlopTrainer.Question.class));
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
        for (String action : List.of("check", "bet"))
            for (int i = 0; i < 4; i++) {
                var body = postJson("/grade", grade(i, action));
                assertEquals(
                        trainer.grade(trainer.question(i), action),
                        mapper.readValue(body, SixMaxBtnFlopTrainer.Feedback.class));
                choices.add(choice(i, action));
            }
        var review =
                mapper.readValue(
                        postJson("/review", Map.of("reportHash", PIN, "choices", choices)),
                        SixMaxBtnFlopTrainer.Review.class);
        assertEquals(
                SixMaxBtnFlopTrainer.read(
                        IntervalResearchControllerTest.fixture(
                                "supplied-four-button-hand-btn-flop-drill.json")),
                review);
        assertEquals(new SixMaxBtnFlopTrainer.Counts(4, 4, 4, 0), review.counts());
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
            var body = new HashMap<>(grade(0, "bet"));
            body.put(
                    field,
                    switch (field) {
                        case "reportHash" -> "0".repeat(64);
                        case "questionIndex" -> 4;
                        case "action" -> "fold";
                        default -> 0;
                    });
            invalid("/grade", mapper.writeValueAsString(body));
        }
        for (Object index : Arrays.asList(null, -1, 4, 1.5, true, "1")) {
            var body = new HashMap<>(grade(0, "bet"));
            body.put("questionIndex", index);
            invalid("/grade", mapper.writeValueAsString(body));
        }
        for (String action : Arrays.asList(null, "", "BET", "call")) {
            var body = new HashMap<>(grade(0, "bet"));
            body.put("action", action);
            invalid("/grade", mapper.writeValueAsString(body));
        }
    }

    @Test
    void duplicateUnknownTrailingMissingAndOversizedInputsFailClosed() throws Exception {
        var valid = mapper.writeValueAsString(grade(0, "bet"));
        for (String body :
                List.of(
                        valid + " {}",
                        valid.substring(0, valid.length() - 1) + ",\"questionIndex\":1}",
                        "null",
                        "{}",
                        "[]",
                        " ".repeat(32769) + valid)) invalid("/grade", body);
        for (String field : List.of("reportHash", "questionIndex", "action")) {
            var body = new HashMap<>(grade(0, "bet"));
            body.remove(field);
            invalid("/grade", mapper.writeValueAsString(body));
        }
        invalid("/review", "{\"reportHash\":\"" + PIN + "\",\"choices\":[],\"counts\":{}}");
        invalid(
                "/review",
                "{\"reportHash\":\""
                        + PIN
                        + "\",\"choices\":[{\"questionIndex\":0,\"action\":\"bet\",\"ev\":1}]}");
    }

    @Test
    void globalHundredAttemptCapIncludesRetriesAndDoesNotInflateCoverage() throws Exception {
        var choice = choice(3, "bet");
        var review =
                mapper.readValue(
                        postJson(
                                "/review",
                                Map.of(
                                        "reportHash",
                                        PIN,
                                        "choices",
                                        Collections.nCopies(100, choice))),
                        SixMaxBtnFlopTrainer.Review.class);
        assertEquals(new SixMaxBtnFlopTrainer.Counts(1, 100, 0, 0), review.counts());
        for (Object choices :
                Arrays.asList(
                        null,
                        Collections.singletonList(null),
                        Collections.nCopies(101, choice),
                        List.of(choice(4, "bet")),
                        List.of(choice(1, "fold")))) {
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
        for (String action : List.of("check", "bet"))
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
                                                "attachment; filename=\"pokerlab-btn-flop-review-9686d76c4a9d.json\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertArrayEquals(
                java.nio.file.Files.readAllBytes(
                        IntervalResearchControllerTest.fixture(
                                "supplied-four-button-hand-btn-flop-drill.json")),
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
                                Collections.nCopies(101, choice(0, "check")))));
        var empty = postJson("/review/export", Map.of("reportHash", PIN, "choices", List.of()));
        assertEquals(0, mapper.readTree(empty).get("attempts").size());
    }
}
