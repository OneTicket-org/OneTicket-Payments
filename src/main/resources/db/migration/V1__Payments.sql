-- Create the payments table
Create TABLE payments (
  payment_id VARCHAR(255) PRIMARY KEY,
  order_id INTEGER NOT NULL,
  amount_cents INTEGER NOT NULL
    CHECK ( amount_cents >= 0),
  currency CHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL
      CHECK (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'REFUNDED'))
    DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL
    DEFAULT NOW(),
  completed_at TIMESTAMPTZ
    CHECK ( completed_at IS NULL OR completed_at >= created_at)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);

-- optionally, you can enforce that only one payment per order can be in a non-terminal state (PENDING, TIMED_OUT)
-- by creating a unique index on the combination of order_id and status, but this is commented out for now.
-- UNIQUE INDEX idx_payments_order_id_status ON payments(order_id, status);
