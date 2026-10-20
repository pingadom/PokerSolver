package com.pokerlab.api;

import com.pokerlab.solver.SixMaxExpandedFlopIntervalCollection;
import com.pokerlab.solver.SixMaxExpandedFlopIntervalCollectionLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in; every source physically replays once during startup, never on a request. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.expanded-flop-interval-collection-enabled",
        havingValue = "true")
class ExpandedFlopIntervalCollectionConfiguration {
    @Bean
    SixMaxExpandedFlopIntervalCollection expandedFlopIntervalCollection(
            @Value("${pokerlab.trainer.expanded-flop-interval-collection-manifest-path:}")
                    String manifest,
            @Value("${pokerlab.trainer.expanded-flop-interval-collection-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval collection needs a manifest path and exact collection hash pin");
        LoggerFactory.getLogger(ExpandedFlopIntervalCollectionConfiguration.class)
                .warn(
                        "Starting full four-source BB/BTN physical qualification; offline replay may take 20 minutes");
        var collection = SixMaxExpandedFlopIntervalCollectionLoader.load(Path.of(manifest), hash);
        LoggerFactory.getLogger(ExpandedFlopIntervalCollectionConfiguration.class)
                .warn(
                        "Research-only interval collection enabled: collection={}, selectedQuestions={}, trainerAdmission=false",
                        collection.collectionHash(),
                        collection.questions().size());
        return collection;
    }
}
