package com.tradeengine.recovery;

import com.tradeengine.entity.Order;
import com.tradeengine.orderbook.OrderBook;
import com.tradeengine.orderbook.OrderBookManager;
import com.tradeengine.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for recovering orderbook state on startup.
 * 
 * Recovery Strategy (Option B - Rebuild by Replaying Persisted Open Orders):
 * 
 * On startup:
 * 1. Query all orders WHERE status IN ('open', 'partially_filled') ordered by created_at
 * 2. Reconstruct in-memory orderbook by inserting these orders into the orderbook
 *    (but do NOT match them - they are already in the book)
 * 3. This ensures we can restart and recover state from database
 * 
 * Trade-offs:
 * - Pros: Simple, robust, no complex replay logic needed
 * - Pros: All orders are persisted before enqueueing, so we have complete state
 * - Cons: Slower startup if there are many open orders (but acceptable for prototype)
 * - Cons: Requires full table scan on startup, but index on status helps
 * 
 * Alternative approach (not implemented):
 * - Option A: Periodic snapshots + replay events since snapshot
 *   - Pros: Faster startup with frequent snapshots
 *   - Cons: Complex replay logic, need to ensure correct replay window
 */
@Service
public class RecoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecoveryService.class);
    
    private final OrderRepository orderRepository;
    private final OrderBookManager orderBookManager;
    
    @Autowired
    public RecoveryService(OrderRepository orderRepository, OrderBookManager orderBookManager) {
        this.orderRepository = orderRepository;
        this.orderBookManager = orderBookManager;
    }
    
    /**
     * Recover orderbook state from database.
     * This should be called before the matching engine starts.
     * 
     * @return Number of orders recovered
     */
    public int recoverOrderbookState() {
        logger.info("Starting orderbook recovery...");
        
        // Query all open and partially filled orders
        List<Order> openOrders = orderRepository.findOpenAndPartiallyFilledOrders();
        logger.info("Found {} open/partially filled orders to recover", openOrders.size());
        
        int recoveredCount = 0;
        
        for (Order order : openOrders) {
            try {
                recoverOrder(order);
                recoveredCount++;
            } catch (Exception e) {
                logger.error("Failed to recover order {}: {}", order.getOrderId(), e.getMessage(), e);
                // Continue with other orders even if one fails
            }
        }
        
        logger.info("Orderbook recovery completed. Recovered {} orders", recoveredCount);
        return recoveredCount;
    }
    
    /**
     * Recover a single order into the orderbook
     */
    private void recoverOrder(Order order) {
        // Skip market orders that are partially filled (they should have been fully filled)
        // Market orders should only be in the book if they're completely unfilled
        if ("market".equals(order.getType()) && order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            logger.warn("Skipping partially filled market order {} - should not exist", order.getOrderId());
            return;
        }
        
        // Get or create orderbook for this instrument
        OrderBook orderBook = orderBookManager.getOrCreateOrderBook(order.getInstrument());
        
        // Calculate remaining quantity
        BigDecimal remainingQty = order.getQuantity().subtract(order.getFilledQuantity());
        
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Order {} has no remaining quantity, skipping", order.getOrderId());
            return;
        }
        
        // Reconstruct orderbook entry
        if ("limit".equals(order.getType())) {
            // Limit order: add to price level
            if (order.getPrice() == null) {
                logger.warn("Limit order {} has null price, skipping", order.getOrderId());
                return;
            }
            
            orderBook.addLimitOrder(
                order.getOrderId(),
                order.getPrice(),
                remainingQty,
                order.getCreatedAt(),
                order.getClientId(),
                order.getSide()
            );
            
            logger.debug("Recovered limit order {}: {} {} @ {} (remaining: {})", 
                order.getOrderId(), order.getSide(), order.getInstrument(), 
                order.getPrice(), remainingQty);
                
        } else if ("market".equals(order.getType())) {
            // Market order: add to orderbook (but it won't be in a price level)
            // Market orders should be matched immediately, but if one exists,
            // it means it was in the queue when the system crashed
            orderBook.addMarketOrder(
                order.getOrderId(),
                remainingQty,
                order.getCreatedAt(),
                order.getClientId(),
                order.getSide()
            );
            
            logger.debug("Recovered market order {}: {} {} (remaining: {})", 
                order.getOrderId(), order.getSide(), order.getInstrument(), remainingQty);
        }
    }
}

