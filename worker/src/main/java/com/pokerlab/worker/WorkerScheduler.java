package com.pokerlab.worker;

import com.pokerlab.shared.JobQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerScheduler {
    private final JobQueue queue;
    private final BatchProcessor processor;

    public WorkerScheduler(JobQueue queue, BatchProcessor processor) {
        this.queue = queue;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${pokerlab.worker.poll-delay-ms:100}")
    public void poll() {
        queue.receive().forEach(processor::process);
    }

    @Scheduled(fixedDelay = 5000)
    public void deadLetters() {
        queue.receiveDeadLetters().forEach(processor::deadLetter);
    }
}
