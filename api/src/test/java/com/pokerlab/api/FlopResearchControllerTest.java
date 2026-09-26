package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.FlopTurnRiverHandSession;
import com.pokerlab.solver.FlopTurnRiverPackBuilder;
import com.pokerlab.solver.FlopTurnRiverSpot;
import com.pokerlab.solver.FlopTurnRiverValidationSpot;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FlopResearchController.class,
        properties = "pokerlab.trainer.flop-research-enabled=true")
@Import(FlopResearchControllerTest.TestPack.class)
class FlopResearchControllerTest {
    private static final String BASE = "/api/v1/trainer/research/flop";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPack {
        @Bean
        FlopTurnRiverHandSession flopTurnRiverHandSession() {
            FlopTurnRiverSpot fixture = FlopTurnRiverValidationSpot.create().withFullTurnDeck();
            FlopTurnRiverSpot small =
                    new FlopTurnRiverSpot(
                            fixture.flop(),
                            fixture.potBb(),
                            fixture.remainingStackBb(),
                            fixture.flopBetBb(),
                            fixture.turnBetBb(),
                            fixture.riverBetBb(),
                            List.of(
                                    fixture.firstRange().stream()
                                            .filter(combo -> combo.key().equals("Ah As"))
                                            .findFirst()
                                            .orElseThrow()),
                            List.of(fixture.secondRange().getFirst()),
                            fixture.turnCandidates());
            return new FlopTurnRiverHandSession(
                    FlopTurnRiverPackBuilder.generate(small, 3, "2026-09-26T12:00:00Z", 100));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FlopTurnRiverHandSession session;

    @Test
    void metadataAndInitialHandAreValidationOnlyAndPrivate() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.packHash").value(session.packHash()))
                .andExpect(jsonPath("$.publicationStatus").value("VALIDATION_ONLY"))
                .andExpect(jsonPath("$.chanceModel").value("FULL_PHYSICAL_DECK"))
                .andExpect(jsonPath("$.gameGapBb").isNumber());
        mvc.perform(
                        post(BASE + "/hands/replay")
                                .contentType("application/json")
                                .content(body(session.packHash(), "[]")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.street").value("FLOP"))
                .andExpect(jsonPath("$.heroCombo").value("Ah As"))
                .andExpect(jsonPath("$.opponentCombo").value(nullValue()))
                .andExpect(jsonPath("$.turn").value(nullValue()))
                .andExpect(jsonPath("$.legalActions.length()").value(2))
                .andExpect(jsonPath("$.feedback.length()").value(0));
    }

    @Test
    void rejectsStalePackIllegalActionAndClientSuppliedEv() throws Exception {
        mvc.perform(
                        post(BASE + "/hands/replay")
                                .contentType("application/json")
                                .content(body("0".repeat(64), "[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/hands/replay")
                                .contentType("application/json")
                                .content(body(session.packHash(), "[\"c\"]")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/hands/replay")
                                .contentType("application/json")
                                .content(
                                        body(session.packHash(), "[]")
                                                .replace(
                                                        "\"actions\":[]",
                                                        "\"actions\":[],\"evLossBb\":0")))
                .andExpect(status().isBadRequest());
    }

    private static String body(String packHash, String actions) {
        return "{\"seed\":\"42\",\"packHash\":\""
                + packHash
                + "\",\"heroPlayer\":0,\"actions\":"
                + actions
                + "}";
    }
}
