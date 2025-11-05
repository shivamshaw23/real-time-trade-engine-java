package com.tradeengine.matching;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metrics for matching engine
 */
@Component
public class MatchingMetrics {
    
    private final Counter ordersMatchedCounter;
    private final Counter ordersRejectedCounter;
    private final Timer orderLatencyTimer;
    private final Gauge orderbookDepthGauge;
    
    public MatchingMetrics(MeterRegistry meterRegistry) {
        this.ordersMatchedCounter = Counter.builder("orders_matched_total")
                .description("Total number of orders matched")
                .register(meterRegistry);
        
        this.ordersRejectedCounter = Counter.builder("orders_rejected_total")
                .description("Total number of orders rejected")
                .register(meterRegistry);
        
        this.orderLatencyTimer = Timer.builder("order_latency_seconds")
                .description("Order processing latency")
                .register(meterRegistry);
        
        this.orderbookDepthGauge = Gauge.builder("current_orderbook_depth", 
                this, MatchingMetrics::getOrderbookDepth)
                .description("Current depth of the orderbook (total open orders)")
                .register(meterRegistry);
    }
    
    public void incrementOrdersMatched() {
        ordersMatchedCounter.increment();
    }
    
    public void incrementOrdersRejected() {
        ordersRejectedCounter.increment();
    }
    
    public Timer.Sample startLatencyTimer() {
        return Timer.start();
    }
    
    public void recordLatency(Timer.Sample sample) {
        sample.stop(orderLatencyTimer);
    }
    
    private double getOrderbookDepth() {
        // This will be updated by MatchingEngine
        // For now, return 0 - will be set dynamically
        return 0.0;
    }
}

