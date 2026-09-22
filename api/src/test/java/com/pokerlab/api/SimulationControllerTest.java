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
    @Test
    void rejectsFractionalTrialsRatherThanSilentlyTruncating() throws Exception {
        mvc.perform(
                        post("/api/v1/simulations")
                                .contentType("application/json")
                                .content(VALID.replace("1000", "1000.5")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

    @Test
    void resultEndpointDistinguishesProcessingAndTerminalFailure() throws Exception {
        var id = UUID.randomUUID();
        var view = mock(SimulationView.class);
        when(store.get(id)).thenReturn(view);
        for (var state :
                new SimulationStatus[] {SimulationStatus.QUEUED, SimulationStatus.RUNNING}) {
            when(view.status()).thenReturn(state);
            mvc.perform(get("/api/v1/simulations/" + id + "/results"))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Retry-After", "2"));
        }
        for (var state :
                new SimulationStatus[] {SimulationStatus.FAILED, SimulationStatus.CANCELLED}) {
            when(view.status()).thenReturn(state);
            mvc.perform(get("/api/v1/simulations/" + id + "/results"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SIMULATION_TERMINAL"));
        }
    }

    @Test
    void returnsCompletedEquityRatherThanRawShares() throws Exception {
        var id = UUID.randomUUID();
        var view = mock(SimulationView.class);
        when(view.status()).thenReturn(SimulationStatus.COMPLETED);
        when(store.get(id)).thenReturn(view);
        when(cache.result(id))
                .thenReturn(
                        new AggregatedSimulationResult(
                                id,
                                SimulationStatus.COMPLETED,
                                100,
                                java.util.List.of(
                                        new com.pokerlab.core.batch.PlayerResult(
                                                "AA", 75, 0, 25, 75),
                                        new com.pokerlab.core.batch.PlayerResult(
                                                "KK", 25, 0, 75, 25)),
                                25));
        mvc.perform(get("/api/v1/simulations/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrials").value(100))
                .andExpect(jsonPath("$.players[0].equity").value(0.75));
    }

    @Test
    void acceptsFullPrecisionSeedStringsFromBrowser() throws Exception {
        when(store.create(any())).thenReturn(UUID.randomUUID());
        mvc.perform(
                        post("/api/v1/simulations")
                                .contentType("application/json")
                                .content(VALID.replace("42", "\"9223372036854775807\"")))
                .andExpect(status().isAccepted());
        verify(store).create(argThat(config -> config.seed() == Long.MAX_VALUE));
    }

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
