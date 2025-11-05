package com.tradeengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for placing a new order
 */
public class PlaceOrderRequest {
    
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
    
    @JsonProperty("order_id")
    private UUID orderId;
    
    @NotBlank(message = "client_id is required")
    @JsonProperty("client_id")
    private String clientId;
    
    @NotBlank(message = "instrument is required")
    private String instrument;
    
    @NotBlank(message = "side is required")
    @Pattern(regexp = "^(buy|sell)$", message = "side must be 'buy' or 'sell'")
    private String side;
    
    @NotBlank(message = "type is required")
    @Pattern(regexp = "^(limit|market)$", message = "type must be 'limit' or 'market'")
    private String type;
    
    // Price validation is handled by isValidPriceForOrderType() - nullable for market orders
    private BigDecimal price;
    
    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be positive")
    @Digits(integer = 22, fraction = 8, message = "quantity has invalid precision")
    private BigDecimal quantity;

    public PlaceOrderRequest() {
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    @AssertTrue(message = "price is required for limit orders")
    public boolean isValidPriceForOrderType() {
        if ("limit".equals(type)) {
            return price != null;
        }
        return true; // market orders don't require price
    }
}

