package com.tradeengine.matching;

import com.tradeengine.entity.Order;
import java.util.UUID;

/**
 * Command for the matching engine queue
 */
public class OrderCommand {
    
    public enum Type {
        PLACE,  // Place a new order
        CANCEL  // Cancel an existing order
    }
    
    private final Type type;
    private final Order order;  // null for CANCEL commands
    private final UUID orderId; // required for CANCEL commands
    
    private OrderCommand(Type type, Order order, UUID orderId) {
        this.type = type;
        this.order = order;
        this.orderId = orderId;
    }
    
    public static OrderCommand placeOrder(Order order) {
        return new OrderCommand(Type.PLACE, order, null);
    }
    
    public static OrderCommand cancelOrder(UUID orderId) {
        return new OrderCommand(Type.CANCEL, null, orderId);
    }
    
    public Type getType() {
        return type;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public UUID getOrderId() {
        return orderId;
    }
}

