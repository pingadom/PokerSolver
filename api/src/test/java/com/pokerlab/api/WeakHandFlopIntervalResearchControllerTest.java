package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokerlab.solver.SixMaxRefinedFlopTrainer;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback;
import com.pokerlab.solver.SixMaxWeakHandFlopTrainer;
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
        controllers = WeakHandFlopIntervalResearchController.class,
        properties = {
            "pokerlab.trainer.weak-hand-flop-interval-research-enabled=true",
            "spring.jackson.deserialization.fail-on-unknown-properties=true"
        })
@Import(WeakHandFlopIntervalResearchConfiguration.class)
class WeakHandFlopIntervalResearchControllerTest {
    static final String PIN = "5dbd0cdeba8a74e6da32dae0bddeb69381a90ba4f72220ab4a9bf92bcc667c56";
    static final String BASE = "/api/v1/trainer/research/weak-hand-flop-interval";
    @Autowired MockMvc mvc;
    @Autowired SixMaxWeakHandFlopTrainer trainer;

    static Path fixture(String name) {
        var path = Path.of("..", "docs", "data", name);
        if (!Files.isRegularFile(path)) path = Path.of("docs", "data", name);
        return path.toAbsolutePath().normalize();
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add(
                "pokerlab.trainer.weak-hand-flop-interval-request-path",
                () -> fixture("supplied-weak-hand-qualified-flop-request.json").toString());
        registry.add(
                "pokerlab.trainer.weak-hand-flop-interval-report-path",
                () -> fixture("supplied-weak-hand-qualified-flop-report.json.gz").toString());
        registry.add("pokerlab.trainer.weak-hand-flop-interval-report-hash", () -> PIN);
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
                .andExpect(jsonPath("$.hero").value("BTN"))
                .andExpect(jsonPath("$.heroCards").value("2c 3c"))
                .andExpect(jsonPath("$.context.board[0]").value("8d"))
                .andExpect(jsonPath("$.context.board[1]").value("Kc"))
                .andExpect(jsonPath("$.context.board[2]").value("Kd"))
                .andExpect(jsonPath("$.context.players.length()").value(6))
                .andExpect(jsonPath("$.context.potBb").value(9.75))
                .andExpect(jsonPath("$.context.betSizeBb").value(3.25))
                .andExpect(jsonPath("$.context.toCallBb").value(3.25))
                .andExpect(jsonPath("$.bounds").doesNotExist())
                .andExpect(jsonPath("$.prior").doesNotExist())
                .andExpect(jsonPath("$.moves").doesNotExist());
    }

    @Test
    void bothLegalActionsReturnCanonicalOwnedRangesWithNoClientPayoffInput() throws Exception {
        for (String action : List.of("fold", "call")) {
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
                            .readValue(response, SixMaxRefinedFlopTrainer.Feedback.class));
        }
    }

    @Test
    void stalePinsIllegalMovesAndSubmittedContextOrEvAreRejected() throws Exception {
        String valid = "{\"reportHash\":\"" + PIN + "\",\"action\":\"fold\"}";
        for (String body :
                List.of(
                        valid.replace(PIN, "0".repeat(64)),
                        valid.replace("}", ",\"action\":\"call\"}"),
                        valid.replace("}", ",\"reportHash\":\"" + PIN + "\"}"),
                        valid + " {}",
                        valid + " ".repeat(32 * 1024),
                        valid.replace("\"fold\"", "123"),
                        valid.replace("\"fold\"", "true"),
                        valid.replace("fold", "check"),
                        valid.replace("fold", "raise:100.0"),
                        valid.replace("fold", ""),
                        valid.replace("fold", "x".repeat(33)),
                        valid.replace("\"fold\"", "null"),
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
                                                        + "\",\"actions\":[\"fold\",\"call\"]}"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andExpect(
                                jsonPath("$.schemaVersion").value(SixMaxRefinedFlopTrainer.SCHEMA))
                        .andExpect(jsonPath("$.counts.withinLimit").value(1))
                        .andExpect(jsonPath("$.counts.outsideLimit").value(1))
                        .andExpect(jsonPath("$.counts.strategyDependent").value(0))
                        .andExpect(jsonPath("$.accuracy").doesNotExist())
                        .andExpect(jsonPath("$.totalEvLossBb").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(
                SixMaxRefinedFlopTrainer.read(
                        fixture("supplied-weak-hand-qualified-flop-drill.json")),
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response, SixMaxRefinedFlopTrainer.Review.class));
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
                        prefix + "[123]}",
                        prefix + "[true]}",
                        prefix + "[]} {}",
                        prefix + "[],\"actions\":[\"call\"]}",
                        prefix + "[]}" + " ".repeat(32 * 1024),
                        prefix + "null}",
                        prefix + "[null]}",
                        prefix + "[\"check\"]}",
                        prefix + "[\"fold\"],\"counts\":{\"withinLimit\":99}}",
                        prefix.replace(PIN, "0".repeat(64)) + "[]}",
                        prefix
                                + "["
                                + String.join(",", java.util.Collections.nCopies(101, "\"fold\""))
                                + "]}"))
            mvc.perform(post(BASE + "/review").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
    }
}
