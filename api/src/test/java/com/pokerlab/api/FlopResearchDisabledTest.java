package com.pokerlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FlopResearchController.class,
        properties = "pokerlab.trainer.flop-research-enabled=false")
class FlopResearchDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void routeIsAbsentByDefault() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/flop")).andExpect(status().isNotFound());
    }
}
