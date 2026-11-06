package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxBatchPreflopDrill;
import com.pokerlab.solver.SixMaxBatchPreflopDrillLoader;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = BatchPreflopDrillController.class,
        properties = "pokerlab.trainer.root-batch-preflop-enabled=true")
@Import(BatchPreflopDrillConfiguration.class)
class BatchPreflopDrillControllerTest {
    static final String PIN = SixMaxBatchPreflopDrillLoader.STUDY_HASH;
    static final String BASE = "/api/v1/trainer/research/root-batch-preflop-drill";
    static final List<String> SOURCES =
            List.of(
                    "supplied-batch-preflop-drill-manifest.json",
                    "supplied-root-sequence-batch-feedback-request.json",
                    "supplied-root-sequence-batch-feedback-report.json.gz",
                    "supplied-root-envelope-feedback-report.json.gz",
                    "supplied-root-sequence-minimum-report.json.gz",
                    "supplied-root-sequence-batch-report.json.gz",
                    "supplied-root-sequence-feedback-report.json.gz");
    static Path sources;
    @Autowired MockMvc mvc;
    @Autowired SixMaxBatchPreflopDrill.Result drill;
    final ObjectMapper mapper = new ObjectMapper();

    static synchronized Path sources() {
        try {
            if (sources == null) {
                sources = Files.createTempDirectory("pokerlab-root-http-");
                for (String name : SOURCES)
                    Files.copy(IntervalResearchControllerTest.fixture(name), sources.resolve(name));
            }
            return sources;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        // Exactly one physical replay/two fresh reference runs in this cached Spring context.
        registry.add(
                "pokerlab.trainer.root-batch-preflop-manifest-path",
                () -> sources().resolve(SOURCES.getFirst()).toString());
        registry.add("pokerlab.trainer.root-batch-preflop-study-hash", () -> PIN);
    }

    @AfterAll
    static void removeOnlyTemporaryCopies() throws Exception {
        if (sources != null) {
            for (String name : SOURCES) Files.delete(sources.resolve(name));
            Files.delete(sources);
        }
    }

    Map<String, Object> grade(int index, String action) {
        return Map.of("studyHash", PIN, "sourceCaseIndex", index, "action", action);
    }

    Map<String, Object> choice(int index, String action) {
        return Map.of("sourceCaseIndex", index, "action", action);
    }

    String postJson(String route, Object value) throws Exception {
        return mvc.perform(
                        post(BASE + route)
                                .contentType("application/json")
                                .content(mapper.writeValueAsBytes(value)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    void invalid(String route, String json) throws Exception {
        mvc.perform(post(BASE + route).contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realStartupServesPreflopContextAndEveryCachedGrade() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.studyHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(1))
                .andExpect(jsonPath("$.cachedLegalGrades").value(3))
                .andExpect(jsonPath("$.tableSeats").value(6))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(jsonPath("$.startupBudget.maximumMinimumLpSolves").value(3))
                .andExpect(jsonPath("$.startupBudget.freshIterations").value(240000));
        for (var q : drill.snapshot().questions()) {
            String text =
                    mvc.perform(
                                    get(BASE + "/questions/" + q.sourceCaseIndex())
                                            .param("studyHash", PIN))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.context.potBb").value(4.5))
                            .andExpect(jsonPath("$.context.toCallBb").value(2.0))
                            .andExpect(jsonPath("$.context.actingSeat").value("BB"))
                            .andExpect(jsonPath("$.context.players.length()").value(6))
                            .andExpect(jsonPath("$.board.length()").value(0))
                            .andExpect(
                                    jsonPath("$.evScope").value(SixMaxBatchPreflopDrill.EV_SCOPE))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(q, mapper.readValue(text, SixMaxBatchPreflopDrill.Question.class));
            for (String hidden :
                    List.of(
                            "worlds",
                            "dealtCombos",
                            "opponentCards",
                            "candidate",
                            "references",
                            "lowerWitness",
                            "strategy",
                            "informationSet",
                            "6d",
                            "Qc",
                            "Td")) assertFalse(text.contains("\"" + hidden + "\""), hidden);
            for (var move : q.moves())
                assertEquals(
                        drill.grade(PIN, q.sourceCaseIndex(), move.action()),
                        mapper.readValue(
                                postJson("/grade", grade(q.sourceCaseIndex(), move.action())),
                                SixMaxBatchPreflopDrill.Grade.class));
        }
    }

    @Test
    void orderedReviewsAndCanonicalUtf8ExportsAreDerivedFromCachedChoices() throws Exception {
        var choices = new ArrayList<Map<String, Object>>();
        var attempts = new ArrayList<SixMaxBatchPreflopDrill.Attempt>();
        for (int i : List.of(0))
            for (String action : List.of("fold", "call", "raise:9.0")) {
                choices.add(choice(i, action));
                attempts.add(new SixMaxBatchPreflopDrill.Attempt(i, action));
            }
        var body = Map.of("studyHash", PIN, "choices", choices);
        var expected = drill.review(PIN, attempts);
        assertEquals(new SixMaxBatchPreflopDrill.Counts(1, 1, 1, 1), expected.counts());
        assertEquals(
                expected,
                mapper.readValue(postJson("/review", body), SixMaxBatchPreflopDrill.Review.class));
        var exported =
                mvc.perform(
                                post(BASE + "/review/export")
                                        .contentType("application/json")
                                        .content(mapper.writeValueAsBytes(body)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(content().contentType("application/json;charset=UTF-8"))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                "attachment; filename=\"pokerlab-root-batch-preflop-review-"
                                                        + PIN.substring(0, 12)
                                                        + ".json\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(expected, mapper.readValue(exported, SixMaxBatchPreflopDrill.Review.class));
        assertTrue(exported.endsWith("\n"));
        assertFalse(exported.contains("\r"));
        assertEquals(exported, postJson("/review/export", body));
    }

    @Test
    void strictBodiesRejectUnknownMissingNullCoercedDuplicateAndNonFiniteValues() throws Exception {
        var valid = grade(0, "fold");
        for (String key : valid.keySet()) {
            var changed = new HashMap<>(valid);
            changed.remove(key);
            invalid("/grade", mapper.writeValueAsString(changed));
            changed.put(key, null);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        for (Object value : List.of(-1, 1, .5, "0", true)) {
            var changed = new HashMap<>(valid);
            changed.put("sourceCaseIndex", value);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        for (String key :
                List.of(
                        "evBb",
                        "classification",
                        "feedback",
                        "question",
                        "request",
                        "trainerAdmission")) {
            var changed = new HashMap<>(valid);
            changed.put(key, 123);
            invalid("/grade", mapper.writeValueAsString(changed));
        }
        for (String action : List.of("check", "bet:0.25", "raise", "CALL", ""))
            invalid("/grade", mapper.writeValueAsString(grade(0, action)));
        String json = mapper.writeValueAsString(valid);
        for (String changed :
                List.of(
                        json + " {}",
                        "null",
                        "[]",
                        json.replaceFirst("\\{", "{\"studyHash\":\"0\","),
                        json.replace("\"sourceCaseIndex\":0", "\"sourceCaseIndex\":NaN")))
            invalid("/grade", changed);
        invalid("/review", "{\"studyHash\":\"" + PIN + "\",\"choices\":[null]}");
        invalid(
                "/review",
                "{\"studyHash\":\""
                        + PIN
                        + "\",\"choices\":[{\"sourceCaseIndex\":\"0\",\"action\":\"fold\"}]}");
        invalid("/review/export", "{\"studyHash\":\"" + PIN + "\",\"choices\":[],\"counts\":{}}");
        invalid("/grade", " ".repeat(BatchPreflopDrillController.MAX_REQUEST_BYTES + 1));
    }

    @Test
    void pinsAndSourceIndicesCannotSilentlySelectAnotherQuestionSet() throws Exception {
        for (String hash :
                List.of(
                        "0".repeat(64),
                        com.pokerlab.solver.SixMaxMinimumPreflopDrillLoader.STUDY_HASH,
                        com.pokerlab.solver.SixMaxRootPreflopDrillLoader.STUDY_HASH,
                        "eeeadc2a2addf1e6878af533fe51251a42f3f2afa2f1a1fb7f0dd6e6bb01b9a5",
                        PIN.toUpperCase(Locale.ROOT))) {
            mvc.perform(get(BASE + "/questions/0").param("studyHash", hash))
                    .andExpect(status().isBadRequest());
            var changed = new HashMap<>(grade(0, "fold"));
            changed.put("studyHash", hash);
            invalid("/grade", mapper.writeValueAsString(changed));
            invalid(
                    "/review",
                    mapper.writeValueAsString(
                            Map.of("studyHash", hash, "choices", List.of(choice(0, "fold")))));
            invalid(
                    "/review/export",
                    mapper.writeValueAsString(Map.of("studyHash", hash, "choices", List.of())));
        }
        for (String index : List.of("-1", "1", "0.5", "unknown"))
            mvc.perform(get(BASE + "/questions/" + index).param("studyHash", PIN))
                    .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/questions/0")).andExpect(status().isBadRequest());
    }

    @Test
    void emptyRepeatedAndMaximumReviewsPreserveHonestCountsAndRejectOverflow() throws Exception {
        for (int count : List.of(0, 1, 128)) {
            var choices = Collections.nCopies(count, choice(0, "fold"));
            var result =
                    mapper.readValue(
                            postJson("/review", Map.of("studyHash", PIN, "choices", choices)),
                            SixMaxBatchPreflopDrill.Review.class);
            assertEquals(count, result.attempts().size());
            assertEquals(
                    new SixMaxBatchPreflopDrill.Counts(count == 0 ? 0 : 1, 0, count, 0),
                    result.counts());
            for (int i = 0; i < count; i++) assertEquals(i, result.attempts().get(i).ordinal());
        }
        var overflow =
                mapper.writeValueAsString(
                        Map.of(
                                "studyHash",
                                PIN,
                                "choices",
                                Collections.nCopies(129, choice(0, "fold"))));
        invalid("/review", overflow);
        invalid("/review/export", overflow);
    }

    @Test
    void everyRouteContinuesFromCacheWhenAllStartupFilesAreUnavailable() throws Exception {
        var before = drill.snapshot();
        try {
            for (String name : SOURCES)
                Files.move(sources.resolve(name), sources.resolve(name + ".held"));
            mvc.perform(get(BASE)).andExpect(status().isOk());
            for (var q : before.questions()) {
                mvc.perform(get(BASE + "/questions/" + q.sourceCaseIndex()).param("studyHash", PIN))
                        .andExpect(status().isOk());
                for (var m : q.moves()) postJson("/grade", grade(q.sourceCaseIndex(), m.action()));
            }
            var body =
                    Map.of(
                            "studyHash",
                            PIN,
                            "choices",
                            List.of(choice(0, "fold"), choice(0, "call")));
            postJson("/review", body);
            postJson("/review/export", body);
            assertSame(before, drill.snapshot());
            assertSame(drill.grade(PIN, 0, "fold"), drill.grade(PIN, 0, "fold"));
        } finally {
            for (String name : SOURCES)
                if (Files.exists(sources.resolve(name + ".held")))
                    Files.move(sources.resolve(name + ".held"), sources.resolve(name));
        }
    }
}
