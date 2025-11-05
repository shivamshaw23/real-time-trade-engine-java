# Trade Engine Design Document

## Architecture Overview

```
┌─────────────┐
│   HTTP API  │
│ (Controllers│
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌──────────────┐
│ OrderService│────▶│  PostgreSQL  │
└──────┬──────┘     └──────────────┘
       │
       ▼
┌─────────────────┐
│ Matching Queue  │
│ (LinkedBlocking)│
└──────┬──────────┘
       │
       ▼
┌─────────────────┐     ┌──────────────┐
│ Matching Engine │────▶│  Orderbook   │
│ (Single Thread) │     │ (In-Memory)  │
└──────┬──────────┘     └──────────────┘
       │
       ▼
┌─────────────┐     ┌──────────────┐
│  Broadcast  │────▶│  SSE Clients │
│   Service   │     └──────────────┘
└─────────────┘
```

## Key Components

### 1. HTTP API Layer

**Controllers:**
- `OrderController` - Order placement and cancellation
- `TradeController` - Trade history
- `OrderBookController` - Orderbook snapshots
- `SSEController` - Real-time event streaming
- `HealthController` - Health checks

**Responsibilities:**
- Request validation
- Rate limiting
- Response formatting
- Error handling

### 2. Service Layer

**OrderService:**
- Idempotency checking
- Order persistence
- Enqueueing to matching engine

**MatchingQueueService:**
- Non-blocking order enqueueing
- Cancel command enqueueing

**OrderBookService:**
- Orderbook snapshot retrieval
- Thread-safe read operations

### 3. Matching Engine

**Single-Threaded Worker:**
- Processes orders from `LinkedBlockingQueue<OrderCommand>`
- No locks needed (single thread)
- Atomic operations

**Matching Logic:**
- **Market Orders**: Match against best price levels until filled or book exhausted
- **Limit Orders**: Match against opposite side if price condition met, then add to book

**Trade Execution:**
- Creates Trade entities
- Updates order filled_quantity and status
- Persists in single transaction
- Broadcasts events

### 4. Orderbook

**Data Structures:**
- `NavigableMap<BigDecimal, PriceLevel>` for bids (descending)
- `NavigableMap<BigDecimal, PriceLevel>` for asks (ascending)
- `ConcurrentHashMap<UUID, OrderBookEntry>` for order lookup

**PriceLevel:**
- FIFO queue of orders at same price
- Total quantity tracking
- Fast insertion/removal

**Thread Safety:**
- Write operations: Single matching thread only
- Read operations: Volatile snapshot mechanism

### 5. Recovery Service

**Strategy: Option B - Rebuild from Database**

On startup:
1. Query all orders with `status IN ('open', 'partially_filled')`
2. Reconstruct orderbook by inserting orders
3. Preserve FIFO order within price levels

**Trade-offs:**
- ✅ Simple and robust
- ✅ No complex replay logic
- ✅ All orders persisted before enqueueing
- ❌ Slower startup with many open orders
- ❌ Requires full table scan

**Alternative (Option A - Not Implemented):**
- Periodic snapshots + replay events
- Faster startup but more complex

### 6. Broadcasting

**Server-Sent Events (SSE):**
- Channel-based subscriptions
- Automatic client cleanup
- JSON event payloads

**Event Types:**
- `TradeEvent` - Trade executions
- `OrderStateChangeEvent` - Order status updates
- `OrderbookDeltaEvent` - Orderbook changes

## Concurrency Model

### Single-Threaded Matching

**Why:**
- Eliminates race conditions
- No double-allocation of quantities
- Simpler code (no locks)
- CPU-bound, typically handles 2k+ orders/sec

**Design:**
- HTTP threads: Persist order → Enqueue command (non-blocking)
- Matching thread: Dequeue → Match → Persist → Broadcast (sequential)

### Read Operations

**Thread-Safe Reads:**
- Volatile snapshot mechanism
- Copy-on-write for orderbook snapshots
- No locking contention

**Write Operations:**
- Single matching thread only
- All orderbook modifications atomic
- DB transactions ensure consistency

## Database Schema

### Orders Table
- `order_id` (UUID, PK)
- `client_id` (text)
- `instrument` (text)
- `side` (buy/sell)
- `type` (limit/market)
- `price` (numeric, nullable for market)
- `quantity` (numeric)
- `filled_quantity` (numeric)
- `status` (open/partially_filled/filled/cancelled/rejected)
- `idempotency_key` (text, unique when not null)
- `created_at`, `updated_at` (timestamptz)

