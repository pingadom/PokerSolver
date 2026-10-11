package com.pokerlab.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokerlab.solver.SixMaxSuppliedIntervalTrainer;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = IntervalResearchController.class,
        properties = {
            "pokerlab.trainer.interval-research-enabled=true",
            "spring.jackson.deserialization.fail-on-unknown-properties=true"
        })
@Import(IntervalResearchConfiguration.class)
class IntervalResearchControllerTest {
    static final String PIN = "5fb55653ae5f53d0110cea8507be9a37a6af2fe5cc596f433aaab0838b6b0aa9";
    static final String BASE = "/api/v1/trainer/research/interval";
    @Autowired MockMvc mvc;
    @Autowired SixMaxSuppliedIntervalTrainer trainer;

    static Path fixture(String name) {
        var path = Path.of("..", "docs", "data", name);
        if (!Files.isRegularFile(path)) path = Path.of("docs", "data", name);
        return path.toAbsolutePath().normalize();
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add(
                "pokerlab.trainer.interval-request-path",
                () ->
                        fixture(
                                        "supplied-conditional-five-target-99-interval-feedback-request.json")
                                .toString());
        registry.add(
                "pokerlab.trainer.interval-report-path",
                () ->
                        fixture("supplied-conditional-five-target-99-interval-feedback-report.json")
                                .toString());
        registry.add("pokerlab.trainer.interval-report-hash", () -> PIN);
    }

    @Test
    void realPinnedStartupServesOneQuestionWithSixSeatsAndResearchProvenance() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.reportHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(1))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(
                        jsonPath("$.sourceHistoryReachStatus")
                                .value("UNAVAILABLE_NO_SOURCE_POLICY"));
        mvc.perform(get(BASE + "/question"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.hero").value("BB"))
                .andExpect(jsonPath("$.heroCards").value("9d 9h"))
                .andExpect(jsonPath("$.history.length()").value(7))
                .andExpect(jsonPath("$.table.players.length()").value(6))
                .andExpect(jsonPath("$.table.potBb").value(31.5))
                .andExpect(jsonPath("$.table.toCallBb").value(13))
                .andExpect(jsonPath("$.prior").doesNotExist())
                .andExpect(jsonPath("$.payoffs").doesNotExist())
                .andExpect(jsonPath("$.posterior").doesNotExist())
                .andExpect(jsonPath("$.moves").doesNotExist());
    }

    @Test
    void allLegalMovesUseOwnedRangesAndRetainStrategyDependentFeedback() throws Exception {
        String[] labels = {"OUTSIDE_LIMIT", "WITHIN_LIMIT", "STRATEGY_DEPENDENT"};
        for (int i = 0; i < 3; i++) {
            String action = trainer.question().legalActions().get(i);
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
                    .andExpect(jsonPath("$.selected.action").value(action))
                    .andExpect(jsonPath("$.selected.classification").value(labels[i]))
                    .andExpect(
                            jsonPath("$.selected.lowerEvBb")
                                    .value(
                                            trainer.grade(trainer.question(), action)
                                                    .selected()
                                                    .lowerEvBb()))
                    .andExpect(
                            jsonPath("$.selected.upperLossBb")
                                    .value(
                                            trainer.grade(trainer.question(), action)
                                                    .selected()
                                                    .upperLossBb()))
                    .andExpect(jsonPath("$.alternatives.length()").value(3))
                    .andExpect(jsonPath("$.maximumLossBb").value(.01))
                    .andExpect(jsonPath("$.trainerAdmission").value(false))
                    .andExpect(jsonPath("$.accuracy").doesNotExist());
        }
    }

    @Test
    void staleHashesIllegalMovesAndSubmittedEvOrQuestionFieldsReject() throws Exception {
        String valid = "{\"reportHash\":\"" + PIN + "\",\"action\":\"call\"}";
        for (String body :
                new String[] {
                    valid.replace(PIN, "0".repeat(64)),
                    valid.replace("call", "check"),
                    valid.replace("call", "raise:100.0"),
                    valid.replace("call", ""),
                    valid.replace("call", "x".repeat(33)),
                    valid.replace("\"call\"", "null"),
                    valid.replace("}", ",\"evLossBb\":0}"),
                    valid.replace("}", ",\"heroCards\":\"Ah Ad\"}"),
                    valid.replace("}", ",\"requestPath\":\"other.json\"}")
                })
            mvc.perform(post(BASE + "/grade").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reviewReconstructsGradesAndSeparateCountsForTheSameQuestion() throws Exception {
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(
                                        "{\"reportHash\":\""
                                                + PIN
                                                + "\",\"actions\":[\"fold\",\"call\",\"raise:50.0\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.question.reportHash").value(PIN))
                .andExpect(jsonPath("$.schemaVersion").value(SixMaxSuppliedIntervalTrainer.SCHEMA))
                .andExpect(jsonPath("$.attempts.length()").value(3))
                .andExpect(jsonPath("$.counts.withinLimit").value(1))
                .andExpect(jsonPath("$.counts.outsideLimit").value(1))
                .andExpect(jsonPath("$.counts.strategyDependent").value(1))
                .andExpect(jsonPath("$.totalEvLossBb").doesNotExist());
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content("{\"reportHash\":\"" + PIN + "\",\"actions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(0));
    }

    @Test
    void malformedAndOversizedReviewsCannotSupplyGradesOrCounts() throws Exception {
        String prefix = "{\"reportHash\":\"" + PIN + "\",\"actions\":";
        for (String body :
                new String[] {
                    prefix + "null}",
                    prefix + "[null]}",
                    prefix + "[\"check\"]}",
                    prefix + "[\"call\"],\"counts\":{\"withinLimit\":99}}",
                    prefix.replace(PIN, "0".repeat(64)) + "[]}",
                    prefix
                            + "["
                            + String.join(",", java.util.Collections.nCopies(101, "\"call\""))
                            + "]}"
                })
            mvc.perform(post(BASE + "/review").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
    }
}
