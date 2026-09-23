-- Optional durable development transport; production uses SQS.
CREATE TABLE development_queue (
    id UUID PRIMARY KEY,
    body TEXT NOT NULL,
    receipt UUID,
    receive_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    dead_letter_handled BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX development_queue_available_idx ON development_queue(available_at) WHERE NOT dead_letter_handled;
