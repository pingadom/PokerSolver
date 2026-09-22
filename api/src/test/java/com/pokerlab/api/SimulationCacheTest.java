package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pokerlab.shared.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class SimulationCacheTest {
    @Test
    void unavailableRedisCannotPreventAuthoritativeReads() {
        var store = mock(SimulationStore.class);
        var redis = mock(StringRedisTemplate.class);
        var id = UUID.randomUUID();
        var view = mock(SimulationView.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        when(store.get(id)).thenReturn(view);
        var metrics = new SimpleMeterRegistry();
        var cache =
                new SimulationCache(
                        store,
                        redis,
                        new JsonCodec(JsonMapper.builder().findAndAddModules().build()),
                        metrics,
                        true);
        assertSame(view, cache.status(id));
        assertEquals(2, metrics.get("pokerlab.cache.error").counter().count());
        verify(store).get(id);
    }
}
