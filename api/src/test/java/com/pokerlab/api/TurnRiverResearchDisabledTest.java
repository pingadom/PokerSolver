package com.pokerlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TurnRiverResearchController.class,
        properties = "pokerlab.trainer.turn-river-research-enabled=false")
class TurnRiverResearchDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void routeIsAbsentByDefault() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/turn-river")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trainer/research/turn-river/questions/42"))
                .andExpect(status().isNotFound());
    }
}
