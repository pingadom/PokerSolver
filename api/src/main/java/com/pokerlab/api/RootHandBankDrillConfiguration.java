package com.pokerlab.api;

import com.pokerlab.solver.SixMaxRootHandBankDrill;
import com.pokerlab.solver.SixMaxRootHandBankDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.root-hand-bank-enabled", havingValue = "true")
class RootHandBankDrillConfiguration {
    @Bean
    SixMaxRootHandBankDrill.Result rootHandBankDrill(
            @Value("${pokerlab.trainer.root-hand-bank-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-hand-bank-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "root hand bank drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(RootHandBankDrillConfiguration.class);
        log.warn(
                "Replaying root hand bank study once: one physical game, up to 8 parent/15 envelope LPs and two fresh references/240000 iterations");
        var result = SixMaxRootHandBankDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only root hand bank cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
