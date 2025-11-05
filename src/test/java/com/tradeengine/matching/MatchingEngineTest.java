package com.tradeengine.matching;

import com.tradeengine.broadcast.BroadcastService;
import com.tradeengine.entity.Order;
import com.tradeengine.entity.Trade;
import com.tradeengine.orderbook.OrderBook;
import com.tradeengine.orderbook.OrderBookManager;
import com.tradeengine.recovery.RecoveryService;
import com.tradeengine.repository.OrderRepository;
import com.tradeengine.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MatchingEngine
 * Tests various matching scenarios: simple match, partial fill, market orders, cancel, edge cases
 */
@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {
    
    @Mock
    private OrderBookManager orderBookManager;
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private TradeRepository tradeRepository;
    
    @Mock
    private RecoveryService recoveryService;
    
    @Mock
    private BroadcastService broadcastService;
    
    @Mock
    private MatchingMetrics metrics;
    
    private MatchingEngine matchingEngine;
    private OrderBook orderBook;
    
    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine(
            orderBookManager,
            orderRepository,
            tradeRepository,
            recoveryService,
            broadcastService,
            metrics
        );
        
        orderBook = new OrderBook("BTC-USD");
        when(orderBookManager.getOrCreateOrderBook(anyString())).thenReturn(orderBook);
        when(orderBookManager.getOrderBook(anyString())).thenReturn(orderBook);
    }
    
    @Test
    void testSimpleMatch_LimitBuyMatchesLimitSell() {
        // Setup: Create a resting sell order
        Order restingSell = createLimitOrder("sell", new BigDecimal("70000"), new BigDecimal("1.0"));
        restingSell.setOrderId(UUID.randomUUID());
        restingSell.setStatus("open");
        
        orderBook.addLimitOrder(
            restingSell.getOrderId(),
            restingSell.getPrice(),
            restingSell.getQuantity(),
            restingSell.getCreatedAt(),
            restingSell.getClientId(),
            restingSell.getSide()
        );
        
        when(orderRepository.findById(restingSell.getOrderId())).thenReturn(Optional.of(restingSell));
        
        // Test: Create incoming buy order that matches
        Order incomingBuy = createLimitOrder("buy", new BigDecimal("70000"), new BigDecimal("1.0"));
        incomingBuy.setOrderId(UUID.randomUUID());
        incomingBuy.setStatus("open");
        
        OrderCommand command = OrderCommand.placeOrder(incomingBuy);
        matchingEngine.enqueueCommand(command);
        
        // Wait a bit for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: Both orders should be filled
        verify(orderRepository, atLeast(1)).save(any(Order.class));
        verify(tradeRepository, atLeast(1)).save(any(Trade.class));
    }
    
    @Test
    void testPartialFill_IncomingOrderPartiallyFilled() {
        // Setup: Create a resting sell order with quantity 1.0
        Order restingSell = createLimitOrder("sell", new BigDecimal("70000"), new BigDecimal("1.0"));
        restingSell.setOrderId(UUID.randomUUID());
        restingSell.setStatus("open");
        
        orderBook.addLimitOrder(
            restingSell.getOrderId(),
            restingSell.getPrice(),
            restingSell.getQuantity(),
            restingSell.getCreatedAt(),
            restingSell.getClientId(),
            restingSell.getSide()
        );
        
        when(orderRepository.findById(restingSell.getOrderId())).thenReturn(Optional.of(restingSell));
        
        // Test: Create incoming buy order with quantity 2.0 (should partially fill)
        Order incomingBuy = createLimitOrder("buy", new BigDecimal("70000"), new BigDecimal("2.0"));
        incomingBuy.setOrderId(UUID.randomUUID());
        incomingBuy.setStatus("open");
        
        OrderCommand command = OrderCommand.placeOrder(incomingBuy);
        matchingEngine.enqueueCommand(command);
        
        // Wait for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: Trade created, resting order filled, incoming order partially filled
        verify(tradeRepository, atLeast(1)).save(any(Trade.class));
        verify(orderRepository, atLeast(1)).save(argThat(order -> 
            order.getStatus().equals("filled") || order.getStatus().equals("partially_filled")
        ));
    }
    
    @Test
    void testMarketOrder_FillsMultipleLevels() {
        // Setup: Create multiple resting sell orders at different prices
        Order sell1 = createLimitOrder("sell", new BigDecimal("70000"), new BigDecimal("0.5"));
        sell1.setOrderId(UUID.randomUUID());
        Order sell2 = createLimitOrder("sell", new BigDecimal("70010"), new BigDecimal("0.5"));
        sell2.setOrderId(UUID.randomUUID());
        
        orderBook.addLimitOrder(sell1.getOrderId(), sell1.getPrice(), sell1.getQuantity(),
            sell1.getCreatedAt(), sell1.getClientId(), sell1.getSide());
        orderBook.addLimitOrder(sell2.getOrderId(), sell2.getPrice(), sell2.getQuantity(),
            sell2.getCreatedAt(), sell2.getClientId(), sell2.getSide());
        
        when(orderRepository.findById(sell1.getOrderId())).thenReturn(Optional.of(sell1));
        when(orderRepository.findById(sell2.getOrderId())).thenReturn(Optional.of(sell2));
        
        // Test: Create market buy order that should fill both levels
        Order marketBuy = createMarketOrder("buy", new BigDecimal("1.0"));
        marketBuy.setOrderId(UUID.randomUUID());
        marketBuy.setStatus("open");
        
        OrderCommand command = OrderCommand.placeOrder(marketBuy);
        matchingEngine.enqueueCommand(command);
        
        // Wait for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: Multiple trades created
        verify(tradeRepository, atLeast(2)).save(any(Trade.class));
    }
    
    @Test
    void testCancel_RemovesOrderFromBook() {
        // Setup: Create and add a limit order to the book
        Order order = createLimitOrder("buy", new BigDecimal("70000"), new BigDecimal("1.0"));
        order.setOrderId(UUID.randomUUID());
        order.setStatus("open");
        
        orderBook.addLimitOrder(
            order.getOrderId(),
            order.getPrice(),
            order.getQuantity(),
            order.getCreatedAt(),
            order.getClientId(),
            order.getSide()
        );
        
        when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
        
        // Test: Cancel the order
        OrderCommand cancelCommand = OrderCommand.cancelOrder(order.getOrderId());
        matchingEngine.enqueueCommand(cancelCommand);
        
        // Wait for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: Order removed from book and status updated
        assertNull(orderBook.getOrder(order.getOrderId()));
        verify(orderRepository, atLeast(1)).save(argThat(o -> 
            o.getStatus().equals("cancelled")
        ));
    }
    
    @Test
    void testLimitOrder_PriceConditionNotMet() {
        // Setup: Create a resting sell order at 70000
        Order restingSell = createLimitOrder("sell", new BigDecimal("70000"), new BigDecimal("1.0"));
        restingSell.setOrderId(UUID.randomUUID());
        
        orderBook.addLimitOrder(
            restingSell.getOrderId(),
            restingSell.getPrice(),
            restingSell.getQuantity(),
            restingSell.getCreatedAt(),
            restingSell.getClientId(),
            restingSell.getSide()
        );
        
        // Test: Create incoming buy order with limit price 69000 (too low)
        Order incomingBuy = createLimitOrder("buy", new BigDecimal("69000"), new BigDecimal("1.0"));
        incomingBuy.setOrderId(UUID.randomUUID());
        incomingBuy.setStatus("open");
        
        OrderCommand command = OrderCommand.placeOrder(incomingBuy);
        matchingEngine.enqueueCommand(command);
        
        // Wait for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: No trade created, order added to book
        verify(tradeRepository, never()).save(any(Trade.class));
        assertNotNull(orderBook.getOrder(incomingBuy.getOrderId()));
    }
    
    @Test
    void testMarketOrder_NoLiquidity() {
        // Test: Create market buy order when book is empty
        Order marketBuy = createMarketOrder("buy", new BigDecimal("1.0"));
        marketBuy.setOrderId(UUID.randomUUID());
        marketBuy.setStatus("open");
        
        OrderCommand command = OrderCommand.placeOrder(marketBuy);
        matchingEngine.enqueueCommand(command);
        
        // Wait for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify: No trade created, order remains partially filled or open
        verify(tradeRepository, never()).save(any(Trade.class));
    }
    
    // Helper methods
    private Order createLimitOrder(String side, BigDecimal price, BigDecimal quantity) {
        Order order = new Order();
        order.setClientId("test-client");
        order.setInstrument("BTC-USD");
        order.setSide(side);
        order.setType("limit");
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("open");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }
    
    private Order createMarketOrder(String side, BigDecimal quantity) {
        Order order = new Order();
        order.setClientId("test-client");
        order.setInstrument("BTC-USD");
        order.setSide(side);
        order.setType("market");
        order.setPrice(null);
        order.setQuantity(quantity);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("open");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }
}

