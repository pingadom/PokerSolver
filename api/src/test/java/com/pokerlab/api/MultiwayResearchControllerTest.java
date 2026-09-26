package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MultiwayResearchController.class,
        properties = "pokerlab.trainer.multiway-research-enabled=true")
@Import(MultiwayResearchControllerTest.TestPack.class)
class MultiwayResearchControllerTest {
    private static final String BASE = "/api/v1/trainer/research/multiway";

    static Path fixture() {
        Path module =
                Path.of("..", "solver", "src", "test", "resources", "six-seat-exact-pack.json");
        return Files.isRegularFile(module)
                ? module
                : Path.of("solver", "src", "test", "resources", "six-seat-exact-pack.json");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        MultiwayResearchService multiwayResearchService() throws Exception {
            return new MultiwayResearchConfiguration()
                    .multiwayResearchService(fixture().toString());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MultiwayResearchService service;

    @Test
    void metadataAndQuestionsExposeModelLimitsWithoutAnswersOrHiddenHands() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(6))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.payoffMethod").value("EXACT_ENUMERATION"))
                .andExpect(jsonPath("$.maximumPayoffStandardErrorBb").value(0))
                .andExpect(jsonPath("$.sessionLength").value(10));
        mvc.perform(get(BASE + "/sessions/9223372036854775807/questions/9?player=5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sessionSeed").value("9223372036854775807"))
                .andExpect(jsonPath("$.actingSeat").value("BB"))
                .andExpect(jsonPath("$.aggressorSeat").value("UTG"))
                .andExpect(jsonPath("$.priorResponses.length()").value(4))
                .andExpect(jsonPath("$.packHash").value(service.metadata().packHash()))
                .andExpect(
                        jsonPath("$.heroCombo")
                                .value(service.question(Long.MAX_VALUE, 9, 5).heroCombo()))
                .andExpect(jsonPath("$.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.callEvBb").doesNotExist())
                .andExpect(jsonPath("$.solution").doesNotExist());
    }

    @Test
    void gradesAndReviewsByReplayingSessionAndRejectsForgedFields() throws Exception {
        String hash = service.metadata().packHash();
        String grade =
                "{\"sessionSeed\":\"42\",\"index\":0,\"player\":0,\"packHash\":\""
                        + hash
                        + "\",\"action\":\"CALL\"}";
        mvc.perform(post(BASE + "/grade").contentType("application/json").content(grade))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.selectedAction").value("CALL"))
                .andExpect(jsonPath("$.feedback.evLossBb").isNumber());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace(hash, "0".repeat(64))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace("\"CALL\"", "\"RAISE\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace("}", ",\"evLossBb\":0}")))
                .andExpect(status().isBadRequest());
        String review =
                "{\"sessionSeed\":\"42\",\"player\":0,\"packHash\":\""
                        + hash
                        + "\",\"actions\":["
                        + "\"FOLD\",".repeat(9)
                        + "\"FOLD\"]}";
        mvc.perform(post(BASE + "/review").contentType("application/json").content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(10))
                .andExpect(jsonPath("$.totalEvLossBb").isNumber())
                .andExpect(jsonPath("$.averageEvLossBb").isNumber());
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(review.replace("\"FOLD\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidSeedsIndicesAndSeats() throws Exception {
        for (String path :
                new String[] {
                    "/sessions/42/questions/10",
                    "/sessions/42/questions/-1",
                    "/sessions/42/questions/0?player=6",
                    "/sessions/42/questions/0?player=-1",
                    "/sessions/9223372036854775808/questions/0",
                    "/sessions/not-a-seed/questions/0"
                }) mvc.perform(get(BASE + path)).andExpect(status().isBadRequest());
    }
}
