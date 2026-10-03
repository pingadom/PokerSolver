package com.pokerlab.api;

import com.pokerlab.solver.MultiwayPackJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.sixmax-preflop-research-enabled",
        havingValue = "true")
class SixMaxPreflopResearchConfiguration {
    @Bean
    SixMaxPreflopResearchService sixMaxPreflopResearchService(
            @Value("${pokerlab.trainer.sixmax-preflop-research-pack-path:}") String packPath)
            throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException(
                    "Full-round preflop research trainer needs a pack path");
        var path = Path.of(packPath);
        if (Files.size(path) > 16 * 1024 * 1024)
            throw new IllegalStateException(
                    "Full-round preflop pack exceeds the 16 MiB file limit");
        var service =
                new SixMaxPreflopResearchService(
                        MultiwayPackJson.readFullRound(Files.readString(path)));
        LoggerFactory.getLogger(SixMaxPreflopResearchConfiguration.class)
                .warn(
                        "Validation-only full-round preflop trainer enabled for {} with pack {}",
                        service.metadata().spotId(),
                        service.metadata().packHash());
        return service;
    }
}
