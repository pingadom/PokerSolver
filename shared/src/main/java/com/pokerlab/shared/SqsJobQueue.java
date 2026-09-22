package com.pokerlab.shared;

import com.pokerlab.core.batch.BatchJob;
import java.util.List;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class SqsJobQueue implements JobQueue {
    private final SqsClient client;
    private final String queueUrl;
    private final String deadLetterUrl;
    private final JsonCodec json;

    public SqsJobQueue(SqsClient client, String queueUrl, String deadLetterUrl, JsonCodec json) {
        this.client = client;
        this.queueUrl = queueUrl;
        this.deadLetterUrl = deadLetterUrl;
        this.json = json;
    }

    @Override
    public void send(BatchJob job) {
        client.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(json.write(job))
                        .build());
    }

    @Override
    public List<Delivery> receive() {
        return receive(queueUrl, 10);
    }

    @Override
    public List<Delivery> receiveDeadLetters() {
        return receive(deadLetterUrl, 0);
    }

    private List<Delivery> receive(String url, int wait) {
        return client
                .receiveMessage(
                        ReceiveMessageRequest.builder()
                                .queueUrl(url)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(wait)
                                .visibilityTimeout(120)
                                .messageSystemAttributeNames(
                                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                                .build())
                .messages()
                .stream()
                .map(
                        m ->
                                new Delivery(
                                        m.receiptHandle(),
                                        m.body(),
                                        Integer.parseInt(
                                                m.attributes()
                                                        .getOrDefault(
                                                                MessageSystemAttributeName
                                                                        .APPROXIMATE_RECEIVE_COUNT,
                                                                "1"))))
                .toList();
    }

    @Override
    public void acknowledge(Delivery delivery) {
        delete(queueUrl, delivery);
    }

    @Override
    public void acknowledgeDeadLetter(Delivery delivery) {
        delete(deadLetterUrl, delivery);
    }

    private void delete(String url, Delivery delivery) {
        client.deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(url)
                        .receiptHandle(delivery.receipt())
                        .build());
    }

    @Override
    public void extend(Delivery delivery) {
        client.changeMessageVisibility(
                ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(delivery.receipt())
                        .visibilityTimeout(120)
                        .build());
    }
}
