package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {RootHandBankDrillController.class, RootHandBankStartupController.class},
        properties = {
            "pokerlab.trainer.root-hand-bank-enabled=false",
            "pokerlab.trainer.root-hand-bank-startup-source=INVALID"
        })
@Import(RootHandBankDrillConfiguration.class)
class RootHandBankStartupDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void absentOptInDoesNoSourceWorkAndExposesNoProvenance() throws Exception {
        assertTrue(context.getBeansOfType(RootHandBankStartupSource.class).isEmpty());
        assertTrue(
                context.getBeansOfType(com.pokerlab.solver.SixMaxRootHandBankDrill.Result.class)
                        .isEmpty());
        mvc.perform(get("/api/v1/trainer/research/root-hand-bank-drill/startup"))
                .andExpect(status().isNotFound());
    }
}
