#!/usr/bin/env node

/**
 * Order generator for load testing
 * Generates realistic limit orders across a price band and market order bursts
 */

const http = require('http');
const https = require('https');

// Configuration
const CONFIG = {
    baseUrl: process.env.API_URL || 'http://localhost:8080',
    instrument: 'BTC-USD',
    priceMin: 70000,
    priceMax: 70500,
    numLimitOrders: 100000,
    marketOrderBurstSize: 100,
    numMarketBursts: 10,
    clientIdPrefix: 'load-test-client',
    delayBetweenRequests: 0, // milliseconds
};

// Price levels for limit orders
const PRICE_STEP = 1;
const PRICE_LEVELS = [];
for (let price = CONFIG.priceMin; price <= CONFIG.priceMax; price += PRICE_STEP) {
    PRICE_LEVELS.push(price);
}

// Generate random quantity between 0.001 and 10
function randomQuantity() {
    return (Math.random() * 9.999 + 0.001).toFixed(8);
}

// Generate random price from price band
function randomPrice() {
    const index = Math.floor(Math.random() * PRICE_LEVELS.length);
    return PRICE_LEVELS[index];
}

// Random side (buy or sell)
function randomSide() {
    return Math.random() < 0.5 ? 'buy' : 'sell';
}

// Generate client ID
function clientId(index) {
    return `${CONFIG.clientIdPrefix}-${index % 1000}`;
}

// Make HTTP POST request
function postOrder(order) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${CONFIG.baseUrl}/orders`);
        const options = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
        };

        const req = (url.protocol === 'https:' ? https : http).request(url, options, (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve({ status: res.statusCode, data: JSON.parse(data) });
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                }
            });
        });

        req.on('error', reject);
        req.write(JSON.stringify(order));
        req.end();
    });
}

// Generate limit order
function generateLimitOrder(index) {
    return {
        client_id: clientId(index),
        instrument: CONFIG.instrument,
        side: randomSide(),
        type: 'limit',
        price: randomPrice(),
        quantity: randomQuantity(),
        idempotency_key: `load-test-${Date.now()}-${index}`,
    };
}

// Generate market order
function generateMarketOrder(index) {
    return {
        client_id: clientId(index),
        instrument: CONFIG.instrument,
        side: randomSide(),
        type: 'market',
        quantity: randomQuantity(),
        idempotency_key: `load-test-market-${Date.now()}-${index}`,
    };
}

// Generate limit orders
async function generateLimitOrders() {
    console.log(`Generating ${CONFIG.numLimitOrders} limit orders...`);
    const startTime = Date.now();
    let successCount = 0;
    let errorCount = 0;

    for (let i = 0; i < CONFIG.numLimitOrders; i++) {
        const order = generateLimitOrder(i);
        
        try {
            await postOrder(order);
            successCount++;
            
            if ((i + 1) % 1000 === 0) {
                console.log(`Progress: ${i + 1}/${CONFIG.numLimitOrders} orders posted`);
            }
            
            if (CONFIG.delayBetweenRequests > 0) {
                await new Promise(resolve => setTimeout(resolve, CONFIG.delayBetweenRequests));
            }
        } catch (error) {
            errorCount++;
            console.error(`Error posting order ${i}:`, error.message);
        }
    }

    const duration = (Date.now() - startTime) / 1000;
    console.log(`\nLimit orders completed:`);
    console.log(`  Success: ${successCount}`);
    console.log(`  Errors: ${errorCount}`);
    console.log(`  Duration: ${duration.toFixed(2)}s`);
    console.log(`  Throughput: ${(successCount / duration).toFixed(2)} orders/sec`);
}

// Generate market order bursts
async function generateMarketBursts() {
    console.log(`\nGenerating ${CONFIG.numMarketBursts} market order bursts (${CONFIG.marketOrderBurstSize} orders each)...`);
    
    for (let burst = 0; burst < CONFIG.numMarketBursts; burst++) {
        console.log(`\nBurst ${burst + 1}/${CONFIG.numMarketBursts}`);
        const startTime = Date.now();
        const promises = [];

        for (let i = 0; i < CONFIG.marketOrderBurstSize; i++) {
            const order = generateMarketOrder(burst * CONFIG.marketOrderBurstSize + i);
            promises.push(postOrder(order).catch(err => ({ error: err.message })));
        }

        const results = await Promise.all(promises);
        const successCount = results.filter(r => !r.error).length;
        const errorCount = results.length - successCount;
        const duration = (Date.now() - startTime) / 1000;

        console.log(`  Success: ${successCount}, Errors: ${errorCount}`);
        console.log(`  Duration: ${duration.toFixed(2)}s, Throughput: ${(successCount / duration).toFixed(2)} orders/sec`);

        // Wait between bursts
        if (burst < CONFIG.numMarketBursts - 1) {
            await new Promise(resolve => setTimeout(resolve, 2000));
        }
    }
}

// Main execution
async function main() {
    console.log('=== Trade Engine Load Test Order Generator ===\n');
    console.log(`Target: ${CONFIG.baseUrl}`);
    console.log(`Instrument: ${CONFIG.instrument}`);
    console.log(`Price Range: ${CONFIG.priceMin} - ${CONFIG.priceMax}\n`);

    try {
        // Generate limit orders first
        await generateLimitOrders();

        // Wait a bit before market orders
        console.log('\nWaiting 5 seconds before market order bursts...');
        await new Promise(resolve => setTimeout(resolve, 5000));

        // Generate market order bursts
        await generateMarketBursts();

        console.log('\n=== Generation Complete ===');
    } catch (error) {
        console.error('Fatal error:', error);
        process.exit(1);
    }
}

// Run if executed directly
if (require.main === module) {
    main();
}

module.exports = { generateLimitOrder, generateMarketOrder };

