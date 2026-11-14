package com.pokerlab.api;

import com.pokerlab.solver.SixMaxRootHandBankRaiseDrill;
import com.pokerlab.solver.SixMaxRootHandBankRaiseDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.bank-raise-feedback-enabled", havingValue = "true")
class RootHandBankRaiseDrillConfiguration {
    @Bean
    SixMaxRootHandBankRaiseDrill.Result bankRaiseFeedbackDrill(
            @Value("${pokerlab.trainer.bank-raise-feedback-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.bank-raise-feedback-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "root hand bank drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(RootHandBankRaiseDrillConfiguration.class);
        log.warn(
                "Replaying root hand bank study once: one physical game, 4 parent/2 endpoint LPs and two fresh references/240000 iterations");
        var result = SixMaxRootHandBankRaiseDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only root hand bank cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
