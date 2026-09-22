CREATE TABLE simulations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    configuration JSONB NOT NULL,
    requested_iterations BIGINT NOT NULL CHECK (requested_iterations > 0),
    completed_iterations BIGINT NOT NULL DEFAULT 0 CHECK (completed_iterations >= 0 AND completed_iterations <= requested_iterations),
    seed BIGINT NOT NULL,
    batch_size INTEGER NOT NULL CHECK (batch_size > 0),
    total_batches INTEGER NOT NULL CHECK (total_batches > 0),
    completed_batches INTEGER NOT NULL DEFAULT 0 CHECK (completed_batches >= 0 AND completed_batches <= total_batches),
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX simulations_recent_idx ON simulations(created_at DESC);
CREATE TABLE simulation_batches (
    simulation_id UUID NOT NULL REFERENCES simulations(id),
    batch_id INTEGER NOT NULL CHECK (batch_id >= 0),
    iterations INTEGER NOT NULL CHECK (iterations > 0),
    seed BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (simulation_id, batch_id)
);
CREATE TABLE batch_results (
    simulation_id UUID NOT NULL,
    batch_id INTEGER NOT NULL,
    payload JSONB NOT NULL,
    trials BIGINT NOT NULL CHECK (trials > 0),
    elapsed_ms BIGINT NOT NULL CHECK (elapsed_ms >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (simulation_id, batch_id),
    FOREIGN KEY (simulation_id, batch_id) REFERENCES simulation_batches(simulation_id, batch_id)
);
CREATE TABLE simulation_results (
    simulation_id UUID PRIMARY KEY REFERENCES simulations(id),
    payload JSONB NOT NULL,
    total_trials BIGINT NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    finalised_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Transactional outbox closes the commit-to-queue crash window.
CREATE TABLE batch_outbox (
    simulation_id UUID NOT NULL,
    batch_id INTEGER NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    PRIMARY KEY (simulation_id, batch_id),
    FOREIGN KEY (simulation_id, batch_id) REFERENCES simulation_batches(simulation_id, batch_id)
);
CREATE INDEX batch_outbox_pending_idx ON batch_outbox(simulation_id, batch_id) WHERE published_at IS NULL;
