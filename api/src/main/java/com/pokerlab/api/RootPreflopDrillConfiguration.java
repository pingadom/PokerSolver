package com.pokerlab.api;

import com.pokerlab.solver.SixMaxRootPreflopDrill;
import com.pokerlab.solver.SixMaxRootPreflopDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.root-preflop-enabled", havingValue = "true")
class RootPreflopDrillConfiguration {
    @Bean
    SixMaxRootPreflopDrill.Result rootPreflopDrill(
            @Value("${pokerlab.trainer.root-preflop-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-preflop-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "root preflop drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(RootPreflopDrillConfiguration.class);
        log.warn(
                "Replaying root preflop study once: one physical game, up to 4 parent/5 envelope LPs and two fresh references/240000 iterations");
        var result = SixMaxRootPreflopDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only root preflop cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
