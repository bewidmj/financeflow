ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS account_id UUID,
    ADD COLUMN IF NOT EXISTS transaction_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS persistence_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_financial_transactions_account_id
    ON financial_transactions (account_id);

CREATE TABLE IF NOT EXISTS finance_transaction_outbox (
    id UUID PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_until TIMESTAMPTZ,
    claim_token UUID,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT chk_finance_outbox_status
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'RETRY', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_finance_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_outbox_aggregate_version
    ON finance_transaction_outbox (aggregate_id, aggregate_version);

CREATE INDEX IF NOT EXISTS idx_finance_outbox_pending
    ON finance_transaction_outbox (status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS finance_csv_import_rows (
    owner_id UUID NOT NULL,
    file_hash CHAR(64) NOT NULL,
    row_number BIGINT NOT NULL,
    transaction_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_id, file_hash, row_number),
    CONSTRAINT chk_finance_csv_row_number CHECK (row_number > 0)
);
