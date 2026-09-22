package com.pokerlab.shared;

import com.pokerlab.core.batch.BatchJob;
import java.util.List;

public interface JobQueue {
    record Delivery(String receipt, String body, int receiveCount) {}

    void send(BatchJob job);

    List<Delivery> receive();

    void acknowledge(Delivery delivery);

    void extend(Delivery delivery);
}
