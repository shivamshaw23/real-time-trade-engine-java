# Edge Cases & Important Behaviors

## Implemented Edge Cases

### 1. Market Orders When Book is Empty

**Behavior**: Market order with no liquidity is partially filled (with 0 filled) and remaining quantity is rejected.

**Implementation**:
- Market orders attempt to match against opposite side
- If book is empty, no trades are created
- Order status set to `partially_filled` (even if nothing filled)
- Remaining quantity is NOT added to orderbook (market orders don't rest)
- Logged as warning for monitoring

**Alternative Behaviors Considered**:
- Reject entire order: Too strict - partial fills should be honored
- Set status to "rejected": Not accurate - order was accepted, just couldn't fill
- Keep as "open": Incorrect - market orders execute immediately or not at all

**Current Choice**: `partially_filled` with 0 filled_quantity - accurate representation

### 2. Partial Fills Across Many Price Levels

**Behavior**: Market orders can fill across multiple price levels until fully filled or book exhausted.

**Implementation**:
- Matching loop continues while `remainingQty > 0` and opposite book has liquidity
- Each iteration matches against best price level
- Creates separate Trade for each price level
- Updates both incoming and resting orders after each match
- All trades and order updates persisted in single transaction

**Test Coverage**: `EdgeCaseTests.testPartialFill_AcrossManyPriceLevels()`

### 3. Cancel Concurrently with Match - Queue Ordering

**Behavior**: Cancel commands are processed in FIFO order relative to place commands.

**Implementation**:
- `LinkedBlockingQueue` ensures FIFO ordering
- If cancel is enqueued before match, cancel is processed first
- If cancel is enqueued after match, match processes first, then cancel
- Orderbook operations are atomic within matching thread
- No race conditions possible due to single-threaded matching

**Guarantee**: Commands are processed in the order they appear in the queue.

**Test Coverage**: `EdgeCaseTests.testCancel_ConcurrentWithMatch_QueueOrdering()`

### 4. Duplicate Submission with Same Idempotency Key

**Behavior**: Returns same order, does NOT re-execute matching.

**Implementation**:
- Idempotency check in `OrderService.placeOrder()` BEFORE persistence
- If order exists with same idempotency_key, returns existing order immediately
- Order is NOT enqueued to matching engine again
- Returns HTTP 201 (same response format) - client can check order_id to determine if new or existing

**Database Constraint**: 
```sql
CREATE UNIQUE INDEX idx_orders_idempotency ON orders(idempotency_key) 
WHERE idempotency_key IS NOT NULL;
```

**Test Coverage**: `OrderMatchingIntegrationTest.testIdempotency()`

### 5. Database Outage Handling

**Design Choice**: Simpler approach - HTTP acceptance persists order; if DB down, reject POSTs with 503. Worker catches DB exceptions, pauses, and retries.

**Implementation**:

#### HTTP Layer (OrderController):
- Order persistence happens in `OrderService.placeOrder()` with `@Transactional`
- If DB is down, transaction fails and HTTP request returns 503
- Order is NOT enqueued if persistence fails
- Client receives clear error message

#### Matching Worker:
- Database operations wrapped in `DatabaseRetryHandler.executeWithRetry()`
- Exponential backoff: 100ms → 200ms → 400ms → 800ms → 1600ms (capped at 5s)
- Maximum 5 retry attempts
- If all retries fail, matching engine pauses with exponential backoff
- Worker continues accepting commands but pauses processing until DB recovers

**Retry Logic**:
```java
// Initial backoff: 100ms
// Exponential: 100ms → 200ms → 400ms → 800ms → 1600ms
// Max backoff: 5000ms
// Max retries: 5
```

**Pause Behavior**:
- On critical DB error, matching engine pauses
- Pause duration: 1s → 2s → 4s → 8s → 10s (capped)
- After pause, resumes and retries
- Queue continues accepting commands (non-blocking)

**Benefits**:
- ✅ Prevents order loss during transient DB issues
- ✅ Graceful degradation - accepts orders but pauses matching
- ✅ Automatic recovery when DB comes back
- ✅ Clear error messages to clients

**Trade-offs**:
- Orders may queue up during DB outage
- Matching resumes automatically when DB recovers
- No manual intervention needed for transient failures

## Test Coverage

All edge cases are covered in:
- `EdgeCaseTests.java` - Unit tests for edge cases
- `OrderMatchingIntegrationTest.java` - Integration test for idempotency
- `MatchingEngineTest.java` - General matching scenarios

## Monitoring

### Metrics to Watch:
- `orders_rejected_total` - Tracks rejected orders (including DB failures)
- Queue size - Monitor for backlog during DB outages
- DB connection pool - Monitor for connection issues

### Logs to Monitor:
- "Market order partially filled with X remaining (book exhausted)" - Market order liquidity issues
- "Database operation failed, retrying" - DB retry attempts
- "Matching engine paused" - Critical DB failures
- "Idempotent request detected" - Duplicate submissions

## Future Enhancements

1. **Dead Letter Queue**: For orders that fail after all retries
2. **Circuit Breaker**: Temporarily stop accepting orders if DB consistently failing
3. **Manual Intervention**: Admin endpoint to pause/resume matching engine
4. **Alerting**: Alert on matching engine pauses or sustained DB failures

