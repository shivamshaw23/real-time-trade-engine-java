package com.tradeengine.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a trade execution
 */
@Entity
@Table(name = "trades")
public class Trade {
    
    @Id
    @Column(name = "trade_id")
    private UUID tradeId;
    
    @Column(name = "buy_order_id", nullable = false)
    private UUID buyOrderId;
    
    @Column(name = "sell_order_id", nullable = false)
    private UUID sellOrderId;
    
    @Column(nullable = false)
    private String instrument;
    
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;
    
    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
    
    @PrePersist
    protected void onCreate() {
        if (tradeId == null) {
            tradeId = UUID.randomUUID();
        }
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }
    
    public Trade() {
    }
    
    public UUID getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(UUID tradeId) {
        this.tradeId = tradeId;
    }
    
    public UUID getBuyOrderId() {
        return buyOrderId;
    }
    
    public void setBuyOrderId(UUID buyOrderId) {
        this.buyOrderId = buyOrderId;
    }
    
    public UUID getSellOrderId() {
        return sellOrderId;
    }
    
    public void setSellOrderId(UUID sellOrderId) {
        this.sellOrderId = sellOrderId;
    }
    
    public String getInstrument() {
        return instrument;
    }
    
    public void setInstrument(String instrument) {
        this.instrument = instrument;
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
    
    public Instant getExecutedAt() {
        return executedAt;
    }
    
    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}

