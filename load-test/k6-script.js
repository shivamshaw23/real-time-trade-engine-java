import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const orderSuccessRate = new Rate('order_success_rate');
const orderLatency = new Trend('order_latency_ms');

// Test configuration
export const options = {
    stages: [
        { duration: '30s', target: 50 },   // Ramp up to 50 users
        { duration: '1m', target: 50 },    // Stay at 50 users
        { duration: '30s', target: 100 },  // Ramp up to 100 users
        { duration: '1m', target: 100 },   // Stay at 100 users
        { duration: '30s', target: 200 },   // Ramp up to 200 users
        { duration: '1m', target: 200 },   // Stay at 200 users
        { duration: '30s', target: 0 },    // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(50)<200', 'p(95)<500', 'p(99)<1000'], // Latency percentiles
        'http_req_failed': ['rate<0.01'], // Error rate < 1%
        'order_success_rate': ['rate>0.99'], // Success rate > 99%
    },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const INSTRUMENT = 'BTC-USD';

// Generate random order
function generateOrder() {
    const side = Math.random() < 0.5 ? 'buy' : 'sell';
    const type = Math.random() < 0.8 ? 'limit' : 'market'; // 80% limit, 20% market
    
    const order = {
        client_id: `k6-client-${Math.floor(Math.random() * 1000)}`,
        instrument: INSTRUMENT,
        side: side,
        type: type,
        quantity: (Math.random() * 9.999 + 0.001).toFixed(8),
        idempotency_key: `k6-${Date.now()}-${Math.random()}`,
    };
    
    if (type === 'limit') {
        order.price = (Math.random() * 500 + 70000).toFixed(2);
    }
    
    return order;
}

// Main test function
export default function() {
    const order = generateOrder();
    
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };
    
    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/orders`, JSON.stringify(order), params);
    const latency = Date.now() - startTime;
    
    const success = check(response, {
        'status is 201': (r) => r.status === 201,
        'response has order_id': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.order_id !== undefined;
            } catch {
                return false;
            }
    });
    
    orderSuccessRate.add(success);
    orderLatency.add(latency);
    
    // Small random sleep to simulate user think time
    sleep(Math.random() * 0.5);
}

// Setup function (runs once)
export function setup() {
    console.log(`Starting load test against ${BASE_URL}`);
    return { baseUrl: BASE_URL };
}

// Teardown function (runs once)
export function teardown(data) {
    console.log('Load test completed');
}

