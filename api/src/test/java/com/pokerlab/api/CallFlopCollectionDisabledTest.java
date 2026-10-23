package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxCallFlopCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CallFlopCollectionController.class)
@Import(CallFlopCollectionConfiguration.class)
class CallFlopCollectionDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoIntervalRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxCallFlopCollection.class).isEmpty());
        for (String route :
                new String[] {
                    "",
                    "/questions/0?collectionHash=0000000000000000000000000000000000000000000000000000000000000000"
                })
            mvc.perform(get("/api/v1/trainer/research/flop-interval-collection-v5" + route))
                    .andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review", "/review/export"})
            mvc.perform(
                            post("/api/v1/trainer/research/flop-interval-collection-v5" + route)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledConfigurationRequiresBothOperatorSettings() {
        var configuration = new CallFlopCollectionConfiguration();
        assertThrows(
                IllegalStateException.class, () -> configuration.callFlopCollection("", "pin"));
        assertThrows(
                IllegalStateException.class,
                () -> configuration.callFlopCollection("manifest", ""));
    }
}
