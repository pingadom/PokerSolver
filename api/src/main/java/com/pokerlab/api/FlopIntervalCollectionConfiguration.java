package com.pokerlab.api;

import com.pokerlab.solver.SixMaxFlopIntervalCollection;
import com.pokerlab.solver.SixMaxFlopIntervalCollectionLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in; every source physically replays once during startup, never on a request. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.flop-interval-collection-enabled",
        havingValue = "true")
class FlopIntervalCollectionConfiguration {
    @Bean
    SixMaxFlopIntervalCollection flopIntervalCollection(
            @Value("${pokerlab.trainer.flop-interval-collection-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.flop-interval-collection-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval collection needs a manifest path and exact collection hash pin");
        var collection = SixMaxFlopIntervalCollectionLoader.load(Path.of(manifest), hash);
        LoggerFactory.getLogger(FlopIntervalCollectionConfiguration.class)
                .warn(
                        "Research-only interval collection enabled: collection={}, selectedQuestions={}, trainerAdmission=false",
                        collection.collectionHash(),
                        collection.questions().size());
        return collection;
    }
}
