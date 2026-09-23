package com.pokerlab.shared;

import com.pokerlab.core.batch.BatchJob;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Durable development-only transport with leases, receipt rotation and five-attempt redrive. */
@Component
@ConditionalOnProperty(name = "pokerlab.queue.mode", havingValue = "postgres")
public class PostgresJobQueue implements JobQueue {
    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public PostgresJobQueue(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void send(BatchJob job) {
        jdbc.update(
                "INSERT INTO development_queue(id,body) VALUES (?,?)",
                UUID.randomUUID(),
                json.write(job));
    }

    @Override
    @Transactional
    public List<Delivery> receive() {
        return lease(false);
    }

    @Override
    @Transactional
    public List<Delivery> receiveDeadLetters() {
        return lease(true);
    }

    private List<Delivery> lease(boolean dead) {
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM development_queue WHERE available_at<=now() AND NOT dead_letter_handled AND receive_count "
                                + (dead ? ">=" : "<")
                                + " 5 ORDER BY available_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                        UUID.class);
        if (ids.isEmpty()) return List.of();
        var receipt = UUID.randomUUID();
        return jdbc.query(
                "UPDATE development_queue SET receipt=?,receive_count=receive_count+1,available_at=now()+interval '120 seconds' WHERE id=? RETURNING body,receive_count",
                (rs, n) -> new Delivery(receipt.toString(), rs.getString(1), rs.getInt(2)),
                receipt,
                ids.getFirst());
    }

    @Override
    public void acknowledge(Delivery delivery) {
        jdbc.update(
                "DELETE FROM development_queue WHERE receipt=?",
                UUID.fromString(delivery.receipt()));
    }

    @Override
    public void acknowledgeDeadLetter(Delivery delivery) {
        jdbc.update(
                "UPDATE development_queue SET dead_letter_handled=true WHERE receipt=?",
                UUID.fromString(delivery.receipt()));
    }

    @Override
    public void extend(Delivery delivery) {
        jdbc.update(
                "UPDATE development_queue SET available_at=now()+interval '120 seconds' WHERE receipt=?",
                UUID.fromString(delivery.receipt()));
    }
}
