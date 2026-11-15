package com.pokerlab.api;

import com.pokerlab.solver.SixMaxRootHandBankDrill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One explicit bounded startup source; absent or invalid configuration cannot fall back. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.root-hand-bank-enabled", havingValue = "true")
class RootHandBankDrillConfiguration {
    @Bean
    RootHandBankStartupSource rootHandBankStartupSource(
            @Value("${pokerlab.trainer.root-hand-bank-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.root-hand-bank-study-hash:}") String hash,
            @Value("${pokerlab.trainer.root-hand-bank-startup-source:ORIGINAL}") String source)
            throws Exception {
        return RootHandBankStartupSource.load(manifest, hash, source);
    }

    @Bean(name = "rootHandBankDrill")
    SixMaxRootHandBankDrill.Result cachedRootHandBankDrill(RootHandBankStartupSource source) {
        return source.drill();
    }

    // Preserve the existing direct configuration validation entry point and its original default.
    SixMaxRootHandBankDrill.Result rootHandBankDrill(String manifest, String hash)
            throws Exception {
        return RootHandBankStartupSource.load(manifest, hash, "ORIGINAL").drill();
    }
}
