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
@Import(MultiwaySidePotControllerTest.TestPack.class)
class MultiwaySidePotControllerTest {
    private static final String BASE = "/api/v1/trainer/research/multiway";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        MultiwayResearchService multiwayResearchService() throws Exception {
            Path fixture =
                    Path.of(
                            "..",
                            "solver",
                            "src",
                            "test",
                            "resources",
                            "six-seat-side-pot-pack.json");
            if (!Files.isRegularFile(fixture))
                fixture =
                        Path.of(
                                "solver",
                                "src",
                                "test",
                                "resources",
                                "six-seat-side-pot-pack.json");
            return new MultiwayResearchConfiguration().multiwayResearchService(fixture.toString());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MultiwayResearchService service;

    @Test
    void servesReplayableSidePotDecisionsWithoutHiddenCardsOrClientEvs() throws Exception {
        String hash = service.metadata().packHash();
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packSchema").value("multiway-side-pot-pack/v1"))
                .andExpect(jsonPath("$.stacksBb[0]").value(30))
                .andExpect(jsonPath("$.stacksBb[5]").value(5))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.maximumPayoffStandardErrorBb").value(0));
        mvc.perform(get(BASE + "/sessions/42/questions/0?player=5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.actingSeat").value("BB"))
                .andExpect(jsonPath("$.callCostBb").value(4))
                .andExpect(jsonPath("$.stackBb").value(5))
                .andExpect(jsonPath("$.packHash").value(hash))
                .andExpect(jsonPath("$.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.callEvBb").doesNotExist());
        String grade =
                "{\"sessionSeed\":\"42\",\"index\":0,\"player\":5,\"packHash\":\""
                        + hash
                        + "\",\"action\":\"CALL\"}";
        mvc.perform(post(BASE + "/grade").contentType("application/json").content(grade))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.callEvBb").isNumber())
                .andExpect(jsonPath("$.feedback.foldEvBb").isNumber());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace(hash, "0".repeat(64))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(grade.replace("}", ",\"callEvBb\":999}")))
                .andExpect(status().isBadRequest());
    }
}
