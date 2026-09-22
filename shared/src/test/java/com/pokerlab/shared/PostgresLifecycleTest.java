package com.pokerlab.shared;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostgresLifecycleTest extends PostgresContract {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "pokerlab"));
        registry.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "pokerlab"));
    }
}
