package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pokerlab.core.batch.SimulationConfiguration;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import com.pokerlab.shared.*;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class RedisContainerTest {
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void cachesSnapshotsAndSurvivesFlushAndOutage() {
        var configuration =
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        var client =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(200))
                        .clientOptions(
                                ClientOptions.builder()
                                        .socketOptions(
                                                SocketOptions.builder()
                                                        .connectTimeout(Duration.ofMillis(200))
                                                        .build())
                                        .build())
                        .build();
        var factory = new LettuceConnectionFactory(configuration, client);
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            var store = mock(SimulationStore.class);
            var id = UUID.randomUUID();
            var scenario =
                    new SimulationConfiguration(
                            List.of(
                                    new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                                    new PlayerHand("KK", Card.parse("KS"), Card.parse("KH"))),
                            List.of(),
                            100,
                            100,
                            42);
            var view =
                    new SimulationView(
                            id,
                            SimulationStatus.QUEUED,
                            scenario,
                            0,
                            100,
                            0,
                            1,
                            Instant.now(),
                            Instant.now(),
                            null,
                            0);
            when(store.get(id)).thenReturn(view);
            var cache =
                    new SimulationCache(
                            store,
                            redis,
                            new JsonCodec(JsonMapper.builder().findAndAddModules().build()),
                            new SimpleMeterRegistry(),
                            true);
            assertEquals(view, cache.status(id));
            assertEquals(view, cache.status(id));
            verify(store, times(1)).get(id);
            redis.delete("pokerlab:v1:status:" + id);
            assertEquals(view, cache.status(id));
            verify(store, times(2)).get(id);
            REDIS.stop();
            assertEquals(view, cache.status(id));
            verify(store, times(3)).get(id);
        } finally {
            factory.destroy();
        }
    }
}
