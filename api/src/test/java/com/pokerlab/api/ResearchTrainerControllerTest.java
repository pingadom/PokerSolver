package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.CfrSolver;
import com.pokerlab.solver.DiverseValidationSpot;
import com.pokerlab.solver.PreflopDrillSession;
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
                .andExpect(jsonPath("$.packHash").value(trainer.packHash()))
                .andExpect(jsonPath("$.question.spotHash").value(expected.spotHash()))
                .andExpect(jsonPath("$.question.heroCombo").value(expected.heroCombo()))
                .andExpect(jsonPath("$.question.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.question.legalActions[0]").value("SHOVE"))
                .andExpect(jsonPath("$.question.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.question.shoveEvBb").doesNotExist())
                .andExpect(jsonPath("$.question.foldEvBb").doesNotExist());
    }

    @Test
    void gradeRecomputesQuestionAndRejectsChangedPackOrSubmittedEv() throws Exception {
        String packHash = trainer.packHash();
        String body = "{\"seed\":\"42\",\"packHash\":\"" + packHash + "\",\"action\":\"FOLD\"}";
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
                                .content(body.replace(packHash, "0".repeat(64))))
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
    void metadataAndTenDecisionSessionRemainBoundToOnePack() throws Exception {
        mvc.perform(get("/api/v1/trainer/research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packHash").value(trainer.packHash()))
                .andExpect(jsonPath("$.heroRange.length()").value(8))
                .andExpect(jsonPath("$.opponentRange.length()").value(7))
                .andExpect(jsonPath("$.sessionLength").value(10));
        var session = new PreflopDrillSession(trainer);
        mvc.perform(get("/api/v1/trainer/research/sessions/9223372036854775807/questions/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionSeed").value("9223372036854775807"))
                .andExpect(jsonPath("$.packHash").value(trainer.packHash()))
                .andExpect(
                        jsonPath("$.question.heroCombo")
                                .value(session.question(Long.MAX_VALUE, 9).heroCombo()))
                .andExpect(jsonPath("$.question.opponentCards").doesNotExist())
                .andExpect(jsonPath("$.question.shoveEvBb").doesNotExist());
        String grade =
                "{\"sessionSeed\":\"42\",\"index\":0,\"packHash\":\""
                        + trainer.packHash()
                        + "\",\"action\":\"SHOVE\"}";
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/grade")
                                .contentType("application/json")
                                .content(grade))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.feedback.heroCombo").value(session.question(42, 0).heroCombo()))
                .andExpect(jsonPath("$.feedback.evLossBb").isNumber());
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/grade")
                                .contentType("application/json")
                                .content(grade.replace(trainer.packHash(), "0".repeat(64))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/grade")
                                .contentType("application/json")
                                .content(grade.replace("\"SHOVE\"", "\"CALL\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/grade")
                                .contentType("application/json")
                                .content(grade.replace("}", ",\"evLossBb\":0}")))
                .andExpect(status().isBadRequest());
        String review =
                "{\"sessionSeed\":\"42\",\"packHash\":\""
                        + trainer.packHash()
                        + "\",\"actions\":["
                        + "\"FOLD\",".repeat(9)
                        + "\"FOLD\"]}";
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/review")
                                .contentType("application/json")
                                .content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(10))
                .andExpect(
                        jsonPath("$.totalEvLossBb")
                                .value(
                                        session.review(
                                                        42,
                                                        java.util.Collections.nCopies(
                                                                10, PreflopTrainer.Action.FOLD))
                                                .totalEvLossBb()));
        mvc.perform(
                        post("/api/v1/trainer/research/sessions/review")
                                .contentType("application/json")
                                .content(review.replace("\"FOLD\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trainer/research/sessions/42/questions/10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidSeedWithoutLeakingInput() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/questions/not-a-seed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seed must be a signed 64-bit integer"));
    }
}
