package com.pokerlab.api;

import com.pokerlab.solver.RiverPackJson;
import com.pokerlab.solver.RiverResearchTrainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads one saved validation pack at startup; requests never run the river solver. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.river-research-enabled", havingValue = "true")
class RiverResearchConfiguration {
    @Bean
    RiverResearchTrainer riverResearchTrainer(
            @Value("${pokerlab.trainer.river-research-pack-path:}") String packPath)
            throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException("River research trainer needs a pack path");
        Path path = Path.of(packPath);
        if (Files.size(path) > 16 * 1024 * 1024)
            throw new IllegalStateException("River research pack exceeds the 16 MiB file limit");
        RiverResearchTrainer trainer =
                new RiverResearchTrainer(RiverPackJson.read(Files.readString(path)));
        if (trainer.pack().gameGapBb() > 0.05)
            throw new IllegalStateException("River research pack exceeds the 0.05bb gap gate");
        LoggerFactory.getLogger(RiverResearchConfiguration.class)
                .warn(
                        "Validation-only river research trainer enabled for {} with pack {}",
                        trainer.pack().spot().id(),
                        trainer.packHash());
        return trainer;
    }
}
