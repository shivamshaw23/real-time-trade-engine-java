package com.tradeengine.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of the orderbook state for thread-safe reads
 */
public class OrderBookSnapshot {
    
    private final String instrument;
    private final Instant snapshotTime;
    private final List<PriceLevelSnapshot> bids;
    private final List<PriceLevelSnapshot> asks;
    
    public OrderBookSnapshot(String instrument, Instant snapshotTime,
                            List<PriceLevelSnapshot> bids, List<PriceLevelSnapshot> asks) {
        this.instrument = instrument;
        this.snapshotTime = snapshotTime;
        this.bids = new ArrayList<>(bids);
        this.asks = new ArrayList<>(asks);
    }
    
    public String getInstrument() {
        return instrument;
    }
    
    public Instant getSnapshotTime() {
        return snapshotTime;
    }
    
    public List<PriceLevelSnapshot> getBids() {
        return new ArrayList<>(bids);
    }
    
    public List<PriceLevelSnapshot> getAsks() {
        return new ArrayList<>(asks);
    }
    
    /**
     * Immutable snapshot of a price level
     */
    public static class PriceLevelSnapshot {
        private final BigDecimal price;
        private final BigDecimal totalQuantity;
        
        public PriceLevelSnapshot(BigDecimal price, BigDecimal totalQuantity) {
            this.price = price;
            this.totalQuantity = totalQuantity;
        }
        
        public BigDecimal getPrice() {
            return price;
        }
        
        public BigDecimal getTotalQuantity() {
            return totalQuantity;
        }
    }
}

