package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokerlab.solver.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = IntervalCollectionController.class,
        properties = {
            "pokerlab.trainer.interval-collection-enabled=true",
            "spring.jackson.deserialization.fail-on-unknown-properties=true"
        })
@Import(IntervalCollectionConfiguration.class)
class SevenQuestionIntervalControllerTest {
    static final String PIN = "776fcc01e70027b5584dbe3232d19e1670bbd8e8e087879ae020c510dccc6a00";
    static final String BASE = "/api/v1/trainer/research/interval-collection";
    @Autowired MockMvc mvc;
    @Autowired SixMaxSuppliedIntervalCollection collection;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add(
                "pokerlab.trainer.interval-collection-manifest-path",
                () ->
                        IntervalResearchControllerTest.fixture(
                                        "supplied-seven-question-interval-collection-manifest.json")
                                .toString());
        registry.add("pokerlab.trainer.interval-collection-hash", () -> PIN);
    }

    String grade(int index, String action) {
        return "{\"collectionHash\":\""
                + PIN
                + "\",\"questionIndex\":"
                + index
                + ",\"reportHash\":\""
                + collection.question(index).decision().reportHash()
                + "\",\"action\":\""
                + action
                + "\"}";
    }

    @Test
    void realPinnedStartupServesSevenDistinctPublicQuestionsWithoutAnswers() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.collectionHash").value(PIN))
                .andExpect(
                        jsonPath("$.publicationStatus")
                                .value(SixMaxSuppliedIntervalCollection.STATUS))
                .andExpect(jsonPath("$.availableQuestions").value(7))
                .andExpect(jsonPath("$.maximumAttempts").value(100))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false));
        for (int i = 0; i < 7; i++)
            mvc.perform(get(BASE + "/questions/" + i).param("collectionHash", PIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.index").value(i))
                    .andExpect(jsonPath("$.totalQuestions").value(7))
                    .andExpect(
                            jsonPath("$.decision.heroCards")
                                    .value(collection.question(i).decision().heroCards()))
                    .andExpect(jsonPath("$.decision.table.players.length()").value(6))
                    .andExpect(jsonPath("$.decision.trainerAdmission").value(false))
                    .andExpect(
                            jsonPath("$.decision.sourceHistoryReachStatus")
                                    .value("UNAVAILABLE_NO_SOURCE_POLICY"))
                    .andExpect(jsonPath("$.decision.prior").doesNotExist())
                    .andExpect(jsonPath("$.decision.moves").doesNotExist())
                    .andExpect(jsonPath("$.decision.payoffs").doesNotExist())
                    .andExpect(jsonPath("$.decision.table.players[3].cards").doesNotExist());
    }

    @Test
    void allThreeVerdictsUseCachedOwnedFeedbackAndBindBothPins() throws Exception {
        for (var item :
                List.of(
                        new String[] {"0", "call", "WITHIN_LIMIT"},
                        new String[] {"1", "raise:9.0", "WITHIN_LIMIT"},
                        new String[] {"2", "call", "WITHIN_LIMIT"},
                        new String[] {"3", "raise:50.0", "STRATEGY_DEPENDENT"},
                        new String[] {"3", "fold", "OUTSIDE_LIMIT"},
                        new String[] {"4", "call", "WITHIN_LIMIT"},
                        new String[] {"5", "raise:22.0", "WITHIN_LIMIT"},
                        new String[] {"6", "raise:22.0", "WITHIN_LIMIT"})) {
            int index = Integer.parseInt(item[0]);
            mvc.perform(
                            post(BASE + "/grade")
                                    .contentType("application/json")
                                    .content(grade(index, item[1])))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.collectionHash").value(PIN))
                    .andExpect(jsonPath("$.questionIndex").value(index))
                    .andExpect(
                            jsonPath("$.decision.reportHash")
                                    .value(collection.question(index).decision().reportHash()))
                    .andExpect(jsonPath("$.decision.selected.classification").value(item[2]))
                    .andExpect(jsonPath("$.decision.alternatives.length()").value(3))
                    .andExpect(jsonPath("$.decision.accuracy").doesNotExist());
        }
    }

    @Test
    void reviewMatchesTheActualOfflineReplayFormatAndCountsDistinctQuestions() throws Exception {
        var saved =
                SixMaxSuppliedIntervalCollection.read(
                        IntervalResearchControllerTest.fixture(
                                "supplied-seven-question-interval-collection-review.json"));
        var choices =
                saved.attempts().stream()
                        .map(
                                a ->
                                        Map.of(
                                                "questionIndex",
                                                a.questionIndex(),
                                                "reportHash",
                                                a.feedback().decision().reportHash(),
                                                "action",
                                                a.action()))
                        .toList();
        var response =
                mvc.perform(
                                post(BASE + "/review")
                                        .contentType("application/json")
                                        .content(
                                                SixMaxTextureStudy.json(
                                                        Map.of(
                                                                "collectionHash",
                                                                PIN,
                                                                "choices",
                                                                choices))))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.counts.distinctQuestionsAttempted").value(7))
                        .andExpect(jsonPath("$.counts.withinLimit").value(6))
                        .andExpect(jsonPath("$.counts.outsideLimit").value(1))
                        .andExpect(jsonPath("$.counts.strategyDependent").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        var actual =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response, SixMaxSuppliedIntervalCollection.Review.class);
        assertEquals(saved, actual);
        assertEquals(saved, collection.replay(actual));
        var repeated = Collections.nCopies(100, choices.getFirst());
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(
                                        SixMaxTextureStudy.json(
                                                Map.of(
                                                        "collectionHash",
                                                        PIN,
                                                        "choices",
                                                        repeated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.distinctQuestionsAttempted").value(1));
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content("{\"collectionHash\":\"" + PIN + "\",\"choices\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.distinctQuestionsAttempted").value(0));
    }

    @Test
    void stalePinsMissingIndicesIllegalMovesAndSubmittedValuesReject() throws Exception {
        var valid = grade(0, "call");
        for (String bad :
                List.of(
                        valid.replace(PIN, "0".repeat(64)),
                        valid.replace(
                                collection.question(0).decision().reportHash(),
                                collection.question(1).decision().reportHash()),
                        valid.replace("\"questionIndex\":0,", ""),
                        valid.replace("\"questionIndex\":0", "\"questionIndex\":null"),
                        valid.replace("\"questionIndex\":0", "\"questionIndex\":7"),
                        valid.replace("\"questionIndex\":0", "\"questionIndex\":-1"),
                        valid.replace("call", "raise:50.0"),
                        valid.replace("call", "check"),
                        valid.replace("}", ",\"evLossBb\":0}"),
                        valid.replace("}", ",\"manifestPath\":\"elsewhere\"}")))
            mvc.perform(post(BASE + "/grade").contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0").param("collectionHash", "0".repeat(64)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/7").param("collectionHash", PIN))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(BASE + "/questions/not-an-index").param("collectionHash", PIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedNestedChoicesOversizedReviewsAndCallerGradesReject() throws Exception {
        String prefix = "{\"collectionHash\":\"" + PIN + "\",\"choices\":";
        var q = collection.question(0).decision().reportHash();
        String choice = "{\"questionIndex\":0,\"reportHash\":\"" + q + "\",\"action\":\"call\"}";
        for (String bad :
                List.of(
                        prefix + "null}",
                        prefix + "[null]}",
                        prefix + "[{}]}",
                        prefix + "[" + choice.replace("call", "") + "]}",
                        prefix + "[" + choice.replace("}", ",\"grade\":0}") + "]}",
                        prefix + "[" + String.join(",", Collections.nCopies(101, choice)) + "]}",
                        prefix + "[],\"counts\":{\"withinLimit\":99}}"))
            mvc.perform(post(BASE + "/review").contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest());
    }
}
