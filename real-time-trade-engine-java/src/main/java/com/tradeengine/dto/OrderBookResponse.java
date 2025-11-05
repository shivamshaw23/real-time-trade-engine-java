package com.tradeengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for orderbook data
 */
public class OrderBookResponse {
    
    private String instrument;
    
    @JsonProperty("snapshot_time")
    private String snapshotTime;
    
    private List<OrderBookLevel> bids;
    
    private List<OrderBookLevel> asks;

    public OrderBookResponse() {
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(String snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    public List<OrderBookLevel> getBids() {
        return bids;
    }

    public void setBids(List<OrderBookLevel> bids) {
        this.bids = bids;
    }

    public List<OrderBookLevel> getAsks() {
        return asks;
    }

    public void setAsks(List<OrderBookLevel> asks) {
        this.asks = asks;
    }

    /**
     * Represents a single price level in the orderbook
     */
    public static class OrderBookLevel {
        
        private BigDecimal price;
        
        private BigDecimal quantity;

        public OrderBookLevel() {
        }

        public OrderBookLevel(BigDecimal price, BigDecimal quantity) {
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

