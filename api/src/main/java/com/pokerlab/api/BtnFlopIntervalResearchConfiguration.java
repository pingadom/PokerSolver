package com.pokerlab.api;

import com.pokerlab.solver.SixMaxBtnFlopTrainer;
import com.pokerlab.solver.SixMaxBtnFlopTrainerLoader;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Off by default. Full physical qualification occurs once while constructing this cached bean. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "pokerlab.trainer.btn-flop-interval-research-enabled",
        havingValue = "true")
class BtnFlopIntervalResearchConfiguration {
    @Bean
    SixMaxBtnFlopTrainer btnFlopIntervalResearchTrainer(
            @Value("${pokerlab.trainer.btn-flop-interval-request-path:}") String request,
            @Value("${pokerlab.trainer.btn-flop-interval-report-path:}") String report,
            @Value("${pokerlab.trainer.btn-flop-interval-report-hash:}") String hash)
            throws Exception {
        if (request.isBlank() || report.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "BTN flop interval research needs request/report paths and an exact report hash pin");
        LoggerFactory.getLogger(BtnFlopIntervalResearchConfiguration.class)
                .info(
                        "Starting full four-hand BTN physical qualification; offline replay may take 15 minutes");
        var trainer = SixMaxBtnFlopTrainerLoader.load(Path.of(request), Path.of(report), hash);
        LoggerFactory.getLogger(BtnFlopIntervalResearchConfiguration.class)
                .warn(
                        "Research-only physical flop interval drill enabled: report={}, selectedQuestions=4, trainerAdmission=false",
                        trainer.reportHash());
        return trainer;
    }
}
