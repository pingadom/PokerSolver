package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = ApiApplication.ResearchPreview.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=research-preview",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable"
        })
class ResearchPreviewTest {
    @DynamicPropertySource
    static void pack(DynamicPropertyRegistry properties) {
        properties.add(
                "pokerlab.trainer.sixmax-preflop-research-pack-path",
                () -> SixMaxPreflopResearchControllerTest.fixture().toAbsolutePath().toString());
    }

    @LocalServerPort int port;
    @Autowired ApplicationContext context;

    @Test
    void servesActualSavedPolicyOverHttpWithoutADatabaseOrSimulationController() throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(SimulationController.class).isEmpty());
        var http = HttpClient.newHttpClient();
        var json = new ObjectMapper();
        String base = "http://127.0.0.1:" + port + "/api/v1/trainer/research/sixmax-preflop";
        var response =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                base + "/sessions/9223372036854775807/questions/0"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        var question = json.readTree(response.body());
        assertEquals("9223372036854775807", question.get("sessionSeed").asText());
        assertEquals(6, question.get("players").size());
        String grade =
                json.writeValueAsString(
                        Map.of(
                                "sessionSeed",
                                question.get("sessionSeed").asText(),
                                "index",
                                0,
                                "packHash",
                                question.get("packHash").asText(),
                                "action",
                                question.get("legalActions").get(0).asText()));
        var graded =
                http.send(
                        HttpRequest.newBuilder(URI.create(base + "/grade"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(grade))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, graded.statusCode());
        assertTrue(json.readTree(graded.body()).get("feedback").get("evLossBb").asDouble() >= 0);
        var simulations =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:" + port + "/api/v1/simulations"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(404, simulations.statusCode());
    }

    @Test
    void normalStartupRemainsSeparateFromTheExplicitPreviewFlag() {
        assertEquals(
                java.util.Set.of(ApiApplication.class),
                ApiApplication.applicationFor(new String[0]).getAllSources());
        var preview = ApiApplication.applicationFor(new String[] {"--research-preview"});
        assertEquals(
                java.util.Set.of(ApiApplication.ResearchPreview.class), preview.getAllSources());
        assertTrue(preview.getAdditionalProfiles().contains("research-preview"));
    }
}
