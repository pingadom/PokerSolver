package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxRefinedFlopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RefinedFlopIntervalResearchController.class)
@Import(RefinedFlopIntervalResearchConfiguration.class)
class RefinedFlopIntervalResearchDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoIntervalRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxRefinedFlopTrainer.class).isEmpty());
        for (String route : new String[] {"", "/question"})
            mvc.perform(get(RefinedFlopIntervalResearchControllerTest.BASE + route))
                    .andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(RefinedFlopIntervalResearchControllerTest.BASE + route)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledConfigurationRequiresAllThreeOperatorSettings() {
        var configuration = new RefinedFlopIntervalResearchConfiguration();
        for (String[] settings :
                new String[][] {
                    {"", "report", "pin"}, {"request", "", "pin"}, {"request", "report", ""}
                })
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            configuration.refinedFlopIntervalResearchTrainer(
                                    settings[0], settings[1], settings[2]));
    }
}
