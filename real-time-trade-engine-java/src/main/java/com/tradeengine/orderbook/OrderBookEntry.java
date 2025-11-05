package com.tradeengine.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an order entry in the orderbook
 */
public class OrderBookEntry {
    
    private final UUID orderId;
    private BigDecimal price; // null for market orders until matched
    private BigDecimal remainingQty;
    private final Instant createdAt;
    private final String clientId;
    private PriceLevel priceLevel; // pointer to containing PriceLevel (if limit order)
    
    public OrderBookEntry(UUID orderId, BigDecimal price, BigDecimal remainingQty, 
                         Instant createdAt, String clientId) {
        this.orderId = orderId;
        this.price = price;
        this.remainingQty = remainingQty;
        this.createdAt = createdAt;
        this.clientId = clientId;
        this.priceLevel = null;
    }
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public BigDecimal getRemainingQty() {
        return remainingQty;
    }
    
    public void setRemainingQty(BigDecimal remainingQty) {
        this.remainingQty = remainingQty;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public PriceLevel getPriceLevel() {
        return priceLevel;
    }
    
    public void setPriceLevel(PriceLevel priceLevel) {
        this.priceLevel = priceLevel;
    }
    
    /**
     * Check if this is a limit order (has a price)
     */
    public boolean isLimitOrder() {
        return price != null;
    }
}

