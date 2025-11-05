package com.tradeengine.orderbook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a price level in the orderbook containing all orders at a specific price.
 * Orders are maintained in FIFO order (by arrival time).
 */
public class PriceLevel {
    
    private final BigDecimal price;
    private BigDecimal totalQuantity; // Sum of all remaining quantities at this price
    private final List<OrderBookEntry> orders; // FIFO queue of orders at this price
    
    public PriceLevel(BigDecimal price) {
        this.price = price;
        this.totalQuantity = BigDecimal.ZERO;
        this.orders = new ArrayList<>();
    }
    
    /**
     * Add an order to this price level
     */
    public void addOrder(OrderBookEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Cannot add null entry to price level");
        }
        orders.add(entry);
        BigDecimal entryQty = entry.getRemainingQty();
        if (entryQty != null) {
            totalQuantity = totalQuantity.add(entryQty);
        }
        entry.setPriceLevel(this);
    }
    
    /**
     * Remove an order from this price level
     */
    public boolean removeOrder(OrderBookEntry entry) {
        boolean removed = orders.remove(entry);
        if (removed) {
            // Subtract the order's remaining quantity from total
            BigDecimal entryQty = entry.getRemainingQty();
            if (entryQty != null) {
                totalQuantity = totalQuantity.subtract(entryQty);
            }
            entry.setPriceLevel(null);
        }
        return removed;
    }
    
    /**
     * Update the quantity of an order in this price level
     */
    public void updateOrderQuantity(OrderBookEntry entry, BigDecimal oldQuantity, BigDecimal newQuantity) {
        totalQuantity = totalQuantity.subtract(oldQuantity).add(newQuantity);
    }
    
    /**
     * Get the first order (oldest) at this price level
     */
    public OrderBookEntry getFirstOrder() {
        return orders.isEmpty() ? null : orders.get(0);
    }
    
    /**
     * Check if this price level is empty
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }
    
    /**
     * Get all orders at this price level (defensive copy for iteration)
     */
    public List<OrderBookEntry> getOrders() {
        return new ArrayList<>(orders);
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }
    
    public int getOrderCount() {
        return orders.size();
    }
}

