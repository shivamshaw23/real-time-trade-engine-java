# Orderbook Recovery Strategy

## Overview

This document describes the recovery strategy implemented for the trading engine to handle restarts and ensure state consistency.

## Recovery Approach: Option B - Rebuild by Replaying Persisted Open Orders

### Implementation

On startup, before the matching engine begins processing:

1. **Query Database**: Query all orders with `status IN ('open', 'partially_filled')` ordered by `created_at`
2. **Reconstruct Orderbook**: Insert each order into the in-memory orderbook based on its remaining quantity
3. **Preserve State**: Orders are added to the orderbook in the same order they were created (FIFO within price levels)

### Key Design Decisions

#### Phase 5 - Concurrency & Correctness

1. **Single-threaded Matching Loop**
   - Only the matching engine worker thread mutates the in-memory orderbook
   - HTTP threads only insert orders into DB and enqueue commands
   - Eliminates double-allocation race conditions

2. **Idempotency Key**
   - Database constraint: `UNIQUE INDEX idx_orders_idempotency ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL`
   - Ensures duplicate POSTs don't create new order rows
   - Handled at the application level in `OrderService.placeOrder()`

3. **Cancellation**
   - Cancel requests insert a `CANCEL` command into the queue
   - The matching worker atomically removes the order from the price level
   - If cancel received after partial fills, worker sets final state appropriately

4. **DB Consistency**
   - Orders are persisted (status `open`) **before** enqueueing to matching engine
   - This ensures restarts can reconstruct state from DB
   - See `OrderService.placeOrder()` - order is saved before `enqueueOrder()`

#### Phase 6 - Persistence & Recovery

**Recovery Process:**
- `RecoveryService.recoverOrderbookState()` runs on startup (before matching engine starts)
- Reconstructs orderbook from database state
- Handles limit orders (adds to price levels) and market orders (adds to orderbook entry map)

**Order Handling:**
- **Limit Orders**: Added to appropriate price level (bids or asks) with remaining quantity
- **Market Orders**: Added to orderbook entry map (they should be matched immediately)
- **Partially Filled Orders**: Recovered with correct remaining quantity = `quantity - filled_quantity`

### Trade-offs

#### Pros
- **Simple**: No complex replay logic or snapshot management
- **Robust**: All orders are persisted before enqueueing, so we have complete state
- **Correct**: Reconstructs exact state from database
- **No Data Loss**: All open orders are preserved across restarts

#### Cons
- **Startup Time**: Slower startup if there are many open orders (requires full table scan)
  - Mitigated by index on `status` column: `idx_orders_instrument_status`
  - Acceptable for prototype/single-node deployment
- **Memory**: All open orders must fit in memory (already a requirement for orderbook)

### Alternative Approach (Not Implemented)

**Option A: Periodic Snapshot + Replay Events**

- Periodically write `orderbook_snapshots` (JSON-serialized map of price levels)
- On restart: Load last snapshot + replay events since snapshot time
- **Pros**: Faster startup if snapshots are frequent
- **Cons**: 
  - Complex replay logic
  - Need to ensure correct replay window
  - Potential for data loss if snapshot is stale
  - More complex state management

### Recovery Flow

```
1. Application Startup
   ↓
2. RecoveryService.recoverOrderbookState()
   ↓
3. Query: SELECT * FROM orders WHERE status IN ('open', 'partially_filled') ORDER BY created_at
   ↓
4. For each order:
   - Calculate remaining_qty = quantity - filled_quantity
   - Add to OrderBook (limit orders → price levels, market orders → entry map)
   ↓
5. MatchingEngine.start() - Begin processing queue
```

### Failure Scenarios Handled

1. **System Crash During Matching**
   - Orders already persisted to DB are recovered
   - Orders in queue are lost (acceptable - they can be resubmitted)
   - In-flight matches: handled by DB transaction rollback

2. **System Crash Before Persistence**
   - Order not in DB → not recovered (expected behavior)
   - Client can retry with same idempotency key

3. **System Crash During Recovery**
   - Recovery is idempotent (orders are added to orderbook, duplicates are handled)
   - Can restart and recover again

### Testing Recovery

To test recovery:
1. Place some orders
2. Stop the application
3. Restart the application
4. Verify orders are recovered by checking `/orderbook` endpoint
5. Verify orders can still be matched/cancelled

### Future Enhancements

For production scale, consider:
- **Option A** (snapshot + replay) for faster startup
- **Partitioning by instrument** with consistent hashing for multi-node
- **Incremental snapshots** (only changed price levels)
- **Recovery metrics** (time taken, orders recovered, etc.)

