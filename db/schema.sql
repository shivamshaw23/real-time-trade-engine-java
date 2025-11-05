-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Orders table
CREATE TABLE orders (
  order_id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  client_id text NOT NULL,
  instrument text NOT NULL,
  side text NOT NULL CHECK (side IN ('buy','sell')),
  type text NOT NULL CHECK (type IN ('limit','market')),
  price numeric(18,8), -- nullable for market
  quantity numeric(30,8) NOT NULL,
  filled_quantity numeric(30,8) NOT NULL DEFAULT 0,
  status text NOT NULL CHECK (status IN ('open','partially_filled','filled','cancelled','rejected')),
  idempotency_key text, -- nullable
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Unique index for idempotency key (only when not null)
CREATE UNIQUE INDEX idx_orders_idempotency ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Index for finding open orders quickly by instrument and status
CREATE INDEX idx_orders_instrument_status ON orders(instrument, status);

-- Trades table
CREATE TABLE trades (
  trade_id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  buy_order_id uuid NOT NULL REFERENCES orders(order_id),
  sell_order_id uuid NOT NULL REFERENCES orders(order_id),
  instrument text NOT NULL,
  price numeric(18,8) NOT NULL,
  quantity numeric(30,8) NOT NULL,
  executed_at timestamptz NOT NULL DEFAULT now()
);

-- Index on executed_at for recent trades queries
CREATE INDEX idx_trades_executed_at ON trades(executed_at);

-- Orderbook snapshots table
CREATE TABLE orderbook_snapshots (
  snapshot_id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  instrument text NOT NULL,
  snapshot_time timestamptz NOT NULL DEFAULT now(),
  snapshot_json jsonb NOT NULL
);

