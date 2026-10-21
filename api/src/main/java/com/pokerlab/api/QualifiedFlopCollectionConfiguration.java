package com.pokerlab.api;

import com.pokerlab.solver.SixMaxQualifiedFlopCollection;
import com.pokerlab.solver.SixMaxQualifiedFlopCollectionLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in; every source physically replays once during startup, never on a request. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.qualified-flop-collection-enabled",
        havingValue = "true")
class QualifiedFlopCollectionConfiguration {
    @Bean
    SixMaxQualifiedFlopCollection qualifiedFlopCollection(
            @Value("${pokerlab.trainer.qualified-flop-collection-manifest-path:}") String manifest,
            @Value("${pokerlab.trainer.qualified-flop-collection-hash:}") String hash)
            throws Exception {
        if (manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval collection needs a manifest path and exact collection hash pin");
        LoggerFactory.getLogger(QualifiedFlopCollectionConfiguration.class)
                .warn(
                        "Starting full five-source independent-range BB/BTN physical qualification; offline replay may take 20 minutes");
        var collection = SixMaxQualifiedFlopCollectionLoader.load(Path.of(manifest), hash);
        LoggerFactory.getLogger(QualifiedFlopCollectionConfiguration.class)
                .warn(
                        "Research-only interval collection enabled: collection={}, selectedQuestions={}, trainerAdmission=false",
                        collection.collectionHash(),
                        collection.questions().size());
        return collection;
    }
}
