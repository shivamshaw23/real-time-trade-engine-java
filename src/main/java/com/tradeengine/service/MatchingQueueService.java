package com.tradeengine.service;

import com.tradeengine.entity.Order;
import com.tradeengine.matching.MatchingEngine;
import com.tradeengine.matching.OrderCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for enqueueing orders to the matching engine
 */
@Service
public class MatchingQueueService {
    
    private final MatchingEngine matchingEngine;
    
    @Autowired
    public MatchingQueueService(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }
    
    /**
     * Enqueue an order to the matching engine queue (non-blocking)
     * 
     * @param order The order to enqueue
     */
    public void enqueueOrder(Order order) {
        OrderCommand command = OrderCommand.placeOrder(order);
        matchingEngine.enqueueCommand(command);
    }
    
    /**
     * Enqueue a cancel command
     * 
     * @param orderId The order ID to cancel
     */
    public void enqueueCancel(java.util.UUID orderId) {
        OrderCommand command = OrderCommand.cancelOrder(orderId);
        matchingEngine.enqueueCommand(command);
    }
}

