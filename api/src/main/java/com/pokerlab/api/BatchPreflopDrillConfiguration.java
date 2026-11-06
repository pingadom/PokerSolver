package com.pokerlab.api;

import com.pokerlab.solver.SixMaxBatchPreflopDrill;
import com.pokerlab.solver.SixMaxBatchPreflopDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.root-batch-preflop-enabled", havingValue = "true")
class BatchPreflopDrillConfiguration {
    @Bean
    SixMaxBatchPreflopDrill.Result batchPreflopDrill(
            @Value("${pokerlab.trainer.root-batch-preflop-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-batch-preflop-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "sequence preflop drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(BatchPreflopDrillConfiguration.class);
        log.warn(
                "Replaying sequence preflop study once: one physical game, up to 4 parent/3 minimum LPs and two fresh references/240000 iterations");
        var result = SixMaxBatchPreflopDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only sequence preflop cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
