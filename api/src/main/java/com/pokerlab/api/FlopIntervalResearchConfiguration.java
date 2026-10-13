package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSuppliedFlopTrainer;
import com.pokerlab.solver.SixMaxSuppliedFlopTrainerLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Off by default. Full physical qualification occurs once while constructing this cached bean. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.flop-interval-research-enabled",
        havingValue = "true")
class FlopIntervalResearchConfiguration {
    @Bean
    SixMaxSuppliedFlopTrainer flopIntervalResearchTrainer(
            @Value("${pokerlab.trainer.flop-interval-request-path:}") String request,
            @Value("${pokerlab.trainer.flop-interval-report-path:}") String report,
            @Value("${pokerlab.trainer.flop-interval-report-hash:}") String hash)
            throws Exception {
        if (request.isBlank() || report.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Flop interval research needs request/report paths and an exact report hash pin");
        var trainer = SixMaxSuppliedFlopTrainerLoader.load(Path.of(request), Path.of(report), hash);
        LoggerFactory.getLogger(FlopIntervalResearchConfiguration.class)
                .warn(
                        "Research-only physical flop interval drill enabled: report={}, selectedQuestions=1, trainerAdmission=false",
                        trainer.question().reportHash());
        return trainer;
    }
}
