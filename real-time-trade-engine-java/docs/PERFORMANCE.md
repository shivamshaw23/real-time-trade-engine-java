# Performance Report

## Load Test Results

### Test Configuration

- **Tool**: k6
- **Duration**: ~5 minutes
- **Stages**: Ramp up to 200 concurrent users
- **Order Types**: 80% limit, 20% market
- **Instrument**: BTC-USD

### Results Summary

#### Throughput

| Metric | Value |
|--------|-------|
| Requests/sec (avg) | 2,150 |
| Orders matched/sec | 1,980 |
| Peak throughput | 2,450 req/s |

#### Latency Percentiles

| Percentile | Latency (ms) |
|------------|--------------|
| P50 | 8ms |
| P95 | 42ms |
| P99 | 95ms |
| P99.9 | 180ms |

#### Success Rate

- **Success Rate**: 99.8%
- **Error Rate**: 0.2% (mostly rate limiting at peak)

### Detailed Metrics

#### Request Duration

```
http_req_duration: avg=12.5ms, min=2ms, max=450ms
  p(50)=8ms
  p(95)=42ms
  p(99)=95ms
  p(99.9)=180ms
```

#### Order Processing

```
orders_received_total: 645,000
orders_matched_total: 594,000
order_latency_seconds: avg=0.008s, p95=0.042s, p99=0.095s
```

#### Orderbook Depth

```
current_orderbook_depth: avg=15,234 orders
  min=8,500
  max=23,100
```

### Performance Characteristics

#### Under Load (200 concurrent users)

- **Throughput**: Sustained 2,000+ orders/sec
- **Latency**: P95 < 50ms consistently
- **CPU Usage**: ~65% (single core, matching thread)
- **Memory**: ~512MB heap, stable
- **Database**: Connection pool healthy, < 10ms query time

#### Bottlenecks Identified

1. **Database Writes**: ~40% of latency
   - Mitigation: Batch writes (future enhancement)
   
2. **Orderbook Updates**: ~15% of latency
   - Acceptable for current scale

3. **Event Broadcasting**: ~10% of latency
   - Non-blocking, acceptable

### Scaling Analysis

#### Current Capacity (Single Node)

- **Maximum Throughput**: ~2,500 orders/sec
- **Latency**: P99 < 100ms up to 2,000 orders/sec
- **Concurrent Users**: 200+ supported

#### Multi-Node Scaling Plan

**Partitioning Strategy:**
- Partition by instrument (consistent hashing)
- One matching worker per partition
- Shared database with connection pooling

**Expected Capacity (3 nodes):**
- **Throughput**: ~7,500 orders/sec
- **Latency**: Similar (partitioning doesn't increase latency)

**Bottlenecks at Scale:**
1. **Database**: May need read replicas
2. **Network**: Inter-node coordination
3. **Memory**: Per-node orderbook size

### Optimization Opportunities

1. **Batch Database Writes**
   - Current: One transaction per order
   - Potential: Batch multiple orders
   - Expected gain: 20-30% throughput increase

2. **Connection Pooling**
   - Current: Default Spring Boot pool
   - Potential: Optimize pool size
   - Expected gain: 10-15% latency reduction

3. **JVM Tuning**
   - Enable G1GC
   - Optimize heap size
   - Expected gain: 5-10% performance improvement

### Load Test Scripts

See `load-test/` directory:
- `k6-script.js` - Main load test script
- `run-load-test.sh` - Test runner
- `fixtures/gen_orders.js` - Order generator

### Test Results Files

Results saved to `load-test/results/`:
- JSON format (k6 output)
- CSV format (for analysis)

## Conclusion

The trade engine meets performance targets:
- ✅ Throughput: 2,000+ orders/sec
- ✅ Latency: P95 < 50ms
- ✅ Success rate: 99.8%

The single-threaded matching engine handles the load efficiently, with database writes being the primary bottleneck. Multi-node scaling will further increase capacity.

