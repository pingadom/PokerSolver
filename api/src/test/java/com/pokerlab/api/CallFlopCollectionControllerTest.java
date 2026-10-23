package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxCallFlopCollection;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CallFlopCollectionController.class,
        properties = "pokerlab.trainer.call-flop-collection-enabled=true")
@Import(CallFlopCollectionConfiguration.class)
class CallFlopCollectionControllerTest {
    static final String PIN = "62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619";
    static final String BASE = "/api/v1/trainer/research/flop-interval-collection-v5";
    @Autowired MockMvc mvc;
    @Autowired SixMaxCallFlopCollection collection;
    final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        // One real six-source replay in this cached Spring context: twelve fresh runs/972k.
        registry.add(
                "pokerlab.trainer.call-flop-collection-manifest-path",
                () ->
                        IntervalResearchControllerTest.fixture(
                                        "supplied-fourteen-question-flop-collection-manifest.json")
                                .toString());
        registry.add("pokerlab.trainer.call-flop-collection-hash", () -> PIN);
    }

    Map<String, Object> choice(int index, String action) {
        return Map.of(
                "questionIndex",
                index,
                "reportHash",
                collection.question(index).decision().reportHash(),
                "action",
                action);
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

    void invalid(String path, String json) throws Exception {
        mvc.perform(post(BASE + path).contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataAndEveryPublicQuestionMatchTheOwnedCache() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.collectionHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(14))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.independentInputSets").value(2))
                .andExpect(jsonPath("$.startupBudget.freshIterations").value(972000))
                .andExpect(jsonPath("$.trainerAdmission").value(false));
        for (int i = 0; i < 14; i++) {
            var text =
                    mvc.perform(get(BASE + "/questions/" + i).param("collectionHash", PIN))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(
                    collection.question(i),
                    mapper.readValue(text, SixMaxCallFlopCollection.Question.class));
            for (String hidden :
                    List.of("worlds", "candidate", "strategy", "opponentCards", "lowerWitness"))
                assertFalse(text.contains("\"" + hidden + "\""));
        }
    }

    @Test
    void everyGradeReviewAndExportMatchesPhysicalEvidence() throws Exception {
        var choices = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 14; i++)
            for (String action : collection.question(i).decision().legalActions()) {
                var c = choice(i, action);
                choices.add(c);
                var body = new HashMap<>(c);
                body.put("collectionHash", PIN);
                assertEquals(
                        collection.grade(collection.question(i), action),
                        mapper.readValue(
                                postJson("/grade", body), SixMaxCallFlopCollection.Feedback.class));
            }
        var body = Map.of("collectionHash", PIN, "choices", choices);
        var expected =
                Files.readString(
                        IntervalResearchControllerTest.fixture(
                                "supplied-fourteen-question-flop-collection-review.json"));
        assertEquals(mapper.readTree(expected), mapper.readTree(postJson("/review", body)));
        assertEquals(expected, postJson("/review/export", body));
    }

    @Test
    void strictBodiesCannotSupplyValuesOrCoercePinsAndIndices() throws Exception {
        var valid = new HashMap<>(choice(11, "call"));
        valid.put("collectionHash", PIN);
        for (Object index : List.of(-1, 14, 11.5, "11", true)) {
            var changed = new HashMap<>(valid);
            changed.put("questionIndex", index);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        for (String field : List.of("collectionHash", "reportHash", "questionIndex", "action")) {
            var changed = new HashMap<>(valid);
            changed.remove(field);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        for (String field : List.of("evBb", "question", "feedback")) {
            var changed = new HashMap<>(valid);
            changed.put(field, 99);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        var wrong = new HashMap<>(valid);
        wrong.put("reportHash", "0".repeat(64));
        invalid("/grade", mapper.writeValueAsString(wrong));
        wrong.put("reportHash", collection.question(11).decision().reportHash());
        wrong.put("action", "bet");
        invalid("/grade", mapper.writeValueAsString(wrong));
        invalid("/grade", mapper.writeValueAsString(valid) + " {}");
        invalid(
                "/grade",
                "{\"collectionHash\":\"" + PIN + "\",\"collectionHash\":\"" + PIN + "\"}");
        invalid(
                "/review",
                mapper.writeValueAsString(
                        Map.of(
                                "collectionHash",
                                PIN,
                                "choices",
                                Collections.nCopies(101, choice(11, "call")))));
        invalid("/review", "{\"collectionHash\":\"" + PIN + "\",\"choices\":[null]}");
        invalid("/review", " ".repeat(32769));
        mvc.perform(get(BASE + "/questions/14").param("collectionHash", PIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedChoicesDoNotInventDistinctQuestionCoverage() throws Exception {
        var body =
                Map.of(
                        "collectionHash",
                        PIN,
                        "choices",
                        Collections.nCopies(100, choice(13, "call")));
        var result = mapper.readTree(postJson("/review", body));
        assertEquals(1, result.path("counts").path("distinctQuestionsAttempted").asInt());
        assertEquals(100, result.path("counts").path("withinLimit").asInt());
        var empty =
                mapper.readTree(
                        postJson("/review", Map.of("collectionHash", PIN, "choices", List.of())));
        assertEquals(0, empty.path("counts").path("distinctQuestionsAttempted").asInt());
    }
}
