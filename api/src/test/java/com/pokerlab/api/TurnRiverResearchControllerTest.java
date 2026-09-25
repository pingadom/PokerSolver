package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.TurnRiverPackBuilder;
import com.pokerlab.solver.TurnRiverResearchTrainer;
import com.pokerlab.solver.TurnRiverValidationSpot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TurnRiverResearchController.class,
        properties = "pokerlab.trainer.turn-river-research-enabled=true")
@Import(TurnRiverResearchControllerTest.TestPack.class)
class TurnRiverResearchControllerTest {
    private static final String BASE = "/api/v1/trainer/research/turn-river";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        TurnRiverResearchTrainer turnRiverResearchTrainer() {
            return new TurnRiverResearchTrainer(
                    TurnRiverPackBuilder.generate(
                            TurnRiverValidationSpot.create(), 100, "2026-09-25T12:00:00Z"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired TurnRiverResearchTrainer trainer;

    @Test
    void metadataAndQuestionDoNotExposeOpponentCardsOrEvs() throws Exception {
        var expected = trainer.question(42);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.packHash").value(trainer.packHash()))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.availableTurnQuestions").isNumber())
                .andExpect(jsonPath("$.availableRiverQuestions").isNumber());
        mvc.perform(get(BASE + "/questions/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed").value("42"))
                .andExpect(jsonPath("$.question.heroCombo").value(expected.heroCombo()))
                .andExpect(jsonPath("$.question.street").value(expected.street()))
                .andExpect(jsonPath("$.question.legalActions.length()").value(2))
                .andExpect(jsonPath("$.question.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.question.actionEvBb").doesNotExist());
    }

    @Test
    void gradeRecomputesEvAndRejectsStalePackAndClientEv() throws Exception {
        var question = trainer.question(42);
        String action = question.legalActions().getFirst();
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
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(body.substring(0, body.length() - 1) + ",\"evLossBb\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/nope")).andExpect(status().isBadRequest());
    }
}
