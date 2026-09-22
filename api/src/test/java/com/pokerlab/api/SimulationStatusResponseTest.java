package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pokerlab.core.batch.SimulationConfiguration;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import com.pokerlab.shared.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class SimulationStatusResponseTest {
    @Test
    void preservesSeedsBeyondJavascriptSafeIntegerAndUsesCompactCards() throws Exception {
        var config =
                new SimulationConfiguration(
                        List.of(
                                new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                                new PlayerHand("KK", Card.parse("KS"), Card.parse("KH"))),
                        List.of(),
                        100,
                        100,
                        Long.MAX_VALUE);
        var view =
                new SimulationView(
                        UUID.randomUUID(),
                        SimulationStatus.QUEUED,
                        config,
                        0,
                        100,
                        0,
                        1,
                        Instant.now(),
                        Instant.now(),
                        null,
                        0);
        var dto = SimulationStatusResponse.from(view);
        var tree = JsonMapper.builder().findAndAddModules().build().valueToTree(dto);
        assertTrue(tree.at("/configuration/seed").isTextual());
        assertEquals("9223372036854775807", tree.at("/configuration/seed").asText());
        assertEquals("As", tree.at("/configuration/players/0/cards/0").asText());
    }
}
