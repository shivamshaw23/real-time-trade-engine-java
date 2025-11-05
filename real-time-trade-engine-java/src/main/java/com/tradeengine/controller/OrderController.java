package com.tradeengine.controller;

import com.tradeengine.dto.ErrorResponse;
import com.tradeengine.dto.OrderResponse;
import com.tradeengine.dto.PlaceOrderRequest;
import com.tradeengine.entity.Order;
import com.tradeengine.security.RateLimitService;
import com.tradeengine.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for order operations
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    private final Counter ordersReceivedCounter;
    private final RateLimitService rateLimitService;
    
    @Autowired
    public OrderController(OrderService orderService, MeterRegistry meterRegistry,
                          RateLimitService rateLimitService) {
        this.orderService = orderService;
        this.rateLimitService = rateLimitService;
        this.ordersReceivedCounter = Counter.builder("orders_received_total")
                .description("Total number of orders received")
                .register(meterRegistry);
    }
    
    /**
     * POST /orders - Place a new order
     */
    @PostMapping
    public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest request, 
                                       BindingResult bindingResult,
                                       HttpServletRequest httpRequest) {
        // Phase 9: Rate limiting
        String clientId = request.getClientId() != null ? request.getClientId() : 
            httpRequest.getRemoteAddr(); // Fallback to IP if no client_id
        if (!rateLimitService.isAllowed(clientId)) {
            logger.warn("Rate limit exceeded for client: {}", clientId);
            ErrorResponse errorResponse = new ErrorResponse(
                "Rate limit exceeded. Please try again later.", 
                "RATE_LIMIT_EXCEEDED"
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }
        
        // Validate input
        if (bindingResult.hasErrors()) {
            return buildValidationErrorResponse(bindingResult);
        }
        
        // Additional validation (Phase 9: Security & Validation)
        List<String> validationErrors = validateOrderRequest(request);
        if (!validationErrors.isEmpty()) {
            logger.warn("Validation failed for client {}: {}", clientId, validationErrors);
            ErrorResponse errorResponse = new ErrorResponse("Validation failed", "VALIDATION_ERROR");
            errorResponse.setErrors(validationErrors);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        try {
            // Increment metric
            ordersReceivedCounter.increment();
            
            // Record start time for latency measurement
            long startTime = System.currentTimeMillis();
            
            // Place order (includes idempotency check and persistence)
            Order order = orderService.placeOrder(request);
            
            // Log structured event (Phase 8: Observability)
            logger.info("Order received: order_id={} client_id={} instrument={} side={} type={} quantity={} price={}", 
                order.getOrderId(), order.getClientId(), order.getInstrument(), 
                order.getSide(), order.getType(), order.getQuantity(), order.getPrice());
            
            // Convert to response DTO
            OrderResponse response = toOrderResponse(order);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
            // DB outage - return 503 Service Unavailable
            logger.error("Database error placing order: client_id={} error={}", clientId, e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Service temporarily unavailable. Please retry later.", 
                "DATABASE_ERROR"
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error placing order: client_id={} error={}", clientId, e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to place order: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * POST /orders/{order_id}/cancel - Cancel an order
     */
    @PostMapping("/{order_id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable("order_id") UUID orderId) {
        try {
            Optional<Order> orderOpt = orderService.cancelOrder(orderId);
            
            if (orderOpt.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "Order not found", 
                    "ORDER_NOT_FOUND"
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            OrderResponse response = toOrderResponse(orderOpt.get());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error cancelling order", e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to cancel order: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /orders/{order_id} - Get order state
     */
    @GetMapping("/{order_id}")
    public ResponseEntity<?> getOrder(@PathVariable("order_id") UUID orderId) {
        try {
            Optional<Order> orderOpt = orderService.getOrder(orderId);
            
            if (orderOpt.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "Order not found", 
                    "ORDER_NOT_FOUND"
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            OrderResponse response = toOrderResponse(orderOpt.get());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting order", e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to get order: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Validate order request with additional business rules (Phase 9: Security & Validation)
     */
    private List<String> validateOrderRequest(PlaceOrderRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Validate quantity is positive
        if (request.getQuantity() != null) {
            if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("quantity must be positive");
            }
            
            // Validate precision: quantity.scale() <= 8
            int scale = request.getQuantity().scale();
            if (scale > 8) {
                errors.add("quantity precision exceeds 8 decimal places (max: 8)");
            }
        }
        
        // Validate price for limit orders
        if ("limit".equals(request.getType())) {
            if (request.getPrice() == null) {
                errors.add("price is required for limit orders");
            } else {
                // Validate price > 0
                if (request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("price must be positive for limit orders");
                }
                
                // Validate precision: price.scale() <= 8
                int scale = request.getPrice().scale();
                if (scale > 8) {
                    errors.add("price precision exceeds 8 decimal places (max: 8)");
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Build validation error response from binding result
     */
    private ResponseEntity<ErrorResponse> buildValidationErrorResponse(BindingResult bindingResult) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.add(error.getField() + ": " + error.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse("Validation failed", "VALIDATION_ERROR");
        errorResponse.setErrors(errors);
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Convert Order entity to OrderResponse DTO
     */
    private OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setClientId(order.getClientId());
        response.setInstrument(order.getInstrument());
        response.setSide(order.getSide());
        response.setType(order.getType());
        response.setPrice(order.getPrice());
        response.setQuantity(order.getQuantity());
        response.setFilledQuantity(order.getFilledQuantity());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}

