package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxRootHandBankDrill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RootHandBankDrillController.class,
        properties = "pokerlab.trainer.root-hand-bank-enabled=false")
@Import(RootHandBankDrillConfiguration.class)
class RootHandBankDrillDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndExposesNoResearchRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxRootHandBankDrill.Result.class).isEmpty());
        String base = "/api/v1/trainer/research/root-hand-bank-drill";
        for (String route : new String[] {"", "/questions/0?studyHash=0"})
            mvc.perform(get(base + route)).andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review", "/review/export"})
            mvc.perform(post(base + route).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledStartupRequiresBothOperatorSettingsAndRejectsUnpinnedSource() {
        var configuration = new RootHandBankDrillConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.rootHandBankDrill("", "pin"));
        assertThrows(
                IllegalStateException.class, () -> configuration.rootHandBankDrill("manifest", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration.rootHandBankDrill("nonexistent.json", "0".repeat(64)));
    }

    @Test
    void independentConfigurationsHaveDistinctBeanNames() {
        assertFalse(
                java.util.Arrays.stream(RootHandBankDrillConfiguration.class.getDeclaredMethods())
                        .filter(
                                m ->
                                        m.isAnnotationPresent(
                                                org.springframework.context.annotation.Bean.class))
                        .anyMatch(
                                m ->
                                        java.util.Set.of(
                                                        "rootPreflopDrill",
                                                        "minimumPreflopDrill",
                                                        "sequencePreflopDrill",
                                                        "batchPreflopDrill",
                                                        "sevensPreflopDrill")
                                                .contains(m.getName())));
    }
}
