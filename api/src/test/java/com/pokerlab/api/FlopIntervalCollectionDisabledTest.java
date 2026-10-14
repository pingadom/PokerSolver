package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokerlab.solver.SixMaxFlopIntervalCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FlopIntervalCollectionController.class)
@Import(FlopIntervalCollectionConfiguration.class)
class FlopIntervalCollectionDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInCreatesNoSolverBeanAndNoCollectionRoutes() throws Exception {
        assertTrue(context.getBeansOfType(SixMaxFlopIntervalCollection.class).isEmpty());
        for (String path : new String[] {"", "/questions/0"})
            mvc.perform(get(FlopIntervalCollectionControllerTest.BASE + path))
                    .andExpect(status().isNotFound());
        for (String path : new String[] {"/grade", "/review"})
            mvc.perform(
                            post(FlopIntervalCollectionControllerTest.BASE + path)
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isNotFound());
    }

    @Test
    void explicitOptInRequiresBothOperatorSettings() {
        var config = new FlopIntervalCollectionConfiguration();
        assertThrows(IllegalStateException.class, () -> config.flopIntervalCollection("", "pin"));
        assertThrows(
                IllegalStateException.class, () -> config.flopIntervalCollection("manifest", ""));
    }
}
