package com.tradeengine.integration;

import com.tradeengine.dto.PlaceOrderRequest;
import com.tradeengine.entity.Order;
import com.tradeengine.entity.Trade;
import com.tradeengine.repository.OrderRepository;
import com.tradeengine.repository.TradeRepository;
import com.tradeengine.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using Testcontainers for PostgreSQL
 * Tests end-to-end: POST order -> trade produced -> DB rows updated
 */
@SpringBootTest
@Testcontainers
@Transactional
class OrderMatchingIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private TradeRepository tradeRepository;
    
    @BeforeEach
    void setUp() {
        // Wait for matching engine to be ready
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    void testOrderPlacementAndMatching() {
        // Create a resting sell order
        PlaceOrderRequest sellRequest = new PlaceOrderRequest();
        sellRequest.setClientId("client-sell");
        sellRequest.setInstrument("BTC-USD");
        sellRequest.setSide("sell");
        sellRequest.setType("limit");
        sellRequest.setPrice(new BigDecimal("70000"));
        sellRequest.setQuantity(new BigDecimal("1.0"));
        
        Order restingOrder = orderService.placeOrder(sellRequest);
        assertNotNull(restingOrder);
        assertEquals("open", restingOrder.getStatus());
        
        // Wait for order to be added to book
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create incoming buy order that should match
        PlaceOrderRequest buyRequest = new PlaceOrderRequest();
        buyRequest.setClientId("client-buy");
        buyRequest.setInstrument("BTC-USD");
        buyRequest.setSide("buy");
        buyRequest.setType("limit");
        buyRequest.setPrice(new BigDecimal("70000"));
        buyRequest.setQuantity(new BigDecimal("1.0"));
        
        Order incomingOrder = orderService.placeOrder(buyRequest);
        assertNotNull(incomingOrder);
        
        // Wait for matching to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: Check database for trades and updated orders
        List<Trade> trades = tradeRepository.findAll();
        assertFalse(trades.isEmpty(), "Trade should be created");
        
        Optional<Order> updatedResting = orderRepository.findById(restingOrder.getOrderId());
        assertTrue(updatedResting.isPresent());
        assertEquals("filled", updatedResting.get().getStatus());
        
        Optional<Order> updatedIncoming = orderRepository.findById(incomingOrder.getOrderId());
        assertTrue(updatedIncoming.isPresent());
        assertEquals("filled", updatedIncoming.get().getStatus());
    }
    
    @Test
    void testIdempotency() {
        String idempotencyKey = UUID.randomUUID().toString();
        
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setClientId("client-1");
        request.setInstrument("BTC-USD");
        request.setSide("buy");
        request.setType("limit");
        request.setPrice(new BigDecimal("70000"));
        request.setQuantity(new BigDecimal("1.0"));
        request.setIdempotencyKey(idempotencyKey);
        
        Order firstOrder = orderService.placeOrder(request);
        UUID firstOrderId = firstOrder.getOrderId();
        
        // Place same order again with same idempotency key
        Order secondOrder = orderService.placeOrder(request);
        UUID secondOrderId = secondOrder.getOrderId();
        
        // Should return the same order
        assertEquals(firstOrderId, secondOrderId);
        
        // Verify only one order in database
        long count = orderRepository.findAll().stream()
            .filter(o -> idempotencyKey.equals(o.getIdempotencyKey()))
            .count();
        assertEquals(1, count, "Should have only one order with this idempotency key");
    }
    
    @Test
    void testOrderCancellation() {
        // Place an order
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setClientId("client-1");
        request.setInstrument("BTC-USD");
        request.setSide("buy");
        request.setType("limit");
        request.setPrice(new BigDecimal("70000"));
        request.setQuantity(new BigDecimal("1.0"));
        
        Order order = orderService.placeOrder(request);
        assertNotNull(order);
        
        // Wait for order to be added to book
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Cancel the order
        Optional<Order> cancelled = orderService.cancelOrder(order.getOrderId());
        assertTrue(cancelled.isPresent());
        
        // Wait for cancellation to process
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify order is cancelled in database
        Optional<Order> updated = orderRepository.findById(order.getOrderId());
        assertTrue(updated.isPresent());
        assertEquals("cancelled", updated.get().getStatus());
    }
}

