package com.pokerlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SixMaxPreflopResearchController.class)
class SixMaxPreflopResearchDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void routesAreAbsentByDefault() throws Exception {
        mvc.perform(get("/api/v1/trainer/research/sixmax-preflop"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trainer/research/sixmax-preflop/sessions/711/questions/0"))
                .andExpect(status().isNotFound());
    }
}
