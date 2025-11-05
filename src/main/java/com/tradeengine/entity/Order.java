package com.tradeengine.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an order in the trading system
 */
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @Column(name = "order_id")
    private UUID orderId;
    
    @Column(name = "client_id", nullable = false)
    private String clientId;
    
    @Column(nullable = false)
    private String instrument;
    
    @Column(nullable = false)
    private String side; // 'buy' or 'sell'
    
    @Column(nullable = false)
    private String type; // 'limit' or 'market'
    
    @Column(precision = 18, scale = 8)
    private BigDecimal price; // nullable for market orders
    
    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "filled_quantity", nullable = false, precision = 30, scale = 8)
    private BigDecimal filledQuantity;
    
    @Column(nullable = false)
    private String status; // 'open', 'partially_filled', 'filled', 'cancelled', 'rejected'
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (orderId == null) {
            orderId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (filledQuantity == null) {
            filledQuantity = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public Order() {
    }
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getInstrument() {
        return instrument;
    }
    
    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
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
    
    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }
    
    public void setFilledQuantity(BigDecimal filledQuantity) {
        this.filledQuantity = filledQuantity;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

