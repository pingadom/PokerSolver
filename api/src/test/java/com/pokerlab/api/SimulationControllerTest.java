package com.pokerlab.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokerlab.shared.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SimulationStore store;
    @MockitoBean SimulationCache cache;
    private static final String VALID =
            """
        {"players":[{"name":"AA","cards":["AS","AH"]},{"name":"KK","cards":["KS","KH"]}],"board":[],"iterations":1000,"seed":42}
        """;

    @Test
    void createsValidatedSimulation() throws Exception {
        var id = UUID.randomUUID();
        when(store.create(any())).thenReturn(id);
        mvc.perform(post("/api/v1/simulations").contentType("application/json").content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/simulations/" + id))
                .andExpect(jsonPath("$.simulationId").value(id.toString()));
    }

    @Test
    void rejectsInvalidCardsAndIterationsBeforePersistence() throws Exception {
        for (String request :
                new String[] {
                    VALID.replace("KS", "AS"), VALID.replace("1000", "0"), VALID.replace("AS", "XX")
                }) {
            mvc.perform(
                            post("/api/v1/simulations")
                                    .contentType("application/json")
                                    .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(store);
    }

    @Test
    void missingSimulationIs404() throws Exception {
        when(cache.status(any())).thenThrow(new SimulationNotFoundException(UUID.randomUUID()));
        mvc.perform(get("/api/v1/simulations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
