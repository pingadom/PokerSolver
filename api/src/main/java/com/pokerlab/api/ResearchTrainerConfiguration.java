package com.pokerlab.api;

import com.pokerlab.solver.PreflopPackJson;
import com.pokerlab.solver.PreflopPackScreening;
import com.pokerlab.solver.PreflopSolutionPack;
import com.pokerlab.solver.PreflopTrainer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in, filesystem-backed research drill; no pack is bundled into the API artifact. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.research-enabled", havingValue = "true")
class ResearchTrainerConfiguration {
    @Bean
    PreflopTrainer researchTrainer(
            @Value("${pokerlab.trainer.research-pack-path:}") String packPath) throws IOException {
        if (packPath.isBlank())
            throw new IllegalStateException("Research trainer needs a pack path");
        PreflopSolutionPack pack =
                PreflopPackJson.read(Files.readString(Path.of(packPath), StandardCharsets.UTF_8));
        if (!PreflopSolutionPack.EXACT_ENUMERATION.equals(pack.payoffMethod()))
            throw new IllegalStateException("Research trainer requires exact payoffs");
        PreflopPackScreening.Report screening = PreflopPackScreening.assess(pack);
        if (!screening.passesAutomatedChecks())
            throw new IllegalStateException(
                    "Research pack fails numeric screening: " + screening.findings());
        LoggerFactory.getLogger(ResearchTrainerConfiguration.class)
                .warn(
                        "Validation-only research trainer enabled for spot {} at hash {}",
                        pack.spot().id(),
                        pack.spotHash());
        return new PreflopTrainer(pack);
    }
}
