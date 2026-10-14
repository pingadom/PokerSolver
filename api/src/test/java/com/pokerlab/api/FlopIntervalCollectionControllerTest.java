package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxFlopIntervalCollection;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FlopIntervalCollectionController.class,
        properties = {
            "pokerlab.trainer.flop-interval-collection-enabled=true",
            "spring.jackson.deserialization.fail-on-unknown-properties=true"
        })
@Import(FlopIntervalCollectionConfiguration.class)
class FlopIntervalCollectionControllerTest {
    static final String PIN = "fb77bda6bf843e8a2e4ec49c91070e608b240e50e770674601413351ad099436";
    static final String BASE = "/api/v1/trainer/research/flop-interval-collection";
    @Autowired MockMvc mvc;
    @Autowired SixMaxFlopIntervalCollection collection;
    final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add(
                "pokerlab.trainer.flop-interval-collection-manifest-path",
                () ->
                        IntervalResearchControllerTest.fixture(
                                        "supplied-two-flop-interval-collection-manifest.json")
                                .toString());
        registry.add("pokerlab.trainer.flop-interval-collection-hash", () -> PIN);
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

    Map<String, Object> grade(int index, String action) {
        var result = new HashMap<>(choice(index, action));
        result.put("collectionHash", PIN);
        return result;
    }

    @Test
    void realPinnedStartupServesBothTypedPublicQuestionsAndSharedProvenance() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.collectionHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(2))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.maximumAttempts").value(100))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(jsonPath("$.questionKinds[0]").value("INITIAL_CHECK_BET"))
                .andExpect(jsonPath("$.questionKinds[1]").value("BB_FACING_BET"))
                .andExpect(
                        jsonPath("$.sourceHistoryReachStatus")
                                .value("UNAVAILABLE_NO_SOURCE_POLICY"));
        for (int i = 0; i < 2; i++) {
            var body =
                    mvc.perform(get(BASE + "/questions/" + i).param("collectionHash", PIN))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.index").value(i))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(
                    collection.question(i),
                    mapper.readValue(body, SixMaxFlopIntervalCollection.Question.class));
            for (String hidden :
                    List.of("opponentCards", "worlds", "prior", "candidate", "lowerWitness"))
                assertFalse(body.contains("\"" + hidden + "\""));
        }
    }

    @Test
    void allFourLegalActionsReturnExactCachedTypedGrades() throws Exception {
        for (int i = 0; i < 2; i++)
            for (String action : i == 0 ? List.of("check", "bet") : List.of("fold", "call")) {
                var body =
                        mvc.perform(
                                        post(BASE + "/grade")
                                                .contentType("application/json")
                                                .content(
                                                        mapper.writeValueAsString(
                                                                grade(i, action))))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                assertEquals(
                        collection.grade(collection.question(i), action),
                        mapper.readValue(body, SixMaxFlopIntervalCollection.Feedback.class));
            }
    }

    @Test
    void mixedReviewExactlyMatchesSavedOwnedArtifactAndEmptyReviewIsValid() throws Exception {
        var choices =
                List.of(choice(0, "check"), choice(1, "fold"), choice(0, "bet"), choice(1, "call"));
        var body =
                mvc.perform(
                                post(BASE + "/review")
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "collectionHash",
                                                                PIN,
                                                                "choices",
                                                                choices))))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.counts.distinctQuestionsAttempted").value(2))
                        .andExpect(jsonPath("$.counts.withinLimit").value(2))
                        .andExpect(jsonPath("$.counts.outsideLimit").value(2))
                        .andExpect(jsonPath("$.accuracy").doesNotExist())
                        .andExpect(jsonPath("$.totalEvLossBb").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(
                SixMaxFlopIntervalCollection.read(
                        IntervalResearchControllerTest.fixture(
                                "supplied-two-flop-interval-collection-review.json")),
                mapper.readValue(body, SixMaxFlopIntervalCollection.Review.class));
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content("{\"collectionHash\":\"" + PIN + "\",\"choices\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(0));
    }

    @Test
    void wrongPinsIndicesMenusAndClientProvidedFactsAreRejected() throws Exception {
        var valid = grade(0, "check");
        for (String field :
                List.of(
                        "collectionHash",
                        "reportHash",
                        "questionIndex",
                        "action",
                        "evLossBb",
                        "context",
                        "manifestPath")) {
            var changed = new HashMap<>(valid);
            changed.put(
                    field,
                    switch (field) {
                        case "collectionHash", "reportHash" -> "0".repeat(64);
                        case "questionIndex" -> 1;
                        case "action" -> "fold";
                        default -> 0;
                    });
            mvc.perform(
                            post(BASE + "/grade")
                                    .contentType("application/json")
                                    .content(mapper.writeValueAsString(changed)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        for (Object invalid : Arrays.asList(null, -1, 2, 1.5)) {
            var changed = new HashMap<>(valid);
            changed.put("questionIndex", invalid);
            mvc.perform(
                            post(BASE + "/grade")
                                    .contentType("application/json")
                                    .content(mapper.writeValueAsString(changed)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(grade(1, "check"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hundredRetriesCountOneContextAndOversizedOrForgedReviewsFail() throws Exception {
        var choice = choice(1, "fold");
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "collectionHash",
                                                        PIN,
                                                        "choices",
                                                        Collections.nCopies(100, choice)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.distinctQuestionsAttempted").value(1))
                .andExpect(jsonPath("$.counts.withinLimit").value(100));
        for (Object choices :
                Arrays.asList(
                        null,
                        Collections.singletonList(null),
                        Collections.nCopies(101, choice),
                        List.of(choice(1, "bet")))) {
            var body = new HashMap<String, Object>();
            body.put("collectionHash", PIN);
            body.put("choices", choices);
            mvc.perform(
                            post(BASE + "/review")
                                    .contentType("application/json")
                                    .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(
                                        "{\"collectionHash\":\""
                                                + PIN
                                                + "\",\"choices\":[],\"counts\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void questionLookupsRequireCurrentCollectionPinAndBoundedIndex() throws Exception {
        for (int i : List.of(-1, 2))
            mvc.perform(get(BASE + "/questions/" + i).param("collectionHash", PIN))
                    .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0").param("collectionHash", "0".repeat(64)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0")).andExpect(status().isBadRequest());
    }
}
