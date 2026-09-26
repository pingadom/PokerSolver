package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.RiverPackBuilder;
import com.pokerlab.solver.RiverResearchTrainer;
import com.pokerlab.solver.RiverValidationSpot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RiverResearchController.class,
        properties = "pokerlab.trainer.river-research-enabled=true")
@Import(RiverResearchControllerTest.TestPack.class)
class RiverResearchControllerTest {
    private static final String BASE = "/api/v1/trainer/research/river";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        RiverResearchTrainer riverResearchTrainer() {
            return new RiverResearchTrainer(
                    RiverPackBuilder.generate(
                            RiverValidationSpot.create(), 200, "2026-09-24T18:00:00Z"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RiverResearchTrainer trainer;

    @Test
    void metadataAndQuestionExposeOnlyPublicGameAndHeroCards() throws Exception {
        var expected = trainer.question(42);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.packHash").value(trainer.packHash()))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.availableQuestions").value(12))
                .andExpect(jsonPath("$.rakeModel").value("NO_RAKE"));
        mvc.perform(get(BASE + "/questions/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed").value("42"))
                .andExpect(jsonPath("$.question.packHash").value(trainer.packHash()))
                .andExpect(jsonPath("$.question.heroCombo").value(expected.heroCombo()))
                .andExpect(jsonPath("$.question.publicHistory").value(expected.publicHistory()))
                .andExpect(jsonPath("$.question.legalActions.length()").value(2))
                .andExpect(jsonPath("$.question.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.question.actionEvBb").doesNotExist());
    }

    @Test
    void gradeRecomputesEvAndRejectsStalePackIllegalActionAndSubmittedValues() throws Exception {
        var question = trainer.question(42);
        String action = question.legalActions().get(0);
        String body =
                "{\"seed\":\"42\",\"packHash\":\""
                        + trainer.packHash()
                        + "\",\"action\":\""
                        + action
                        + "\"}";
        mvc.perform(post(BASE + "/grade").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.heroCombo").value(question.heroCombo()))
                .andExpect(jsonPath("$.selectedAction").value(action))
                .andExpect(jsonPath("$.evLossBb").isNumber());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(body.replace(trainer.packHash(), "0".repeat(64))))
                .andExpect(status().isBadRequest());
        String illegal = question.legalActions().contains("c") ? "b" : "c";
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(
                                        body.replace(
                                                "\"" + action + "\"}", "\"" + illegal + "\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(body.substring(0, body.length() - 1) + ",\"evLossBb\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/not-a-seed")).andExpect(status().isBadRequest());
    }
}
