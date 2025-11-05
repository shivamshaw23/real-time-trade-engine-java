# Implementation Notes

## Design Decisions

### 1. Queue Implementation: `offer()` vs `put()`

**Decision**: Use `offer()` with bounded queue

**Rationale**:
- `put()` blocks indefinitely if queue is full - could cause thread pool exhaustion
- `offer()` returns false immediately - allows graceful rejection
- Bounded queue (10,000) prevents memory issues
- Throws exception on queue full - returns HTTP 503 to client

**Alternative**: Could use `put()` with unbounded queue, but risks memory growth

### 2. OrderBook Order Map: HashMap vs ConcurrentHashMap

**Current**: `ConcurrentHashMap<UUID, OrderBookEntry>`

**Alternative**: `HashMap<UUID, OrderBookEntry>` (since only matching thread writes)

**Rationale**: 
- ConcurrentHashMap is slightly overkill but safe
- Allows future read access from other threads
- Minimal performance difference for our use case
- More defensive programming

### 3. Snapshot Mechanism: Volatile vs Synchronized

**Current**: `volatile OrderBookSnapshot snapshot`

**Alternative**: `synchronized Snapshot getTopLevels()`

**Rationale**:
- Volatile read/write is faster than synchronized block
- Single writer (matching thread) + volatile ensures visibility
- No blocking on read operations
- Better performance for high read throughput

### 4. Error Handling in Matching Loop

**Pattern**: Try-catch around command processing

**Rationale**:
- Prevents one bad order from crashing matching engine
- Logs errors with context
- Continues processing other orders
- Could add dead letter queue for failed orders (future enhancement)

### 5. Idempotency Response Codes

**Current**: Returns existing order (same response for new and existing)

**Alternative**: Return 200 OK for existing, 201 CREATED for new

**Rationale**:
- Simpler implementation
- Consistent response format
- Client can check order_id to determine if it's new or existing
- HTTP semantics less important than functionality

## Performance Considerations

### Queue Size
- **Bounded**: 10,000 orders
- **Rationale**: At 2,000 orders/sec, queue can hold ~5 seconds of backlog
- **Rejection**: If queue full, reject with clear error message

### Database Transactions
- **Current**: One transaction per incoming order
- **Future**: Could batch multiple orders for better throughput
- **Trade-off**: Simplicity vs performance

### Snapshot Frequency
- **Current**: Updated after each orderbook modification
- **Alternative**: Periodic updates (every N orders or every X ms)
- **Trade-off**: Real-time accuracy vs performance

## Code Quality

### Separation of Concerns
- **Controller**: HTTP handling, validation, rate limiting
- **Service**: Business logic, idempotency, persistence
- **Matching Engine**: Order matching, trade creation
- **Orderbook**: Data structure management

### Error Handling
- **Controller**: HTTP error responses
- **Service**: Business exceptions
- **Matching Engine**: Logging and continuation

### Testing
- **Unit tests**: Mock dependencies
- **Integration tests**: Testcontainers with real database
- **Load tests**: k6 scripts for performance validation

