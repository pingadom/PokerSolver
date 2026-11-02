package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxRootPreflopDrill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RootPreflopDrillController.class)
@Import(RootPreflopDrillConfiguration.class)
class RootPreflopDrillDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndExposesNoResearchRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxRootPreflopDrill.Result.class).isEmpty());
        String base = "/api/v1/trainer/research/root-preflop-drill";
        for (String route : new String[] {"", "/questions/0?studyHash=0"})
            mvc.perform(get(base + route)).andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review", "/review/export"})
            mvc.perform(post(base + route).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledStartupRequiresBothOperatorSettingsAndRejectsUnpinnedSource() {
        var configuration = new RootPreflopDrillConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.rootPreflopDrill("", "pin"));
        assertThrows(
                IllegalStateException.class, () -> configuration.rootPreflopDrill("manifest", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration.rootPreflopDrill("nonexistent.json", "0".repeat(64)));
    }
}
