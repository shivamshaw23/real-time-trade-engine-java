package com.tradeengine.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory orderbook for a single instrument.
 * 
 * Thread-safety:
 * - All write operations (add, cancel, match) must be executed by a single matching thread
 * - Read operations use snapshots to avoid locking contention
 */
public class OrderBook {
    
    private final String instrument;
    
    // Bids: sorted descending (highest price first)
    private final NavigableMap<BigDecimal, PriceLevel> bids;
    
    // Asks: sorted ascending (lowest price first)
    private final NavigableMap<BigDecimal, PriceLevel> asks;
    
    // Quick lookup of order by ID for cancels/queries
    private final ConcurrentHashMap<UUID, OrderBookEntry> orderMap;
    
    // Volatile snapshot for thread-safe reads
    private volatile OrderBookSnapshot snapshot;
    
    public OrderBook(String instrument) {
        this.instrument = instrument;
        // Bids: descending order (highest first)
        this.bids = new TreeMap<>(Collections.reverseOrder());
        // Asks: ascending order (lowest first)
        this.asks = new TreeMap<>();
        this.orderMap = new ConcurrentHashMap<>();
        this.snapshot = createSnapshot();
    }
    
    public String getInstrument() {
        return instrument;
    }
    
    /**
     * Add a limit order to the orderbook.
     * This method should only be called by the matching thread.
     * 
     * @param orderId Order ID
     * @param price Price (must not be null)
     * @param quantity Quantity
     * @param createdAt Creation timestamp
     * @param clientId Client ID
     * @param side "buy" or "sell"
     * @return The created OrderBookEntry
     */
    public OrderBookEntry addLimitOrder(UUID orderId, BigDecimal price, BigDecimal quantity,
                                       Instant createdAt, String clientId, String side) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null for limit orders");
        }
        
        OrderBookEntry entry = new OrderBookEntry(orderId, price, quantity, createdAt, clientId);
        orderMap.put(orderId, entry);
        
        NavigableMap<BigDecimal, PriceLevel> sideMap = "buy".equals(side) ? bids : asks;
        
        PriceLevel level = sideMap.computeIfAbsent(price, PriceLevel::new);
        level.addOrder(entry);
        
        // Update snapshot
        updateSnapshot();
        
        return entry;
    }
    
    /**
     * Add a market order to the orderbook.
     * Market orders don't have a price initially and are stored separately.
     * This method should only be called by the matching thread.
     * 
     * @param orderId Order ID
     * @param quantity Quantity
     * @param createdAt Creation timestamp
     * @param clientId Client ID
     * @param side "buy" or "sell"
     * @return The created OrderBookEntry
     */
    public OrderBookEntry addMarketOrder(UUID orderId, BigDecimal quantity,
                                        Instant createdAt, String clientId, String side) {
        OrderBookEntry entry = new OrderBookEntry(orderId, null, quantity, createdAt, clientId);
        orderMap.put(orderId, entry);
        
        // Market orders are stored in the orderMap but not in price levels
        // They will be matched immediately in the matching engine
        
        // Update snapshot
        updateSnapshot();
        
        return entry;
    }
    
    /**
     * Cancel an order from the orderbook.
     * This method should only be called by the matching thread.
     * 
     * @param orderId Order ID to cancel
     * @return true if order was found and removed, false otherwise
     */
    public boolean cancelOrder(UUID orderId) {
        OrderBookEntry entry = orderMap.remove(orderId);
        if (entry == null) {
            return false;
        }
        
        // If it's a limit order, remove from price level
        if (entry.isLimitOrder() && entry.getPriceLevel() != null) {
            PriceLevel level = entry.getPriceLevel();
            BigDecimal price = entry.getPrice();
            level.removeOrder(entry);
            
            // Remove empty price levels from the appropriate side
            // Check both maps to find which one contains this price level
            if (bids.containsKey(price)) {
                PriceLevel bidLevel = bids.get(price);
                if (bidLevel != null && bidLevel.isEmpty()) {
                    bids.remove(price);
                }
            } else if (asks.containsKey(price)) {
                PriceLevel askLevel = asks.get(price);
                if (askLevel != null && askLevel.isEmpty()) {
                    asks.remove(price);
                }
            }
        }
        
        // Update snapshot
        updateSnapshot();
        
        return true;
    }
    
    /**
     * Update the remaining quantity of an order.
     * This method should only be called by the matching thread.
     * 
     * @param orderId Order ID
     * @param newQuantity New remaining quantity
     */
    public void updateOrderQuantity(UUID orderId, BigDecimal newQuantity) {
        OrderBookEntry entry = orderMap.get(orderId);
        if (entry == null) {
            return;
        }
        
        BigDecimal oldQuantity = entry.getRemainingQty();
        entry.setRemainingQty(newQuantity);
        
        // Update price level total if it's a limit order
        if (entry.isLimitOrder() && entry.getPriceLevel() != null) {
            entry.getPriceLevel().updateOrderQuantity(entry, oldQuantity, newQuantity);
        }
        
        // Update snapshot
        updateSnapshot();
    }
    
    /**
     * Get an order entry by ID (thread-safe read)
     */
    public OrderBookEntry getOrder(UUID orderId) {
        return orderMap.get(orderId);
    }
    
    /**
     * Get the best bid price (highest buy price)
     */
    public BigDecimal getBestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }
    
    /**
     * Get the best ask price (lowest sell price)
     */
    public BigDecimal getBestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }
    
    /**
     * Get the best bid price level
     */
    public PriceLevel getBestBidLevel() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue();
    }
    
    /**
     * Get the best ask price level
     */
    public PriceLevel getBestAskLevel() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue();
    }
    
    /**
     * Get top N bids (thread-safe via snapshot)
     */
    public List<OrderBookSnapshot.PriceLevelSnapshot> getTopBids(int n) {
        OrderBookSnapshot snap = snapshot; // Read volatile once
        List<OrderBookSnapshot.PriceLevelSnapshot> bidsList = snap.getBids();
        return bidsList.size() <= n ? bidsList : bidsList.subList(0, n);
    }
    
    /**
     * Get top N asks (thread-safe via snapshot)
     */
    public List<OrderBookSnapshot.PriceLevelSnapshot> getTopAsks(int n) {
        OrderBookSnapshot snap = snapshot; // Read volatile once
        List<OrderBookSnapshot.PriceLevelSnapshot> asksList = snap.getAsks();
        return asksList.size() <= n ? asksList : asksList.subList(0, n);
    }
    
    /**
     * Get a snapshot of the orderbook (thread-safe read)
     */
    public OrderBookSnapshot getSnapshot() {
        return snapshot; // Volatile read
    }
    
    /**
     * Create a new snapshot (should only be called by matching thread)
     */
    private OrderBookSnapshot createSnapshot() {
        List<OrderBookSnapshot.PriceLevelSnapshot> bidSnapshots = new ArrayList<>();
        for (PriceLevel level : bids.values()) {
            bidSnapshots.add(new OrderBookSnapshot.PriceLevelSnapshot(
                level.getPrice(), level.getTotalQuantity()));
        }
        
        List<OrderBookSnapshot.PriceLevelSnapshot> askSnapshots = new ArrayList<>();
        for (PriceLevel level : asks.values()) {
            askSnapshots.add(new OrderBookSnapshot.PriceLevelSnapshot(
                level.getPrice(), level.getTotalQuantity()));
        }
        
        return new OrderBookSnapshot(instrument, Instant.now(), bidSnapshots, askSnapshots);
    }
    
    /**
     * Update the snapshot (should only be called by matching thread)
     */
    private void updateSnapshot() {
        this.snapshot = createSnapshot(); // Volatile write
    }
    
    /**
     * Get all bids (for internal use by matching thread)
     */
    NavigableMap<BigDecimal, PriceLevel> getBids() {
        return bids;
    }
    
    /**
     * Get all asks (for internal use by matching thread)
     */
    NavigableMap<BigDecimal, PriceLevel> getAsks() {
        return asks;
    }
}

