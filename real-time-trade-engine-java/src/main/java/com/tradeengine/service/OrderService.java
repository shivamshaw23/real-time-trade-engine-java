package com.tradeengine.service;

import com.tradeengine.dto.PlaceOrderRequest;
import com.tradeengine.entity.Order;
import com.tradeengine.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final MatchingQueueService matchingQueueService;
    
    @Autowired
    public OrderService(OrderRepository orderRepository, MatchingQueueService matchingQueueService) {
        this.orderRepository = orderRepository;
        this.matchingQueueService = matchingQueueService;
    }
    
    /**
     * Place a new order with idempotency check
     * 
     * @param request The order request
     * @return The created or existing order
     */
    @Transactional
    public Order placeOrder(PlaceOrderRequest request) {
        // Check idempotency - duplicate submission returns same order without re-execution
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingOrder.isPresent()) {
                logger.info("Idempotent request detected for key: {} - returning existing order {}", 
                    request.getIdempotencyKey(), existingOrder.get().getOrderId());
                // IMPORTANT: Do NOT enqueue again - order already processed
                return existingOrder.get();
            }
        }
        
        // Validate and create order
        Order order = new Order();
        
        // Use provided order_id or generate new one
        if (request.getOrderId() != null) {
            order.setOrderId(request.getOrderId());
        }
        
        order.setClientId(request.getClientId());
        order.setInstrument(request.getInstrument());
        order.setSide(request.getSide());
        order.setType(request.getType());
        order.setPrice(request.getPrice());
        order.setQuantity(request.getQuantity());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setIdempotencyKey(request.getIdempotencyKey());
        
        // Determine initial status
        String status = determineInitialStatus(request);
        order.setStatus(status);
        
        // Persist order
        Order savedOrder = orderRepository.save(order);
        logger.info("Order created: {}", savedOrder.getOrderId());
        
        // Enqueue to matching engine if status is open
        if ("open".equals(status)) {
            matchingQueueService.enqueueOrder(savedOrder);
        }
        
        return savedOrder;
    }
    
    /**
     * Cancel an order
     * Enqueues cancel command to matching engine for processing
     * 
     * @param orderId The order ID to cancel
     * @return The order (may not be cancelled yet if it's in the queue)
     */
    public Optional<Order> cancelOrder(UUID orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Order order = orderOpt.get();
        
        // Only cancel open or partially filled orders
        if (!"open".equals(order.getStatus()) && !"partially_filled".equals(order.getStatus())) {
            logger.warn("Cannot cancel order {} with status {}", orderId, order.getStatus());
            return Optional.of(order); // Return existing order but don't change status
        }
        
        // Enqueue cancel command to matching engine
        matchingQueueService.enqueueCancel(orderId);
        logger.info("Cancel command enqueued for order: {}", orderId);
        
        // Return the order (status will be updated by matching engine)
        return Optional.of(order);
    }
    
    /**
     * Get order by ID
     */
    public Optional<Order> getOrder(UUID orderId) {
        return orderRepository.findById(orderId);
    }
    
    /**
     * Determine initial status based on validation
     * For now, always returns "open" - validation happens in controller
     */
    private String determineInitialStatus(PlaceOrderRequest request) {
        // Additional business logic validation can go here
        // For now, if we got here, the order is valid
        return "open";
    }
}

