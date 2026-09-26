package com.pokerlab.api;

import com.pokerlab.solver.TurnRiverHandSession;
import com.pokerlab.solver.TurnRiverPackJson;
import com.pokerlab.solver.TurnRiverResearchTrainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads one saved turn-river pack at startup; HTTP requests never solve a game. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.turn-river-research-enabled", havingValue = "true")
class TurnRiverResearchConfiguration {
    @Bean
    TurnRiverResearchTrainer turnRiverResearchTrainer(
            @Value("${pokerlab.trainer.turn-river-research-pack-path:}") String packPath)
            throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException("Turn-river research trainer needs a pack path");
        Path path = Path.of(packPath);
        if (Files.size(path) > 16 * 1024 * 1024)
            throw new IllegalStateException(
                    "Turn-river research pack exceeds the 16 MiB file limit");
        TurnRiverResearchTrainer trainer =
                new TurnRiverResearchTrainer(TurnRiverPackJson.read(Files.readString(path)));
        if (trainer.pack().gameGapBb() > 0.05)
            throw new IllegalStateException("Turn-river research pack exceeds the 0.05bb gap gate");
        LoggerFactory.getLogger(TurnRiverResearchConfiguration.class)
                .warn(
                        "Validation-only turn-river research trainer enabled with pack {}",
                        trainer.packHash());
        return trainer;
    }

    @Bean
    TurnRiverHandSession turnRiverHandSession(TurnRiverResearchTrainer trainer) {
        return new TurnRiverHandSession(trainer.pack());
    }
}
