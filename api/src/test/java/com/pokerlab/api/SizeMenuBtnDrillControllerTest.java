package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxSizeMenuBtnDrill;
import com.pokerlab.solver.SixMaxSizeMenuBtnDrillLoader;
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
        controllers = SizeMenuBtnDrillController.class,
        properties = "pokerlab.trainer.size-menu-btn-enabled=true")
@Import(SizeMenuBtnDrillConfiguration.class)
class SizeMenuBtnDrillControllerTest {
    static final String PIN = SixMaxSizeMenuBtnDrillLoader.STUDY_HASH;
    static final String BASE = "/api/v1/trainer/research/size-menu-btn-drill";
    static final List<String> SOURCES =
            List.of(
                    "supplied-size-menu-btn-quarter-drill-manifest.json",
                    "supplied-size-menu-btn-quarter-request.json",
                    "supplied-size-menu-btn-quarter-report.json.gz");
    static Path sources;
    @Autowired MockMvc mvc;
    @Autowired SixMaxSizeMenuBtnDrill.Result drill;
    final ObjectMapper mapper = new ObjectMapper();

    static synchronized Path sources() {
        try {
            if (sources == null) {
                sources = Files.createTempDirectory("pokerlab-btn-http-");
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
                "pokerlab.trainer.size-menu-btn-manifest-path",
                () -> sources().resolve(SOURCES.getFirst()).toString());
        registry.add("pokerlab.trainer.size-menu-btn-study-hash", () -> PIN);
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
    void realStartupServesAllFourPublicTablesAndEveryCachedGrade() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.studyHash").value(PIN))
                .andExpect(jsonPath("$.availableQuestions").value(4))
                .andExpect(jsonPath("$.cachedLegalGrades").value(8))
                .andExpect(jsonPath("$.tableSeats").value(6))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false))
                .andExpect(jsonPath("$.startupBudget.maximumIntervalLpSolves").value(32))
                .andExpect(jsonPath("$.startupBudget.freshIterations").value(240000));
        for (var q : drill.snapshot().questions()) {
            String text =
                    mvc.perform(
                                    get(BASE + "/questions/" + q.sourceCaseIndex())
                                            .param("studyHash", PIN))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.context.potBb").value(8.125))
                            .andExpect(jsonPath("$.context.toCallBb").value(1.625))
                            .andExpect(jsonPath("$.context.actingSeat").value("BTN"))
                            .andExpect(jsonPath("$.context.players.length()").value(6))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(q, mapper.readValue(text, SixMaxSizeMenuBtnDrill.Question.class));
            for (String hidden :
                    List.of(
                            "worlds",
                            "dealtCombos",
                            "opponentCards",
                            "candidate",
                            "references",
                            "lowerWitness",
                            "strategy")) assertFalse(text.contains("\"" + hidden + "\""), hidden);
            for (var move : q.moves())
                assertEquals(
                        drill.grade(PIN, q.sourceCaseIndex(), move.action()),
                        mapper.readValue(
                                postJson("/grade", grade(q.sourceCaseIndex(), move.action())),
                                SixMaxSizeMenuBtnDrill.Grade.class));
        }
    }

    @Test
    void orderedReviewsAndCanonicalUtf8ExportsAreDerivedFromCachedChoices() throws Exception {
        var choices = new ArrayList<Map<String, Object>>();
        var attempts = new ArrayList<SixMaxSizeMenuBtnDrill.Attempt>();
        for (int i : List.of(3, 0, 2, 1))
            for (String action : List.of("fold", "call")) {
                choices.add(choice(i, action));
                attempts.add(new SixMaxSizeMenuBtnDrill.Attempt(i, action));
            }
        var body = Map.of("studyHash", PIN, "choices", choices);
        var expected = drill.review(PIN, attempts);
        assertEquals(new SixMaxSizeMenuBtnDrill.Counts(4, 4, 4, 0), expected.counts());
        assertEquals(
                expected,
                mapper.readValue(postJson("/review", body), SixMaxSizeMenuBtnDrill.Review.class));
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
                                                "attachment; filename=\"pokerlab-btn-sizing-review-c55d598cedc0.json\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(expected, mapper.readValue(exported, SixMaxSizeMenuBtnDrill.Review.class));
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
        for (Object value : List.of(-1, 4, .5, "0", true)) {
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
        invalid("/grade", " ".repeat(SizeMenuBtnDrillController.MAX_REQUEST_BYTES + 1));
    }

    @Test
    void pinsAndSourceIndicesCannotSilentlySelectAnotherQuestionSet() throws Exception {
        for (String hash :
                List.of(
                        "0".repeat(64),
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
        for (String index : List.of("-1", "4", "0.5", "unknown"))
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
                            SixMaxSizeMenuBtnDrill.Review.class);
            assertEquals(count, result.attempts().size());
            assertEquals(
                    new SixMaxSizeMenuBtnDrill.Counts(count == 0 ? 0 : 1, count, 0, 0),
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
                            List.of(choice(0, "fold"), choice(3, "call")));
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
