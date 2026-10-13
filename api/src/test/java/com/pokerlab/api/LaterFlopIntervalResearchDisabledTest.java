package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxLaterFlopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LaterFlopIntervalResearchController.class)
@Import(LaterFlopIntervalResearchConfiguration.class)
class LaterFlopIntervalResearchDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoIntervalRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxLaterFlopTrainer.class).isEmpty());
        for (String route : new String[] {"", "/question"})
            mvc.perform(get(LaterFlopIntervalResearchControllerTest.BASE + route))
                    .andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(LaterFlopIntervalResearchControllerTest.BASE + route)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledConfigurationRequiresAllThreeOperatorSettings() {
        var configuration = new LaterFlopIntervalResearchConfiguration();
        for (String[] settings :
                new String[][] {
                    {"", "report", "pin"}, {"request", "", "pin"}, {"request", "report", ""}
                })
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            configuration.laterFlopIntervalResearchTrainer(
                                    settings[0], settings[1], settings[2]));
    }
}
