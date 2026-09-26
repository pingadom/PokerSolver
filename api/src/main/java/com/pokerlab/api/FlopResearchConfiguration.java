package com.pokerlab.api;

import com.pokerlab.solver.FlopTurnRiverHandSession;
import com.pokerlab.solver.FlopTurnRiverPackJson;
import com.pokerlab.solver.FlopTurnRiverSolutionPack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads one validated exact-deck flop pack at startup; requests never solve. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.flop-research-enabled", havingValue = "true")
class FlopResearchConfiguration {
    @Bean
    FlopTurnRiverHandSession flopTurnRiverHandSession(
            @Value("${pokerlab.trainer.flop-research-pack-path:}") String packPath)
            throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException("Flop research trainer needs a pack path");
        Path path = Path.of(packPath);
        if (Files.size(path) > 8 * 1024 * 1024)
            throw new IllegalStateException("Flop research pack exceeds the 8 MiB file limit");
        FlopTurnRiverSolutionPack pack = FlopTurnRiverPackJson.gunzip(Files.readAllBytes(path));
        if (pack.gameGapBb() > 0.01)
            throw new IllegalStateException("Flop research pack exceeds the 0.01bb gap gate");
        FlopTurnRiverHandSession session = new FlopTurnRiverHandSession(pack);
        LoggerFactory.getLogger(FlopResearchConfiguration.class)
                .warn(
                        "Validation-only flop research trainer enabled with pack {}",
                        session.packHash());
        return session;
    }
}
