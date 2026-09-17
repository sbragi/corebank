CREATE TABLE bank_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    balance_after NUMERIC(19,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bank_transaction_idempotency UNIQUE (idempotency_key),
    CONSTRAINT bank_transaction_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_bank_transaction_account FOREIGN KEY (account_id) REFERENCES account(id)
);

CREATE INDEX idx_bank_transaction_account ON bank_transaction(account_id);
CREATE INDEX idx_bank_transaction_created_at ON bank_transaction(created_at);