### Trades Table
- `trade_id` (UUID, PK)
- `buy_order_id` (UUID, FK)
- `sell_order_id` (UUID, FK)
- `instrument` (text)
- `price` (numeric)
- `quantity` (numeric)
- `executed_at` (timestamptz)

### Indexes
- `idx_orders_idempotency` - Unique on idempotency_key
- `idx_orders_instrument_status` - Composite for recovery queries
- `idx_trades_executed_at` - For recent trades queries

## Persistence Strategy

### Order Lifecycle

1. **Place Order (HTTP Thread):**
   - Validate request
   - Check idempotency
   - Persist order (status: `open`)
   - Enqueue to matching engine

2. **Match Order (Matching Thread):**
   - Dequeue command
   - Match against orderbook
   - Create trades
   - Update order status and filled_quantity
   - Persist all changes in transaction

3. **Recovery:**
   - On startup, query open orders
   - Reconstruct orderbook
   - Resume matching

### Transaction Boundaries

- **Order Placement**: Single transaction per order
- **Matching**: One transaction per incoming order (includes all trades and order updates)
- **Cancellation**: Single transaction

## Performance Characteristics

### Throughput

- **Target**: 2,000+ orders/second
- **Bottleneck**: Database writes (mitigated with batching)
- **Matching**: CPU-bound, very fast

### Latency

- **Order Acceptance**: < 10ms (P50)
- **Matching**: < 5ms for simple match
- **End-to-End**: < 50ms (P95)

### Scalability

**Current (Single Node):**
- Single matching thread per node
- One orderbook per instrument
- Shared database

**Multi-Node (Future):**
- Partition by instrument (consistent hashing)
- One matching worker per partition
- Shared database with optimistic locking
- Redis/Kafka for coordination

## Security & Validation

### Input Validation
- Quantity > 0
- Price > 0 (for limit orders)
- Precision: price.scale() <= 8, quantity.scale() <= 8
- Side: buy or sell
- Type: limit or market

### Rate Limiting
- Per-client: 100 requests/minute
- Bucket4j (in-memory)
- Can be extended to Redis for distributed

### Idempotency
- Unique constraint on `idempotency_key`
- Application-level check before persistence
- Prevents duplicate orders

## Observability

### Metrics (Micrometer/Prometheus)
- `orders_received_total` - Counter
- `orders_matched_total` - Counter
- `orders_rejected_total` - Counter
- `order_latency_seconds` - Histogram
- `current_orderbook_depth` - Gauge

### Logging
- Structured logs (JSON format)
- Key events: order received, matched, trade created, cancelled
- Error logging with context

### Health Checks
- Database connection
- Queue health
- Matching engine status

## Trade-offs & Design Decisions

### 1. Single-Threaded vs Multi-Threaded Matching

**Chosen: Single-threaded**
- ✅ Simpler (no locks)
- ✅ No race conditions
- ✅ Meets performance target (2k+ orders/sec)
- ❌ Can't parallelize matching

**Alternative: Multi-threaded**
- ✅ Higher throughput potential
- ❌ Complex locking/coordination
- ❌ Risk of double-allocation

### 2. Snapshot vs Rebuild Recovery

**Chosen: Rebuild (Option B)**
- ✅ Simple and robust
- ✅ No complex replay logic
- ✅ All orders persisted
- ❌ Slower startup with many orders

**Alternative: Snapshot (Option A)**
- ✅ Faster startup
- ❌ Complex replay logic
- ❌ Risk of data loss if snapshot stale

### 3. In-Memory vs Persistent Orderbook

**Chosen: In-Memory**
- ✅ Fast lookups and matching
- ✅ Low latency
- ✅ Meets performance requirements
- ❌ Requires recovery on restart

### 4. SSE vs WebSocket

**Chosen: SSE**
- ✅ Simpler (HTTP-based)
- ✅ Auto-reconnection
- ✅ Good enough for one-way events
- ❌ One-way only (not needed here)

## Future Enhancements

1. **Multi-Node Scaling**
   - Instrument partitioning
   - Consistent hashing
   - Distributed coordination

2. **Advanced Features**
   - Stop-loss orders
   - Iceberg orders
   - Order types (FOK, IOC)

3. **Performance**
   - Batch database writes
   - Connection pooling optimization
   - JIT compilation tuning

4. **Monitoring**
   - Distributed tracing
   - Advanced alerting
   - Performance dashboards

## References

- [Recovery Strategy](RECOVERY_STRATEGY.md)
- [Performance Results](../load-test/results/README.md)
- [API Documentation](../README.md)

