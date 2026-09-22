package com.pokerlab.shared;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(name = "pokerlab.queue.mode", havingValue = "sqs")
public class QueueConfiguration {
    @Bean(destroyMethod = "close")
    SqsClient sqsClient(
            @Value("${pokerlab.queue.region:eu-west-2}") String region,
            @Value("${pokerlab.queue.endpoint:}") String endpoint) {
        var builder =
                SqsClient.builder()
                        .region(Region.of(region))
                        .overrideConfiguration(
                                c ->
                                        c.apiCallTimeout(Duration.ofSeconds(25))
                                                .apiCallAttemptTimeout(Duration.ofSeconds(15)));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    @Bean
    JobQueue sqsQueue(
            SqsClient client,
            JsonCodec json,
            @Value("${pokerlab.queue.url}") String url,
            @Value("${pokerlab.queue.dlq-url}") String dlq) {
        return new SqsJobQueue(client, url, dlq, json);
    }
}
