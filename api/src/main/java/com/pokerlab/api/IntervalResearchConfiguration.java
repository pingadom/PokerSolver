package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSuppliedIntervalTrainer;
import com.pokerlab.solver.SixMaxSuppliedIntervalTrainerLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Off by default. Full physical qualification occurs once while constructing this cached bean. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pokerlab.trainer.interval-research-enabled", havingValue = "true")
class IntervalResearchConfiguration {
    @Bean
    SixMaxSuppliedIntervalTrainer intervalResearchTrainer(
            @Value("${pokerlab.trainer.interval-request-path:}") String request,
            @Value("${pokerlab.trainer.interval-report-path:}") String report,
            @Value("${pokerlab.trainer.interval-report-hash:}") String hash)
            throws Exception {
        if (request.isBlank() || report.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "Interval research needs request/report paths and an exact report hash pin");
        var trainer =
                SixMaxSuppliedIntervalTrainerLoader.load(Path.of(request), Path.of(report), hash);
        LoggerFactory.getLogger(IntervalResearchConfiguration.class)
                .warn(
                        "Research-only interval drill enabled: report={}, selectedQuestions=1, trainerAdmission=false",
                        trainer.question().reportHash());
        return trainer;
    }
}
