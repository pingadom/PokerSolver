package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.CfrSolver;
import com.pokerlab.solver.DiverseValidationSpot;
import com.pokerlab.solver.PreflopPackBuilder;
import com.pokerlab.solver.PreflopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ResearchTrainerController.class,
        properties = "pokerlab.trainer.research-enabled=true")
@Import(ResearchTrainerControllerTest.TestPack.class)
class ResearchTrainerControllerTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        PreflopTrainer researchTrainer() {
            return new PreflopTrainer(
                    PreflopPackBuilder.generate(
                            DiverseValidationSpot.create(),
                            100,
                            1_000,
                            42,
                            "2026-09-23T12:00:00Z",
                            CfrSolver.Variant.CFR_PLUS));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired PreflopTrainer trainer;

    @Test
    void questionExposesPublicSpotButNoAnswerOrOpponentCards() throws Exception {
        PreflopTrainer.Question expected = trainer.question(42);
        mvc.perform(get("/api/v1/trainer/research/questions/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.seed").value("42"))
                .andExpect(jsonPath("$.question.spotHash").value(expected.spotHash()))
                .andExpect(jsonPath("$.question.heroCombo").value(expected.heroCombo()))
                .andExpect(jsonPath("$.question.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.question.legalActions[0]").value("SHOVE"))
                .andExpect(jsonPath("$.question.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.question.shoveEvBb").doesNotExist())
                .andExpect(jsonPath("$.question.foldEvBb").doesNotExist());
    }

    @Test
    void gradeRecomputesQuestionAndRejectsChangedSpotOrSubmittedEv() throws Exception {
        String spotHash = trainer.question(42).spotHash();
        String body = "{\"seed\":\"42\",\"spotHash\":\"" + spotHash + "\",\"action\":\"FOLD\"}";
        mvc.perform(
                        post("/api/v1/trainer/research/grade")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.heroCombo").value(trainer.question(42).heroCombo()))
                .andExpect(jsonPath("$.selectedAction").value("FOLD"))
                .andExpect(jsonPath("$.evLossBb").isNumber());

        mvc.perform(
                        post("/api/v1/trainer/research/grade")
                                .contentType("application/json")
                                .content(body.replace(spotHash, "0".repeat(64))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(
                        post("/api/v1/trainer/research/grade")
                                .contentType("application/json")
                                .content(body.replace("\"FOLD\"", "\"CALL\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/trainer/research/grade")
                                .contentType("application/json")
                                .content(
                                        body.substring(0, body.length() - 1)
                                                + ",\"shoveEvBb\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidSeedWithoutLeakingInput() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/questions/not-a-seed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seed must be a signed 64-bit integer"));
    }
}
