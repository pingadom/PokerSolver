package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxWeakHandFlopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WeakHandFlopIntervalResearchController.class)
@Import(WeakHandFlopIntervalResearchConfiguration.class)
class WeakHandFlopIntervalResearchDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoIntervalRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxWeakHandFlopTrainer.class).isEmpty());
        for (String route : new String[] {"", "/question"})
            mvc.perform(get(WeakHandFlopIntervalResearchControllerTest.BASE + route))
                    .andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(WeakHandFlopIntervalResearchControllerTest.BASE + route)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledConfigurationRequiresAllThreeOperatorSettings() {
        var configuration = new WeakHandFlopIntervalResearchConfiguration();
        for (String[] settings :
                new String[][] {
                    {"", "report", "pin"}, {"request", "", "pin"}, {"request", "report", ""}
                })
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            configuration.weakHandFlopIntervalResearchTrainer(
                                    settings[0], settings[1], settings[2]));
    }
}
