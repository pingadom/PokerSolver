package com.pokerlab.api;

import com.pokerlab.solver.SixMaxMixedFlopIntervalCollection;
import com.pokerlab.solver.SixMaxMixedFlopIntervalCollectionLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in; every source physically replays once during startup, never on a request. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.mixed-flop-interval-collection-enabled",
        havingValue = "true")
class MixedFlopIntervalCollectionConfiguration {
    @Bean
    SixMaxMixedFlopIntervalCollection mixedFlopIntervalCollection(
            @Value("${pokerlab.trainer.mixed-flop-interval-collection-manifest-path:}")
                    String manifest,
            @Value("${pokerlab.trainer.mixed-flop-interval-collection-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval collection needs a manifest path and exact collection hash pin");
        LoggerFactory.getLogger(MixedFlopIntervalCollectionConfiguration.class)
                .warn(
                        "Starting full three-source BB/BTN physical qualification; offline replay may take 15 minutes");
        var collection = SixMaxMixedFlopIntervalCollectionLoader.load(Path.of(manifest), hash);
        LoggerFactory.getLogger(MixedFlopIntervalCollectionConfiguration.class)
                .warn(
                        "Research-only interval collection enabled: collection={}, selectedQuestions={}, trainerAdmission=false",
                        collection.collectionHash(),
                        collection.questions().size());
        return collection;
    }
}
