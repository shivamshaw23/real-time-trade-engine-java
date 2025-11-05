# Real-Time Trade Engine

A high-performance, real-time trading engine built with Java/Spring Boot, featuring in-memory orderbook management, single-threaded matching engine, and real-time event broadcasting.

## Features

- **Order Management**: Place limit and market orders with idempotency support
- **Matching Engine**: Single-threaded, lock-free matching with price-time priority
- **Orderbook**: In-memory orderbook with thread-safe snapshots for reads
- **Real-time Events**: Server-Sent Events (SSE) for trades, orderbook deltas, and order state changes
- **Recovery**: Automatic orderbook reconstruction from database on startup
- **Metrics**: Prometheus metrics exposed via `/actuator/prometheus`
- **Rate Limiting**: Per-client rate limiting (100 requests/minute)
- **Health Checks**: Database and queue health monitoring

## Architecture

- **Single-threaded Matching**: Eliminates race conditions and ensures consistency
- **In-Memory Orderbook**: Fast price-level lookups with NavigableMap
- **Persistent Storage**: PostgreSQL for orders and trades
- **Event Broadcasting**: SSE for real-time updates
- **Recovery Strategy**: Rebuild orderbook from open orders on startup

See [docs/DESIGN.md](docs/DESIGN.md) for detailed architecture documentation.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 15+
- Docker & Docker Compose (optional, for containerized deployment)

## Build

```bash
./mvnw clean package
```

Or using Maven directly:

```bash
mvn clean package
```

## Run Locally

### 1. Start PostgreSQL

```bash
# Using Docker
docker run -d \
  --name trade-postgres \
  -e POSTGRES_USER=trade \
  -e POSTGRES_PASSWORD=trade \
  -e POSTGRES_DB=trade \
  -p 5432:5432 \
  postgres:15

# Initialize schema
psql -h localhost -U trade -d trade -f db/schema.sql
```

### 2. Configure Application

Create `application.properties` or set environment variables:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/trade
spring.datasource.username=trade
spring.datasource.password=trade
spring.datasource.driver-class-name=org.postgresql.Driver
```

### 3. Run Application

```bash
java -jar target/trade-engine-*.jar
```

Or using Maven:

```bash
./mvnw spring-boot:run
```

## Docker Compose

Quick start with Docker Compose:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Application (port 8080)
- Prometheus (port 9090)

## API Endpoints

### Orders

- `POST /orders` - Place a new order
- `POST /orders/{order_id}/cancel` - Cancel an order
- `GET /orders/{order_id}` - Get order status

### Orderbook

- `GET /orderbook?instrument=BTC-USD&levels=20` - Get orderbook snapshot

### Trades

- `GET /trades?limit=50` - Get recent trades

### Events (SSE)

- `GET /events/trades` - Subscribe to trade events
- `GET /events/orderbook` - Subscribe to orderbook deltas
- `GET /events/orders` - Subscribe to order state changes

### Health & Metrics

- `GET /healthz` - Health check
- `GET /actuator/prometheus` - Prometheus metrics

## Example Requests

### Place Limit Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "client-1",
    "instrument": "BTC-USD",
    "side": "buy",
    "type": "limit",
    "price": 70000.50,
    "quantity": 0.25,
    "idempotency_key": "order-123"
  }'
```

### Place Market Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "client-1",
    "instrument": "BTC-USD",
    "side": "buy",
    "type": "market",
    "quantity": 0.5
  }'
```

### Cancel Order

```bash
curl -X POST http://localhost:8080/orders/{order_id}/cancel
```

### Get Orderbook

```bash
curl "http://localhost:8080/orderbook?instrument=BTC-USD&levels=20"
```

### Subscribe to Events (SSE)

```bash
curl -N http://localhost:8080/events/trades
```

## Load Testing

### Generate Test Orders

```bash
# Install Node.js dependencies (if needed)
npm install

# Generate 100k limit orders
node fixtures/gen_orders.js
```

### Run k6 Load Test

```bash
# Install k6: https://k6.io/docs/getting-started/installation/

# Run load test
cd load-test
./run-load-test.sh http://localhost:8080

# Results will be saved to load-test/results/

# View metrics during load test
curl http://localhost:8080/actuator/prometheus | grep orders_received_total
```

## Testing

### Unit Tests

```bash
./mvnw test
```

### Integration Tests

Integration tests use Testcontainers and require Docker:

```bash
./mvnw verify
```

## Monitoring

### Prometheus

If using Docker Compose, Prometheus is available at http://localhost:9090

### Metrics

Access metrics at: `http://localhost:8080/actuator/prometheus`

Key metrics:
- `orders_received_total` - Total orders received
- `orders_matched_total` - Total orders matched
- `order_latency_seconds` - Order processing latency
- `current_orderbook_depth` - Current orderbook depth

### View Metrics

```bash
# Query Prometheus
curl http://localhost:9090/api/v1/query?query=orders_received_total

# Or use Prometheus UI
open http://localhost:9090
```

## Configuration

### Rate Limiting

Default: 100 requests per minute per client

Configure in `RateLimitService.java`:
```java
private static final int REQUESTS_PER_MINUTE = 100;
```

### Matching Engine

The matching engine runs in a single thread. Adjust queue size if needed:
```java
private final LinkedBlockingQueue<OrderCommand> queue = new LinkedBlockingQueue<>();
```

## Recovery

On startup, the engine automatically:
1. Queries all open/partially_filled orders from database
2. Reconstructs the in-memory orderbook
3. Resumes matching

See [docs/RECOVERY_STRATEGY.md](docs/RECOVERY_STRATEGY.md) for details.

## Performance

Target performance:
- **Throughput**: 2,000+ orders/second
- **Latency**: P50 < 10ms, P95 < 50ms, P99 < 100ms
- **Matching**: Single-threaded, CPU-bound

See [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for load test results.

## Scaling

For multi-node deployment:
- Partition by instrument (consistent hashing)
- One matching worker per instrument partition
- Shared database for persistence
- Redis/Kafka for inter-node coordination

## Development

### Project Structure

```
src/
  main/
    java/com/tradeengine/
      controller/     # REST endpoints
      service/        # Business logic
      matching/       # Matching engine
      orderbook/      # Orderbook data structures
      broadcast/      # SSE event broadcasting
      recovery/       # Recovery logic
      security/       # Rate limiting
  test/
    java/             # Unit and integration tests
```

## License

[Your License Here]

## Contributing

[Your Contributing Guidelines Here]

