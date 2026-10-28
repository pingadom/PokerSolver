package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSizeMenuBtnDrill;
import com.pokerlab.solver.SixMaxSizeMenuBtnDrillLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One bounded startup replay, explicitly enabled by an operator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.size-menu-btn-enabled", havingValue = "true")
class SizeMenuBtnDrillConfiguration {
    @Bean
    SixMaxSizeMenuBtnDrill.Result sizeMenuBtnDrill(
            @Value("${pokerlab.trainer.size-menu-btn-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.size-menu-btn-study-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException("BTN drill needs a manifest path and exact study hash");
        var log = LoggerFactory.getLogger(SizeMenuBtnDrillConfiguration.class);
        log.warn(
                "Replaying BTN sizing study once: one physical game, up to 18 parent/32 interval LPs and two fresh references/240000 iterations");
        var result = SixMaxSizeMenuBtnDrillLoader.load(Path.of(manifest), hash);
        log.warn(
                "Research-only BTN cache enabled: study={}, questions={}, trainerAdmission=false",
                result.snapshot().studyHash(),
                result.snapshot().questions().size());
        return result;
    }
}
