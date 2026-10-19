package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.solver.SixMaxMixedFlopIntervalCollection;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP transport controls. Full owned qualification is covered by solver and production smoke. */
@WebMvcTest(
        controllers = MixedFlopIntervalCollectionController.class,
        properties = "pokerlab.trainer.mixed-flop-interval-collection-enabled=true")
class MixedFlopIntervalCollectionControllerTest {
    static final String PIN = "1694d51922de3619a3523b9ed07dbe6375a742c882632e18eafc3f7fe7c6cb2b";
    static final String BASE = "/api/v1/trainer/research/flop-interval-collection-v2";
    @Autowired MockMvc mvc;
    @MockitoBean SixMaxMixedFlopIntervalCollection collection;
    final ObjectMapper mapper = new ObjectMapper();
    SixMaxMixedFlopIntervalCollection.Review fixture;
    List<SixMaxMixedFlopIntervalCollection.Choice> choices;
    byte[] bytes;

    @BeforeEach
    void cachedTransportFixture() throws Exception {
        var path =
                IntervalResearchControllerTest.fixture(
                        "supplied-six-question-flop-collection-review.json");
        fixture = SixMaxMixedFlopIntervalCollection.read(path);
        bytes = Files.readAllBytes(path);
        choices =
                fixture.attempts().stream()
                        .map(
                                a ->
                                        new SixMaxMixedFlopIntervalCollection.Choice(
                                                a.questionIndex(),
                                                a.feedback().decision().reportHash(),
                                                a.action()))
                        .toList();
        when(collection.collectionHash()).thenReturn(PIN);
        when(collection.questions()).thenReturn(fixture.questions());
        when(collection.question(anyInt()))
                .thenAnswer(
                        call -> {
                            int i = call.getArgument(0);
                            if (i < 0 || i >= 6)
                                throw new IllegalArgumentException("Invalid question");
                            return fixture.questions().get(i);
                        });
        when(collection.grade(any(), any()))
                .thenAnswer(
                        call -> {
                            SixMaxMixedFlopIntervalCollection.Question q = call.getArgument(0);
                            String action = call.getArgument(1);
                            return fixture.attempts().stream()
                                    .filter(
                                            a ->
                                                    a.questionIndex() == q.index()
                                                            && a.action().equals(action))
                                    .findFirst()
                                    .orElseThrow(
                                            () -> new IllegalArgumentException("Illegal action"))
                                    .feedback();
                        });
        when(collection.review(anyList()))
                .thenThrow(new IllegalArgumentException("Invalid cached choice"));
        doReturn(fixture).when(collection).review(choices);
        when(collection.reviewJson(anyList()))
                .thenThrow(new IllegalArgumentException("Invalid cached choice"));
        doReturn(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                .when(collection)
                .reviewJson(choices);
    }

    Map<String, Object> gradeBody(int i, String action) {
        return Map.of(
                "collectionHash",
                PIN,
                "questionIndex",
                i,
                "reportHash",
                fixture.questions().get(i).decision().reportHash(),
                "action",
                action);
    }

    Map<String, Object> reviewBody() {
        return Map.of("collectionHash", PIN, "choices", choices);
    }

    @Test
    void metadataQuestionsAndEveryTypedGradeAreBoundToTheCache() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.availableQuestions").value(6))
                .andExpect(jsonPath("$.activePlayers").value(2))
                .andExpect(jsonPath("$.trainerAdmission").value(false));
        for (int i = 0; i < 6; i++) {
            var body =
                    mvc.perform(get(BASE + "/questions/" + i).param("collectionHash", PIN))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(mapper.valueToTree(fixture.questions().get(i)), mapper.readTree(body));
        }
        for (var a : fixture.attempts()) {
            var body =
                    mvc.perform(
                                    post(BASE + "/grade")
                                            .contentType("application/json")
                                            .content(
                                                    mapper.writeValueAsBytes(
                                                            gradeBody(
                                                                    a.questionIndex(),
                                                                    a.action()))))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertEquals(mapper.valueToTree(a.feedback()), mapper.readTree(body));
        }
    }

    @Test
    void exactMixedReviewAndCanonicalExportDelegateOnlyExplicitChoices() throws Exception {
        mvc.perform(
                        post(BASE + "/review")
                                .contentType("application/json")
                                .content(mapper.writeValueAsBytes(reviewBody())))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(fixture)));
        var result =
                mvc.perform(
                                post(BASE + "/review/export")
                                        .contentType("application/json")
                                        .content(mapper.writeValueAsBytes(reviewBody())))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                "attachment; filename=\"pokerlab-mixed-flop-review-1694d51922de.json\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertArrayEquals(bytes, result);
        verify(collection).review(choices);
        verify(collection).reviewJson(choices);
    }

    @Test
    void strictBodiesRejectUnknownDuplicateTrailingMissingCoercedAndOversizeInput()
            throws Exception {
        var valid = mapper.writeValueAsString(gradeBody(2, "bet"));
        var bodies =
                new ArrayList<>(
                        List.of(
                                "null",
                                "{}",
                                "[]",
                                valid + " {}",
                                valid.substring(0, valid.length() - 1) + ",\"questionIndex\":3}",
                                valid.substring(0, valid.length() - 1) + ",\"evLossBb\":0}",
                                " ".repeat(32769) + valid));
        for (var field : List.of("collectionHash", "questionIndex", "reportHash", "action")) {
            var tree = mapper.valueToTree(gradeBody(2, "bet"));
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove(field);
            bodies.add(tree.toString());
        }
        for (var value : List.of("\"2\"", "2.5", "false", "null", "6", "-1"))
            bodies.add(valid.replace("\"questionIndex\":2", "\"questionIndex\":" + value));
        for (var body : bodies)
            mvc.perform(post(BASE + "/grade").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        for (var route : List.of("/review", "/review/export"))
            for (var bad :
                    List.of(
                            "null",
                            "{}",
                            mapper.writeValueAsString(reviewBody()) + " {}",
                            "{\"collectionHash\":\""
                                    + PIN
                                    + "\",\"choices\":[{\"questionIndex\":2,\"reportHash\":\""
                                    + fixture.questions().get(2).decision().reportHash()
                                    + "\",\"action\":\"bet\",\"sourceQuestionIndex\":0}]}"))
                mvc.perform(post(BASE + route).contentType("application/json").content(bad))
                        .andExpect(status().isBadRequest());
    }

    @Test
    void stalePinsWrongMenusAndGlobalCapAreRejected() throws Exception {
        mvc.perform(get(BASE + "/questions/0").param("collectionHash", "0".repeat(64)))
                .andExpect(status().isBadRequest());
        var body = new HashMap<>(gradeBody(2, "bet"));
        body.put("reportHash", "0".repeat(64));
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE + "/grade")
                                .contentType("application/json")
                                .content(mapper.writeValueAsBytes(gradeBody(1, "check"))))
                .andExpect(status().isBadRequest());
        for (var route : List.of("/review", "/review/export"))
            mvc.perform(
                            post(BASE + route)
                                    .contentType("application/json")
                                    .content(
                                            mapper.writeValueAsBytes(
                                                    Map.of(
                                                            "collectionHash",
                                                            PIN,
                                                            "choices",
                                                            Collections.nCopies(
                                                                    101, choices.getFirst())))))
                    .andExpect(status().isBadRequest());
    }
}
