package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = SixMaxPreflopResearchController.class,
        properties = "pokerlab.trainer.sixmax-preflop-research-enabled=true")
@Import(SixMaxPreflopResearchControllerTest.TestPack.class)
class SixMaxPreflopResearchControllerTest {
    private static final String BASE = "/api/v1/trainer/research/sixmax-preflop";
    private static final ObjectMapper JSON = new ObjectMapper();

    static Path fixture() {
        var path =
                Path.of(
                        "..",
                        "solver",
                        "src",
                        "test",
                        "resources",
                        "six-seat-full-round-pack.json");
        return Files.isRegularFile(path)
                ? path
                : Path.of("solver", "src", "test", "resources", "six-seat-full-round-pack.json");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        SixMaxPreflopResearchService sixMaxPreflopResearchService() throws Exception {
            return new SixMaxPreflopResearchConfiguration()
                    .sixMaxPreflopResearchService(fixture().toString());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired SixMaxPreflopResearchService service;

    @Test
    void exposesAssumptionsAndPublicQuestionsWithoutHiddenHandsOrAnswerValues() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(6))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.continuationModel").value("MANDATORY_CHECKDOWN"))
                .andExpect(jsonPath("$.chanceModel").value("EXACT_RANGE_PRODUCT"))
                .andExpect(jsonPath("$.raiseToBb[0]").value(3))
                .andExpect(jsonPath("$.raiseToBb[1]").value(100))
                .andExpect(jsonPath("$.maximumPayoffStandardErrorBb").value(0))
                .andExpect(jsonPath("$.sessionLength").value(10));
        mvc.perform(get(BASE + "/sessions/9223372036854775807/questions/9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sessionSeed").value("9223372036854775807"))
                .andExpect(jsonPath("$.packHash").value(service.metadata().packHash()))
                .andExpect(
                        jsonPath("$.heroCombo")
                                .value(service.question(Long.MAX_VALUE, 9).heroCombo()))
                .andExpect(jsonPath("$.priorActions").isArray())
                .andExpect(jsonPath("$.legalActions").isArray())
                .andExpect(jsonPath("$.actionEvBb").doesNotExist())
                .andExpect(jsonPath("$.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.solution").doesNotExist())
                .andExpect(jsonPath("$.seed").doesNotExist());
    }

    @Test
    void gradesAndReviewsOnlyReplayedDecisionsAndRejectsForgedInputs() throws Exception {
        String hash = service.metadata().packHash();
        String action = service.question(711, 0).legalActions().getFirst();
        String grade =
                JSON.writeValueAsString(
                        Map.of(
                                "sessionSeed",
                                "711",
                                "index",
                                0,
                                "packHash",
                                hash,
                                "action",
                                action));
        mvc.perform(post(BASE + "/grade").contentType("application/json").content(grade))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.selectedAction").value(action))
                .andExpect(jsonPath("$.feedback.evLossBb").isNumber())
                .andExpect(jsonPath("$.feedback.actionPayoffStandardErrorBb").isMap());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace(hash, "0".repeat(64))))
                .andExpect(status().isBadRequest());
        String illegal =
                JSON.writeValueAsString(
                        Map.of(
                                "sessionSeed",
                                "711",
                                "index",
                                0,
                                "packHash",
                                hash,
                                "action",
                                "teleport"));
        mvc.perform(post(BASE + "/grade").contentType("application/json").content(illegal))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(
                                        grade.substring(0, grade.length() - 1)
                                                + ",\"evLossBb\":0}"))
                .andExpect(status().isBadRequest());
        var actions = new ArrayList<String>();
        for (int index = 0; index < 10; index++)
            actions.add(service.question(711, index).legalActions().getFirst());
        String review =
                JSON.writeValueAsString(
                        Map.of("sessionSeed", "711", "packHash", hash, "actions", actions));
        var expected = service.review(711, hash, actions);
        mvc.perform(post(BASE + "/review").contentType("application/json").content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(10))
                .andExpect(jsonPath("$.totalEvLossBb").value(expected.totalEvLossBb()))
                .andExpect(jsonPath("$.averageEvLossBb").value(expected.averageEvLossBb()));
        actions.removeLast();
        String incomplete =
                JSON.writeValueAsString(
                        Map.of("sessionSeed", "711", "packHash", hash, "actions", actions));
        mvc.perform(post(BASE + "/review").contentType("application/json").content(incomplete))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidSeedsAndIndices() throws Exception {
        for (String suffix :
                new String[] {
                    "/sessions/711/questions/-1",
                    "/sessions/711/questions/10",
                    "/sessions/9223372036854775808/questions/0",
                    "/sessions/not-a-seed/questions/0"
                }) mvc.perform(get(BASE + suffix)).andExpect(status().isBadRequest());
    }
}
