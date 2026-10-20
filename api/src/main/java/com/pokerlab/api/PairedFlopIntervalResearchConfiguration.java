package com.pokerlab.api;

import com.pokerlab.solver.SixMaxPairedFlopTrainer;
import com.pokerlab.solver.SixMaxPairedFlopTrainerLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Off by default. Full physical qualification occurs once while constructing this cached bean. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.paired-flop-interval-research-enabled",
        havingValue = "true")
class PairedFlopIntervalResearchConfiguration {
    @Bean
    SixMaxPairedFlopTrainer pairedFlopIntervalResearchTrainer(
            @Value("${pokerlab.trainer.paired-flop-interval-request-path:}") String request,
            @Value("${pokerlab.trainer.paired-flop-interval-report-path:}") String report,
            @Value("${pokerlab.trainer.paired-flop-interval-report-hash:}") String hash)
            throws Exception {
        if (request.isBlank() || report.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "BTN flop interval research needs request/report paths and an exact report hash pin");
        LoggerFactory.getLogger(PairedFlopIntervalResearchConfiguration.class)
                .info(
                        "Starting full four-hand BTN physical qualification; offline replay may take 15 minutes");
        var trainer = SixMaxPairedFlopTrainerLoader.load(Path.of(request), Path.of(report), hash);
        LoggerFactory.getLogger(PairedFlopIntervalResearchConfiguration.class)
                .warn(
                        "Research-only physical flop interval drill enabled: report={}, selectedQuestions=4, trainerAdmission=false",
                        trainer.reportHash());
        return trainer;
    }
}
