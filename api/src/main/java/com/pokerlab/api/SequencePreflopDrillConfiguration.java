package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSequencePreflopDrill;
import com.pokerlab.solver.SixMaxSequencePreflopDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.root-sequence-preflop-enabled",
        havingValue = "true")
class SequencePreflopDrillConfiguration {
    @Bean
    SixMaxSequencePreflopDrill.Result sequencePreflopDrill(
            @Value("${pokerlab.trainer.root-sequence-preflop-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-sequence-preflop-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "sequence preflop drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(SequencePreflopDrillConfiguration.class);
        log.warn(
                "Replaying sequence preflop study once: one physical game, up to 4 parent/1 minimum LPs and two fresh references/240000 iterations");
        var result = SixMaxSequencePreflopDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only sequence preflop cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
