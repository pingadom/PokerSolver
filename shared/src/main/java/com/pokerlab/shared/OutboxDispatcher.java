package com.pokerlab.shared;

import com.pokerlab.core.batch.BatchJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public OutboxDispatcher(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Send before marking published; a crash can duplicate but cannot lose the committed job. */
    @Transactional
    public int dispatch(JobQueue queue) {
        var jobs =
                jdbc.query(
                        "SELECT payload::text FROM batch_outbox WHERE published_at IS NULL ORDER BY simulation_id,batch_id LIMIT 10 FOR UPDATE SKIP LOCKED",
                        (rs, n) -> json.read(rs.getString(1), BatchJob.class));
        for (var job : jobs) {
            queue.send(job);
            jdbc.update(
                    "UPDATE batch_outbox SET published_at=now() WHERE simulation_id=? AND batch_id=?",
                    job.simulationId(),
                    job.batchId());
        }
        return jobs.size();
    }
}
