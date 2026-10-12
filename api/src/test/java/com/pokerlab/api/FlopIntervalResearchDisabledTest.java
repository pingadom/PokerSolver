package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxSuppliedFlopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FlopIntervalResearchController.class)
@Import(FlopIntervalResearchConfiguration.class)
class FlopIntervalResearchDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoIntervalRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxSuppliedFlopTrainer.class).isEmpty());
        for (String route : new String[] {"", "/question"})
            mvc.perform(get(FlopIntervalResearchControllerTest.BASE + route))
                    .andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(FlopIntervalResearchControllerTest.BASE + route)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledConfigurationRequiresAllThreeOperatorSettings() {
        var configuration = new FlopIntervalResearchConfiguration();
        for (String[] settings :
                new String[][] {
                    {"", "report", "pin"}, {"request", "", "pin"}, {"request", "report", ""}
                })
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            configuration.flopIntervalResearchTrainer(
                                    settings[0], settings[1], settings[2]));
    }
}
