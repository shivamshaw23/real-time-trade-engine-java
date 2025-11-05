package com.tradeengine.broadcast.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Trade event - broadcasted when a trade is executed
 */
public class TradeEvent {
    
    @JsonProperty("event_type")
    private String eventType = "trade";
    
    @JsonProperty("trade_id")
    private UUID tradeId;
    
    @JsonProperty("buy_order_id")
    private UUID buyOrderId;
    
    @JsonProperty("sell_order_id")
    private UUID sellOrderId;
    
    private String instrument;
    
    private BigDecimal price;
    
    private BigDecimal quantity;
    
    @JsonProperty("executed_at")
    private String executedAt;
    
    public TradeEvent() {
    }
    
    public String getEventType() {
        return eventType;
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
    
    public String getExecutedAt() {
        return executedAt;
    }
    
    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }
}

