package com.pokerlab.api;

import com.pokerlab.solver.SixMaxMinimumPreflopDrill;
import com.pokerlab.solver.SixMaxMinimumPreflopDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.root-minimum-preflop-enabled", havingValue = "true")
class MinimumPreflopDrillConfiguration {
    @Bean
    SixMaxMinimumPreflopDrill.Result minimumPreflopDrill(
            @Value("${pokerlab.trainer.root-minimum-preflop-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-minimum-preflop-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "minimum preflop drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(MinimumPreflopDrillConfiguration.class);
        log.warn(
                "Replaying minimum preflop study once: one physical game, up to 4 parent/1 minimum LPs and two fresh references/240000 iterations");
        var result = SixMaxMinimumPreflopDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only minimum preflop cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
