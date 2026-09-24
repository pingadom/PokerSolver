package com.pokerlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RiverResearchController.class,
        properties = "pokerlab.trainer.river-research-enabled=false")
class RiverResearchDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void routeIsAbsentByDefault() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/river")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trainer/research/river/questions/42"))
                .andExpect(status().isNotFound());
    }
}
