# Quick Start Guide

## 5-Minute Setup

### 1. Start Everything

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Application (port 8080)
- Prometheus (port 9090)

### 2. Verify Health

```bash
curl http://localhost:8080/healthz
```

Expected: `{"status":"UP","database":"UP","queue":"UP"}`

### 3. Place Your First Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "client-1",
    "instrument": "BTC-USD",
    "side": "buy",
    "type": "limit",
    "price": 70000.50,
    "quantity": 0.25
  }'
```

### 4. View Orderbook

```bash
curl "http://localhost:8080/orderbook?instrument=BTC-USD&levels=20"
```

### 5. Subscribe to Events (in another terminal)

```bash
curl -N http://localhost:8080/events/trades
```

## Running Load Tests

```bash
# Install k6 (if not installed)
# https://k6.io/docs/getting-started/installation/

# Run load test
cd load-test
./run-load-test.sh http://localhost:8080

# View results
cat results/result_*.json
```

## View Metrics

```bash
# Prometheus metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Or open Prometheus UI
open http://localhost:9090
```

## Import Postman Collection

1. Open Postman
2. Import → `postman/TradeEngine.postman_collection.json`
3. Set `base_url` variable to `http://localhost:8080`
4. Start testing!

## Run Tests

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify
```

## Stop Everything

```bash
docker-compose down
```

