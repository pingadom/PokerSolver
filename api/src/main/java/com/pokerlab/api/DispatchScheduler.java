package com.pokerlab.api;

import com.pokerlab.shared.*;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchScheduler {
    private final OutboxDispatcher dispatcher;
    private final JobQueue queue;

    public DispatchScheduler(OutboxDispatcher dispatcher, JobQueue queue) {
        this.dispatcher = dispatcher;
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${pokerlab.dispatch-delay-ms:500}")
    public void dispatch() {
        try {
            dispatcher.dispatch(queue);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Outbox dispatch will retry", e);
        }
    }
}
