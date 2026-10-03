package com.pokerlab.api;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.pokerlab.api", "com.pokerlab.shared"})
@EnableScheduling
public class ApiApplication {
    public static void main(String[] args) {
        applicationFor(args).run(args);
    }

    static SpringApplication applicationFor(String[] args) {
        if (!Arrays.asList(args).contains("--research-preview"))
            return new SpringApplication(ApiApplication.class);
        var application = new SpringApplication(ResearchPreview.class);
        application.setAdditionalProfiles("research-preview");
        return application;
    }

    /** Explicit local saved-pack preview; no simulation services or persistent infrastructure. */
    @Configuration(proxyBeanMethods = false)
    @Profile("research-preview")
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
            })
    @Import({
        SixMaxPreflopResearchController.class,
        SixMaxPreflopResearchConfiguration.class,
        ApiErrors.class
    })
    public static class ResearchPreview {}
}
