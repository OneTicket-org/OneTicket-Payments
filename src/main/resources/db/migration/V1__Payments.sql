-- Create the payments table
CREATE TABLE payments (
  payment_id VARCHAR(255) PRIMARY KEY,
  -- One payment per order ([BIZ-17]): the UNIQUE constraint makes the rule race-safe across replicas.
  order_id BIGINT NOT NULL UNIQUE,
  -- Keycloak subject of the customer who placed the order (needed to hide other customers' payments, [API-18]).
  customer_id UUID NOT NULL,
  -- Money is NUMERIC(12,2) plus an ISO 4217 currency column ([BIZ-23]).
  amount NUMERIC(12, 2) NOT NULL
    CHECK (amount > 0),
  currency CHAR(3) NOT NULL
    CHECK (currency ~ '^[A-Z]{3}$'),
  status VARCHAR(20) NOT NULL
      CHECK (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'REFUNDED'))
    DEFAULT 'PENDING',
  -- Row version exposed as the strong ETag ([API-29], [API-32]).
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL
    DEFAULT NOW(),
  completed_at TIMESTAMPTZ
    CHECK ( completed_at IS NULL OR completed_at >= created_at)
);
