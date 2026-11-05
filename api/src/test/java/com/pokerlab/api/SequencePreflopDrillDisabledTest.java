package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxSequencePreflopDrill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = SequencePreflopDrillController.class,
        properties = "pokerlab.trainer.root-sequence-preflop-enabled=false")
@Import(SequencePreflopDrillConfiguration.class)
class SequencePreflopDrillDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndExposesNoResearchRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxSequencePreflopDrill.Result.class).isEmpty());
        String base = "/api/v1/trainer/research/root-sequence-preflop-drill";
        for (String route : new String[] {"", "/questions/0?studyHash=0"})
            mvc.perform(get(base + route)).andExpect(status().isNotFound());
        for (String route : new String[] {"/grade", "/review", "/review/export"})
            mvc.perform(post(base + route).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void enabledStartupRequiresBothOperatorSettingsAndRejectsUnpinnedSource() {
        var configuration = new SequencePreflopDrillConfiguration();
        assertThrows(
                IllegalStateException.class, () -> configuration.sequencePreflopDrill("", "pin"));
        assertThrows(
                IllegalStateException.class,
                () -> configuration.sequencePreflopDrill("manifest", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration.sequencePreflopDrill("nonexistent.json", "0".repeat(64)));
    }

    @Test
    void independentConfigurationsHaveDistinctBeanNames() {
        assertFalse(
                java.util.Arrays.stream(
                                SequencePreflopDrillConfiguration.class.getDeclaredMethods())
                        .filter(
                                m ->
                                        m.isAnnotationPresent(
                                                org.springframework.context.annotation.Bean.class))
                        .anyMatch(
                                m ->
                                        java.util.Set.of("rootPreflopDrill", "minimumPreflopDrill")
                                                .contains(m.getName())));
    }
}
