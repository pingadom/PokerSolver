package com.pokerlab.api;

import com.pokerlab.solver.SixMaxUnpairedFlopCollection;
import com.pokerlab.solver.SixMaxUnpairedFlopCollectionLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in; every source physically replays once during startup, never on a request. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.unpaired-flop-collection-enabled",
        havingValue = "true")
class UnpairedFlopCollectionConfiguration {
    @Bean
    SixMaxUnpairedFlopCollection unpairedFlopCollection(
            @Value("${pokerlab.trainer.unpaired-flop-collection-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.unpaired-flop-collection-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval collection needs a manifest path and exact collection hash pin");
        LoggerFactory.getLogger(UnpairedFlopCollectionConfiguration.class)
                .warn(
                        "Starting seven-source BB/BTN physical qualification with explicit shared-game reference pair; offline replay may take 20 minutes");
        var collection = SixMaxUnpairedFlopCollectionLoader.load(Path.of(manifest), hash);
        LoggerFactory.getLogger(UnpairedFlopCollectionConfiguration.class)
                .warn(
                        "Research-only interval collection enabled: collection={}, selectedQuestions={}, trainerAdmission=false",
                        collection.collectionHash(),
                        collection.questions().size());
        return collection;
    }
}
