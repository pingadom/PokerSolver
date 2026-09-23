package com.pokerlab.api;

import com.pokerlab.shared.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimulationCache {
    private final SimulationStore store;
    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final MeterRegistry metrics;
    private final boolean enabled;

    public SimulationCache(
            SimulationStore store,
            StringRedisTemplate redis,
            JsonCodec json,
            MeterRegistry metrics,
            @Value("${pokerlab.cache.enabled:true}") boolean enabled) {
        this.store = store;
        this.redis = redis;
        this.json = json;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    public SimulationView status(UUID id) {
        return cached(
                "pokerlab:v1:status:" + id,
                SimulationView.class,
                Duration.ofSeconds(2),
                () -> store.get(id));
    }

    public AggregatedSimulationResult result(UUID id) {
        return cached(
                "pokerlab:v1:result:" + id,
                AggregatedSimulationResult.class,
                Duration.ofHours(1),
                () -> store.result(id).orElseThrow());
    }

    private <T> T cached(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        if (enabled) {
            try {
                var value = redis.opsForValue().get(key);
                if (value != null) {
                    T decoded = json.read(value, type);
                    metrics.counter("pokerlab.cache.hit").increment();
                    return decoded;
                }
            } catch (RuntimeException ignored) {
                metrics.counter("pokerlab.cache.error").increment();
            }
        }
        metrics.counter("pokerlab.cache.miss").increment();
        T value = loader.get();
        if (enabled) {
            try {
                redis.opsForValue().set(key, json.write(value), ttl);
            } catch (RuntimeException ignored) {
                metrics.counter("pokerlab.cache.error").increment();
            }
        }
        return value;
    }
}
