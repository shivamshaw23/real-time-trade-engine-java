package com.tradeengine.orderbook;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages orderbooks for multiple instruments.
 * Thread-safe for read operations, but write operations should be done by matching thread.
 */
public class OrderBookManager {
    
    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    
    public OrderBookManager() {
        this.orderBooks = new ConcurrentHashMap<>();
    }
    
    /**
     * Get or create an orderbook for an instrument
     */
    public OrderBook getOrCreateOrderBook(String instrument) {
        return orderBooks.computeIfAbsent(instrument, OrderBook::new);
    }
    
    /**
     * Get an orderbook for an instrument (returns null if not found)
     */
    public OrderBook getOrderBook(String instrument) {
        return orderBooks.get(instrument);
    }
    
    /**
     * Remove an orderbook (for cleanup if needed)
     */
    public void removeOrderBook(String instrument) {
        orderBooks.remove(instrument);
    }
}

