package com.tradeengine.broadcast.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Orderbook delta event - changes to orderbook
 */
public class OrderbookDeltaEvent {
    
    @JsonProperty("event_type")
    private String eventType = "orderbook_delta";
    
    private String instrument;
    
    private List<PriceLevelDelta> bids;
    
    private List<PriceLevelDelta> asks;
    
    @JsonProperty("snapshot_time")
    private String snapshotTime;
    
    public OrderbookDeltaEvent() {
    }
    
    public OrderbookDeltaEvent(String instrument, List<PriceLevelDelta> bids, 
                              List<PriceLevelDelta> asks, String snapshotTime) {
        this.instrument = instrument;
        this.bids = bids;
        this.asks = asks;
        this.snapshotTime = snapshotTime;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getInstrument() {
        return instrument;
    }
    
    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }
    
    public List<PriceLevelDelta> getBids() {
        return bids;
    }
    
    public void setBids(List<PriceLevelDelta> bids) {
        this.bids = bids;
    }
    
    public List<PriceLevelDelta> getAsks() {
        return asks;
    }
    
    public void setAsks(List<PriceLevelDelta> asks) {
        this.asks = asks;
    }
    
    public String getSnapshotTime() {
        return snapshotTime;
    }
    
    public void setSnapshotTime(String snapshotTime) {
        this.snapshotTime = snapshotTime;
    }
    
    /**
     * Represents a price level change
     */
    public static class PriceLevelDelta {
        private BigDecimal price;
        private BigDecimal quantity;
        
        public PriceLevelDelta() {
        }
        
        public PriceLevelDelta(BigDecimal price, BigDecimal quantity) {
            this.price = price;
            this.quantity = quantity;
        }
        
        public BigDecimal getPrice() {
            return price;
        }
        
        public void setPrice(BigDecimal price) {
            this.price = price;
        }
        
        public BigDecimal getQuantity() {
            return quantity;
        }
        
        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
    }
}

