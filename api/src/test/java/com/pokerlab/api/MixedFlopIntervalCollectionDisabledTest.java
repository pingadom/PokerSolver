package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxMixedFlopIntervalCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MixedFlopIntervalCollectionController.class)
@Import(MixedFlopIntervalCollectionConfiguration.class)
class MixedFlopIntervalCollectionDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoCollectionRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxMixedFlopIntervalCollection.class).isEmpty());
        for (String path : new String[] {"", "/questions/0"})
            mvc.perform(get(MixedFlopIntervalCollectionControllerTest.BASE + path))
                    .andExpect(status().isNotFound());
        for (String path : new String[] {"/grade", "/review", "/review/export"})
            mvc.perform(
                            post(MixedFlopIntervalCollectionControllerTest.BASE + path)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void explicitOptInRequiresAllOperatorSettings() {
        var config = new MixedFlopIntervalCollectionConfiguration();
        assertThrows(
                IllegalStateException.class, () -> config.mixedFlopIntervalCollection("", "pin"));
        assertThrows(
                IllegalStateException.class,
                () -> config.mixedFlopIntervalCollection("manifest", ""));
    }
}
