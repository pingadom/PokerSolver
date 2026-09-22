package com.pokerlab.worker;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pokerlab.core.batch.*;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import com.pokerlab.shared.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchProcessorTest {
    private final ResultAggregator aggregator = mock(ResultAggregator.class);
    private final JobQueue queue = mock(JobQueue.class);
    private final JsonCodec json = new JsonCodec(JsonMapper.builder().findAndAddModules().build());
    private final BatchProcessor processor =
            new BatchProcessor(aggregator, queue, json, new SimpleMeterRegistry());

    private BatchJob job() {
        var config =
                new SimulationConfiguration(
                        List.of(
                                new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                                new PlayerHand("KK", Card.parse("KS"), Card.parse("KH"))),
                        List.of(),
                        10,
                        10,
                        42);
        return BatchPlanner.plan(UUID.randomUUID(), config).getFirst();
    }

    @Test
    void acknowledgesOnlyAfterDurableSubmission() {
        var job = job();
        var delivery = new JobQueue.Delivery("receipt", json.write(job), 1);
        when(aggregator.start(job)).thenReturn(true);
        when(aggregator.submit(eq(job), any())).thenReturn(true);
        processor.process(delivery);
        var order = inOrder(aggregator, queue);
        order.verify(aggregator).start(job);
        order.verify(aggregator).submit(eq(job), any());
        order.verify(queue).acknowledge(delivery);
    }

    @Test
    void transientFailureRemainsUnacknowledgedThenRetries() {
        var job = job();
        var delivery = new JobQueue.Delivery("receipt", json.write(job), 2);
        when(aggregator.start(job))
                .thenThrow(new IllegalStateException("temporary database outage"))
                .thenReturn(true);
        processor.process(delivery);
        verify(queue, never()).acknowledge(any());
        processor.process(delivery);
        verify(queue).acknowledge(delivery);
    }

    @Test
    void poisonMessageIsRetainedForRedriveAndInspection() {
        var delivery = new JobQueue.Delivery("receipt", "not json", 1);
        processor.process(delivery);
        processor.deadLetter(delivery);
        verifyNoInteractions(aggregator, queue);
    }

    @Test
    void deadLetterRecordsFailureBeforeAcknowledging() {
        var job = job();
        var delivery = new JobQueue.Delivery("receipt", json.write(job), 6);
        processor.deadLetter(delivery);
        var order = inOrder(aggregator, queue);
        order.verify(aggregator).fail(eq(job), anyString());
        order.verify(queue).acknowledgeDeadLetter(delivery);
    }
}
