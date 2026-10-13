package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokerlab.solver.SixMaxSuppliedFlopFeedback;
import com.pokerlab.solver.SixMaxSuppliedFlopTrainer;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FlopIntervalResearchController.class,
        properties = {
            "pokerlab.trainer.flop-interval-research-enabled=true",
            "spring.jackson.deserialization.fail-on-unknown-properties=true"
        })
@Import(FlopIntervalResearchConfiguration.class)
class FlopIntervalResearchControllerTest {
    static final String PIN = "49b1be1e91bb0ae7cae6a3171f4e15e516fc0a52be80efabc6d281e8ec5863d8";
    static final String BASE = "/api/v1/trainer/research/flop-interval";
    @Autowired MockMvc mvc;
    @Autowired SixMaxSuppliedFlopTrainer trainer;

    static Path fixture(String name) {
        var path = Path.of("..", "docs", "data", name);
        if (!Files.isRegularFile(path)) path = Path.of("docs", "data", name);
        return path.toAbsolutePath().normalize();
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add(
                "pokerlab.trainer.flop-interval-request-path",
                () -> fixture("supplied-four-button-hand-flop-feedback-request.json").toString());
        registry.add(
                "pokerlab.trainer.flop-interval-report-path",
                () -> fixture("supplied-four-button-hand-flop-feedback-report.json.gz").toString());
        registry.add("pokerlab.trainer.flop-interval-report-hash", () -> PIN);
    }

    @Test
    void pinnedPhysicalStartupServesSixSeatQuestionWithBoardAndScopedProvenance() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.reportHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(1))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(
                        jsonPath("$.sourceHistoryReachStatus")
                                .value("UNAVAILABLE_NO_SOURCE_POLICY"))
                .andExpect(jsonPath("$.evScope").value(SixMaxSuppliedFlopFeedback.EV_SCOPE));
        mvc.perform(get(BASE + "/question"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.context.hero").value("BB"))
                .andExpect(jsonPath("$.context.heroCards").value("7d 7h"))
                .andExpect(jsonPath("$.context.board[0]").value("2s"))
                .andExpect(jsonPath("$.context.board[1]").value("8s"))
                .andExpect(jsonPath("$.context.board[2]").value("Ts"))
                .andExpect(jsonPath("$.context.players.length()").value(6))
                .andExpect(jsonPath("$.context.potBb").value(6.5))
                .andExpect(jsonPath("$.context.betSizeBb").value(3.25))
                .andExpect(jsonPath("$.context.toCallBb").value(0))
                .andExpect(jsonPath("$.bounds").doesNotExist())
                .andExpect(jsonPath("$.prior").doesNotExist())
                .andExpect(jsonPath("$.moves").doesNotExist());
    }

    @Test
    void bothLegalActionsReturnCanonicalOwnedRangesWithNoClientPayoffInput() throws Exception {
        for (String action : List.of("check", "bet")) {
            var expected = trainer.grade(trainer.question(), action);
            var response =
                    mvc.perform(
                                    post(BASE + "/grade")
                                            .contentType("application/json")
                                            .content(
                                                    "{\"reportHash\":\""
                                                            + PIN
                                                            + "\",\"action\":\""
                                                            + action
                                                            + "\"}"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", containsString("no-store")))
                            .andExpect(
                                    jsonPath("$.selected.classification")
                                            .value(expected.selected().classification().name()))
                            .andExpect(jsonPath("$.alternatives.length()").value(2))
                            .andExpect(jsonPath("$.trainerAdmission").value(false))
                            .andExpect(
                                    jsonPath("$.evScope")
                                            .value(SixMaxSuppliedFlopFeedback.EV_SCOPE))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(
                    expected,
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(response, SixMaxSuppliedFlopTrainer.Feedback.class));
        }
    }

    @Test
    void stalePinsIllegalMovesAndSubmittedContextOrEvAreRejected() throws Exception {
        String valid = "{\"reportHash\":\"" + PIN + "\",\"action\":\"check\"}";
        for (String body :
                List.of(
                        valid.replace(PIN, "0".repeat(64)),
                        valid.replace("check", "call"),
                        valid.replace("check", "raise:100.0"),
                        valid.replace("check", ""),
                        valid.replace("check", "x".repeat(33)),
                        valid.replace("\"check\"", "null"),
                        valid.replace("}", ",\"evLossBb\":0}"),
                        valid.replace("}", ",\"heroCards\":\"Ah Ad\"}"),
                        valid.replace("}", ",\"requestPath\":\"other.json\"}")))
            mvc.perform(post(BASE + "/grade").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void canonicalSavedReviewAndEmptyReviewAreReconstructed() throws Exception {
        String response =
                mvc.perform(
                                post(BASE + "/review")
                                        .contentType("application/json")
                                        .content(
                                                "{\"reportHash\":\""
                                                        + PIN
                                                        + "\",\"actions\":[\"check\",\"bet\"]}"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andExpect(
                                jsonPath("$.schemaVersion").value(SixMaxSuppliedFlopTrainer.SCHEMA))
                        .andExpect(jsonPath("$.counts.withinLimit").value(1))
                        .andExpect(jsonPath("$.counts.outsideLimit").value(1))
                        .andExpect(jsonPath("$.counts.strategyDependent").value(0))
                        .andExpect(jsonPath("$.accuracy").doesNotExist())
                        .andExpect(jsonPath("$.totalEvLossBb").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(
                SixMaxSuppliedFlopTrainer.read(
                        fixture("supplied-four-button-hand-flop-drill.json")),
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response, SixMaxSuppliedFlopTrainer.Review.class));
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content("{\"reportHash\":\"" + PIN + "\",\"actions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(0));
    }

    @Test
    void malformedAndOversizedReviewsCannotSupplyTheirOwnGrades() throws Exception {
        String prefix = "{\"reportHash\":\"" + PIN + "\",\"actions\":";
        for (String body :
                List.of(
                        prefix + "null}",
                        prefix + "[null]}",
                        prefix + "[\"call\"]}",
                        prefix + "[\"check\"],\"counts\":{\"withinLimit\":99}}",
                        prefix.replace(PIN, "0".repeat(64)) + "[]}",
                        prefix
                                + "["
                                + String.join(",", java.util.Collections.nCopies(101, "\"check\""))
                                + "]}"))
            mvc.perform(post(BASE + "/review").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
    }
}
