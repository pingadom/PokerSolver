package com.pokerlab.api;

import com.pokerlab.solver.MultiwayPackJson;
import com.pokerlab.solver.MultiwaySidePotPack;
import com.pokerlab.solver.MultiwaySolutionPack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.multiway-research-enabled", havingValue = "true")
class MultiwayResearchConfiguration {
    @Bean
    MultiwayResearchService multiwayResearchService(
            @Value("${pokerlab.trainer.multiway-research-pack-path:}") String packPath)
            throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException("Multiway research trainer needs a pack path");
        Path path = Path.of(packPath);
        if (Files.size(path) > 16 * 1024 * 1024)
            throw new IllegalStateException("Multiway research pack exceeds the 16 MiB file limit");
        String json = Files.readString(path);
        MultiwayResearchService service =
                switch (MultiwayPackJson.schemaVersion(json)) {
                    case MultiwaySolutionPack.SCHEMA_VERSION ->
                            new MultiwayResearchService(MultiwayPackJson.read(json));
                    case MultiwaySidePotPack.SCHEMA_VERSION ->
                            new MultiwayResearchService(MultiwayPackJson.readSidePot(json));
                    default -> throw new IllegalStateException("Unsupported multiway pack schema");
                };
        LoggerFactory.getLogger(MultiwayResearchConfiguration.class)
                .warn(
                        "Validation-only multiway research trainer enabled for {} with pack {}",
                        service.metadata().spotId(),
                        service.metadata().packHash());
        return service;
    }
}
