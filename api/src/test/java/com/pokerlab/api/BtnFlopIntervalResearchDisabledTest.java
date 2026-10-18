package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxBtnFlopTrainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BtnFlopIntervalResearchController.class)
@Import(BtnFlopIntervalResearchConfiguration.class)
class BtnFlopIntervalResearchDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoCollectionRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxBtnFlopTrainer.class).isEmpty());
        for (String path : new String[] {"", "/questions/0"})
            mvc.perform(get(BtnFlopIntervalResearchControllerTest.BASE + path))
                    .andExpect(status().isNotFound());
        for (String path : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(BtnFlopIntervalResearchControllerTest.BASE + path)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void explicitOptInRequiresAllOperatorSettings() {
        var config = new BtnFlopIntervalResearchConfiguration();
        assertThrows(
                IllegalStateException.class,
                () -> config.btnFlopIntervalResearchTrainer("", "report", "pin"));
        assertThrows(
                IllegalStateException.class,
                () -> config.btnFlopIntervalResearchTrainer("request", "", "pin"));
        assertThrows(
                IllegalStateException.class,
                () -> config.btnFlopIntervalResearchTrainer("request", "report", ""));
    }
}
