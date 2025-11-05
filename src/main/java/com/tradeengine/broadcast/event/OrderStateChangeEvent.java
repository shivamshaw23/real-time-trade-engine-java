package com.tradeengine.broadcast.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order state change event - broadcasted when order status changes
 */
public class OrderStateChangeEvent {
    
    @JsonProperty("event_type")
    private String eventType = "order_state_change";
    
    @JsonProperty("order_id")
    private UUID orderId;
    
    @JsonProperty("client_id")
    private String clientId;
    
    private String instrument;
    
    private String side;
    
    private String type;
    
    private BigDecimal price;
    
    private BigDecimal quantity;
    
    @JsonProperty("filled_quantity")
    private BigDecimal filledQuantity;
    
    private String status;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    public OrderStateChangeEvent() {
    }
    
    public String getEventType() {
        return eventType;
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
    
    public String getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}

