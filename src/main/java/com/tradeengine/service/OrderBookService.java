package com.tradeengine.service;

import com.tradeengine.dto.OrderBookResponse;
import com.tradeengine.orderbook.OrderBook;
import com.tradeengine.orderbook.OrderBookManager;
import com.tradeengine.orderbook.OrderBookSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for orderbook operations
 */
@Service
public class OrderBookService {
    
    private final OrderBookManager orderBookManager;
    
    @Autowired
    public OrderBookService(OrderBookManager orderBookManager) {
        this.orderBookManager = orderBookManager;
    }
    
    /**
     * Get orderbook snapshot for an instrument
     * Uses thread-safe snapshot mechanism for reads
     * 
     * @param instrument The trading instrument
     * @param levels Number of price levels to return
     * @return Orderbook response with bids and asks
     */
    public OrderBookResponse getOrderBook(String instrument, int levels) {
        OrderBook orderBook = orderBookManager.getOrderBook(instrument);
        
        OrderBookResponse response = new OrderBookResponse();
        response.setInstrument(instrument);
        
        if (orderBook == null) {
            // No orderbook exists yet for this instrument
            response.setSnapshotTime(java.time.Instant.now().toString());
            response.setBids(new ArrayList<>());
            response.setAsks(new ArrayList<>());
            return response;
        }
        
        // Get thread-safe snapshot
        OrderBookSnapshot snapshot = orderBook.getSnapshot();
        
        response.setSnapshotTime(snapshot.getSnapshotTime().toString());
        
        // Get top N bids and asks
        List<OrderBookSnapshot.PriceLevelSnapshot> bidSnapshots = orderBook.getTopBids(levels);
        List<OrderBookSnapshot.PriceLevelSnapshot> askSnapshots = orderBook.getTopAsks(levels);
        
        // Convert to DTO
        List<OrderBookResponse.OrderBookLevel> bids = bidSnapshots.stream()
                .map(level -> new OrderBookResponse.OrderBookLevel(level.getPrice(), level.getTotalQuantity()))
                .collect(Collectors.toList());
        
        List<OrderBookResponse.OrderBookLevel> asks = askSnapshots.stream()
                .map(level -> new OrderBookResponse.OrderBookLevel(level.getPrice(), level.getTotalQuantity()))
                .collect(Collectors.toList());
        
        response.setBids(bids);
        response.setAsks(asks);
        
        return response;
    }
    
    /**
     * Get the OrderBookManager (for use by matching engine)
     */
    public OrderBookManager getOrderBookManager() {
        return orderBookManager;
    }
}

