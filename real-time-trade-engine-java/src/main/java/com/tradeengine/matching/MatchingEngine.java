package com.tradeengine.matching;

import com.tradeengine.entity.Order;
import com.tradeengine.entity.Trade;
import com.tradeengine.broadcast.BroadcastService;
import com.tradeengine.broadcast.event.OrderStateChangeEvent;
import com.tradeengine.broadcast.event.OrderbookDeltaEvent;
import com.tradeengine.broadcast.event.TradeEvent;
import com.tradeengine.orderbook.OrderBook;
import com.tradeengine.orderbook.OrderBookEntry;
import com.tradeengine.orderbook.OrderBookManager;
import com.tradeengine.orderbook.OrderBookSnapshot;
import com.tradeengine.orderbook.PriceLevel;
import com.tradeengine.recovery.RecoveryService;
import com.tradeengine.repository.OrderRepository;
import com.tradeengine.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded matching engine worker.
 * Processes orders from a queue and matches them against the orderbook.
 */
@Component
public class MatchingEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);
    private static final int SNAPSHOT_FLUSH_INTERVAL_MS = 1000; // Flush snapshot every second
    private static final int MAX_QUEUE_SIZE = 10000; // Bounded queue to prevent memory issues
    
    private final LinkedBlockingQueue<OrderCommand> queue;
    private final OrderBookManager orderBookManager;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final RecoveryService recoveryService;
    private final BroadcastService broadcastService;
    private final MatchingMetrics metrics;
    
    private Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long lastSnapshotFlushTime;
    
    @Autowired
    public MatchingEngine(OrderBookManager orderBookManager,
                         OrderRepository orderRepository,
                         TradeRepository tradeRepository,
                         RecoveryService recoveryService,
                         BroadcastService broadcastService,
                         MatchingMetrics metrics) {
        this.queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE); // Bounded queue
        this.orderBookManager = orderBookManager;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.recoveryService = recoveryService;
        this.broadcastService = broadcastService;
        this.metrics = metrics;
        this.lastSnapshotFlushTime = System.currentTimeMillis();
    }
    
    @PostConstruct
    public void start() {
        // Phase 6: Recovery - Rebuild orderbook from database before starting matching
        logger.info("Starting recovery process...");
        try {
            int recoveredCount = recoveryService.recoverOrderbookState();
            logger.info("Recovery completed. Recovered {} orders. Starting matching engine...", recoveredCount);
        } catch (Exception e) {
            logger.error("Recovery failed, but continuing with matching engine startup", e);
            // Continue startup even if recovery fails - better to have partial state than no state
        }
        
        // Start matching engine worker thread
        running.set(true);
        workerThread = new Thread(this::run, "MatchingEngine-Worker");
        workerThread.setDaemon(false);
        workerThread.start();
        logger.info("Matching engine started");
    }
    
    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for matching engine to stop");
            }
        }
        logger.info("Matching engine stopped");
    }
    
    /**
     * Enqueue an order command (non-blocking)
     * Uses offer() to prevent blocking and allows graceful handling of queue full scenarios
     */
    public void enqueueCommand(OrderCommand command) {
        if (!queue.offer(command)) {
            // Queue is full - log error and reject
            logger.error("Matching queue is full (size: {}), rejecting order command", queue.size());
            // Could throw exception or return false, but for now we log and continue
            // In production, you might want to throw an exception to fail the request
            throw new IllegalStateException("Matching queue is full, please retry later");
        }
    }
    
    /**
     * Main matching loop
     */
    private void run() {
        logger.info("Matching engine worker thread started");
        
        while (running.get()) {
            try {
                OrderCommand cmd = queue.take(); // Blocks until command available
                
                if (cmd.getType() == OrderCommand.Type.CANCEL) {
                    handleCancel(cmd.getOrderId());
                } else if (cmd.getType() == OrderCommand.Type.PLACE) {
                    handlePlaceOrder(cmd.getOrder());
                }
                
                // Periodically flush snapshot
                maybeFlushSnapshotPeriodically();
                
            } catch (InterruptedException e) {
                if (running.get()) {
                    logger.warn("Matching engine interrupted", e);
                }
                break;
            } catch (RuntimeException e) {
                // DB errors or other critical errors - pause matching engine
                logger.error("Critical error processing order command, pausing matching engine", e);
                
                // Pause matching engine - wait with exponential backoff before retrying
                long pauseMs = 1000;
                int pauseAttempt = 0;
                while (pauseAttempt < 5 && running.get()) {
                    try {
                        logger.warn("Matching engine paused, waiting {}ms before retry (attempt {})", pauseMs, pauseAttempt + 1);
                        Thread.sleep(pauseMs);
                        pauseMs = Math.min(pauseMs * 2, 10000); // Cap at 10 seconds
                        pauseAttempt++;
                        
                        // Try to continue - check if DB is back up by testing a simple query
                        // For now, we'll just continue and let the retry handler deal with it
                        break;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // Continue processing - retry handler will deal with DB errors
                logger.info("Matching engine resuming after pause");
            } catch (Exception e) {
                logger.error("Unexpected error processing order command", e);
                // Continue processing other orders
            }
        }
        
        logger.info("Matching engine worker thread stopped");
    }
    
    /**
     * Handle cancel command
     */
    private void handleCancel(UUID orderId) {
        OrderBook orderBook = findOrderBookForOrder(orderId);
        if (orderBook == null) {
            logger.warn("Order not found in orderbook for cancellation: {}", orderId);
            return;
        }
        
        // Cancel in orderbook
        boolean cancelled = orderBook.cancelOrder(orderId);
        if (cancelled) {
            // Persist cancellation
            persistCancel(orderId);
            logger.info("Order cancelled: {}", orderId);
        }
    }
    
    /**
     * Handle place order command
     */
    private void handlePlaceOrder(Order order) {
        // Defensive check: order must have instrument
        if (order.getInstrument() == null || order.getInstrument().isBlank()) {
            logger.error("Order {} has null or blank instrument, rejecting", order.getOrderId());
            order.setStatus("rejected");
            try {
                orderRepository.save(order);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        // Defensive check: order must have quantity
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Order {} has invalid quantity, rejecting", order.getOrderId());
            order.setStatus("rejected");
            try {
                orderRepository.save(order);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        OrderBook orderBook = orderBookManager.getOrCreateOrderBook(order.getInstrument());
        
        if ("market".equals(order.getType())) {
            matchMarket(order, orderBook);
        } else {
            matchLimit(order, orderBook);
        }
    }
    
    /**
     * Match a market order against the orderbook
     */
    @Transactional
    private void matchMarket(Order incomingOrder, OrderBook orderBook) {
        logger.debug("Matching market order: {}", incomingOrder.getOrderId());
        
        // Defensive check: ensure quantity is valid
        if (incomingOrder.getQuantity() == null) {
            logger.error("Market order {} has null quantity, rejecting", incomingOrder.getOrderId());
            incomingOrder.setStatus("rejected");
            try {
                orderRepository.save(incomingOrder);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        BigDecimal filledQty = incomingOrder.getFilledQuantity() != null ? incomingOrder.getFilledQuantity() : BigDecimal.ZERO;
        BigDecimal remainingQty = incomingOrder.getQuantity().subtract(filledQty);
        List<Trade> trades = new ArrayList<>();
        List<Order> ordersToUpdate = new ArrayList<>();
        
        String side = incomingOrder.getSide();
        
        // Defensive check: side must be valid
        if (side == null || (!"buy".equals(side) && !"sell".equals(side))) {
            logger.error("Market order {} has invalid side: {}, rejecting", incomingOrder.getOrderId(), side);
            incomingOrder.setStatus("rejected");
            try {
                orderRepository.save(incomingOrder);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        // Market buy: match against asks (lowest first)
        // Market sell: match against bids (highest first)
        while (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            PriceLevel bestLevel = "buy".equals(side) 
                ? orderBook.getBestAskLevel() 
                : orderBook.getBestBidLevel();
            
            if (bestLevel == null) {
                // No more liquidity
                break;
            }
            
            OrderBookEntry restingEntry = bestLevel.getFirstOrder();
            if (restingEntry == null) {
                break;
            }
            
            // Match against this resting order
            BigDecimal tradeQty = remainingQty.min(restingEntry.getRemainingQty());
            BigDecimal tradePrice = bestLevel.getPrice();
            
            // Create trade
            Trade trade = createTrade(incomingOrder, restingEntry, tradePrice, tradeQty);
            trades.add(trade);
            
            // Update quantities
            remainingQty = remainingQty.subtract(tradeQty);
            BigDecimal newRestingQty = restingEntry.getRemainingQty().subtract(tradeQty);
            
            // Update incoming order
            BigDecimal newFilledQty = incomingOrder.getFilledQuantity().add(tradeQty);
            incomingOrder.setFilledQuantity(newFilledQty);
            
            // Update resting order
            restingEntry.setRemainingQty(newRestingQty);
            orderBook.updateOrderQuantity(restingEntry.getOrderId(), newRestingQty);
            
            // Update resting order entity
            Order restingOrder = orderRepository.findById(restingEntry.getOrderId()).orElse(null);
            if (restingOrder != null) {
                restingOrder.setFilledQuantity(
                    restingOrder.getFilledQuantity().add(tradeQty));
                
                // Check if resting order is fully filled
                if (newRestingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    restingOrder.setStatus("filled");
                    // Remove fully filled resting orders from book
                    orderBook.cancelOrder(restingEntry.getOrderId());
                } else {
                    restingOrder.setStatus("partially_filled");
                }
                ordersToUpdate.add(restingOrder);
            } else {
                // If order not found in DB but still in orderbook, remove it
                if (newRestingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    orderBook.cancelOrder(restingEntry.getOrderId());
                }
            }
        }
        
        // Update incoming order status
        // Edge case: Market order with no liquidity - reject remaining quantity
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
            incomingOrder.setStatus("filled");
        } else {
            // Market order partially filled but book exhausted - reject remaining quantity
            // Alternative behavior: Could set to "partially_filled" and keep in DB
            // We choose to reject remaining quantity for market orders
            incomingOrder.setStatus("partially_filled");
            logger.warn("Market order {} partially filled with {} remaining (book exhausted). Remaining quantity rejected.", 
                incomingOrder.getOrderId(), remainingQty);
            // Note: Remaining quantity is not added to orderbook (market orders don't rest)
        }
        ordersToUpdate.add(incomingOrder);
        
        // Persist all changes
        persistTradesAndOrders(trades, ordersToUpdate);
    }
    
    /**
     * Match a limit order against the orderbook
     */
    @Transactional
    private void matchLimit(Order incomingOrder, OrderBook orderBook) {
        logger.debug("Matching limit order: {}", incomingOrder.getOrderId());
        
        // Defensive check: limit orders must have a price
        BigDecimal limitPrice = incomingOrder.getPrice();
        if (limitPrice == null) {
            logger.error("Limit order {} has null price, rejecting", incomingOrder.getOrderId());
            incomingOrder.setStatus("rejected");
            try {
                orderRepository.save(incomingOrder);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        // Defensive check: ensure quantity and filledQuantity are valid
        if (incomingOrder.getQuantity() == null) {
            logger.error("Limit order {} has null quantity, rejecting", incomingOrder.getOrderId());
            incomingOrder.setStatus("rejected");
            try {
                orderRepository.save(incomingOrder);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        BigDecimal filledQty = incomingOrder.getFilledQuantity() != null ? incomingOrder.getFilledQuantity() : BigDecimal.ZERO;
        BigDecimal remainingQty = incomingOrder.getQuantity().subtract(filledQty);
        String side = incomingOrder.getSide();
        
        // Defensive check: side must be valid
        if (side == null || (!"buy".equals(side) && !"sell".equals(side))) {
            logger.error("Limit order {} has invalid side: {}, rejecting", incomingOrder.getOrderId(), side);
            incomingOrder.setStatus("rejected");
            try {
                orderRepository.save(incomingOrder);
            } catch (Exception e) {
                logger.error("Failed to persist rejected order", e);
            }
            return;
        }
        
        List<Trade> trades = new ArrayList<>();
        List<Order> ordersToUpdate = new ArrayList<>();
        
        // Match against opposite side best prices
        // Limit buy: match against asks (lowest first) if ask price <= buy price
        // Limit sell: match against bids (highest first) if bid price >= sell price
        while (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            PriceLevel bestLevel = "buy".equals(side) 
                ? orderBook.getBestAskLevel() 
                : orderBook.getBestBidLevel();
            
            if (bestLevel == null) {
                break;
            }
            
            BigDecimal bestPrice = bestLevel.getPrice();
            
            // Check price condition
            boolean priceMatch = "buy".equals(side) 
                ? bestPrice.compareTo(limitPrice) <= 0  // Buy: can match if ask <= limit
                : bestPrice.compareTo(limitPrice) >= 0;  // Sell: can match if bid >= limit
            
            if (!priceMatch) {
                break;
            }
            
            OrderBookEntry restingEntry = bestLevel.getFirstOrder();
            if (restingEntry == null) {
                break;
            }
            
            // Match against this resting order
            BigDecimal tradeQty = remainingQty.min(restingEntry.getRemainingQty());
            BigDecimal tradePrice = bestPrice;
            
            // Create trade
            Trade trade = createTrade(incomingOrder, restingEntry, tradePrice, tradeQty);
            trades.add(trade);
            
            // Update quantities
            remainingQty = remainingQty.subtract(tradeQty);
            BigDecimal newRestingQty = restingEntry.getRemainingQty().subtract(tradeQty);
            
            // Update incoming order
            BigDecimal newFilledQty = incomingOrder.getFilledQuantity().add(tradeQty);
            incomingOrder.setFilledQuantity(newFilledQty);
            
            // Update resting order
            restingEntry.setRemainingQty(newRestingQty);
            orderBook.updateOrderQuantity(restingEntry.getOrderId(), newRestingQty);
            
            // Update resting order entity
            Order restingOrder = orderRepository.findById(restingEntry.getOrderId()).orElse(null);
            if (restingOrder != null) {
                restingOrder.setFilledQuantity(
                    restingOrder.getFilledQuantity().add(tradeQty));
                
                // Check if resting order is fully filled
                if (newRestingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    restingOrder.setStatus("filled");
                    // Remove fully filled resting orders from book
                    orderBook.cancelOrder(restingEntry.getOrderId());
                } else {
                    restingOrder.setStatus("partially_filled");
                }
                ordersToUpdate.add(restingOrder);
            } else {
                // If order not found in DB but still in orderbook, remove it
                if (newRestingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    orderBook.cancelOrder(restingEntry.getOrderId());
                }
            }
        }
        
        // If there's remaining quantity, add to orderbook
        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            // Add to orderbook
            OrderBookEntry entry = orderBook.addLimitOrder(
                incomingOrder.getOrderId(),
                limitPrice,
                remainingQty,
                incomingOrder.getCreatedAt(),
                incomingOrder.getClientId(),
                side
            );
            
            // Update order status
            if (incomingOrder.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                incomingOrder.setStatus("partially_filled");
            } else {
                incomingOrder.setStatus("open");
            }
        } else {
            // Fully filled
            incomingOrder.setStatus("filled");
        }
        
        ordersToUpdate.add(incomingOrder);
        
        // Persist all changes
        persistTradesAndOrders(trades, ordersToUpdate);
    }
    
    /**
     * Create a Trade entity
     */
    private Trade createTrade(Order incomingOrder, OrderBookEntry restingEntry, 
                             BigDecimal price, BigDecimal quantity) {
        Trade trade = new Trade();
        
        // Determine buy and sell order IDs
        UUID buyOrderId, sellOrderId;
        if ("buy".equals(incomingOrder.getSide())) {
            buyOrderId = incomingOrder.getOrderId();
            sellOrderId = restingEntry.getOrderId();
        } else {
            buyOrderId = restingEntry.getOrderId();
            sellOrderId = incomingOrder.getOrderId();
        }
        
        trade.setBuyOrderId(buyOrderId);
        trade.setSellOrderId(sellOrderId);
        trade.setInstrument(incomingOrder.getInstrument());
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setExecutedAt(Instant.now());
        
        return trade;
    }
    
    /**
     * Persist trades and update orders in a single transaction
     */
    @Transactional
    private void persistTradesAndOrders(List<Trade> trades, List<Order> orders) {
        // Save all trades (idempotency: trade_id is unique)
        // Use retry handler for DB resilience
        for (Trade trade : trades) {
            try {
                DatabaseRetryHandler.executeWithRetry(() -> {
                    tradeRepository.save(trade);
                    logger.info("Trade executed: trade_id={} instrument={} price={} quantity={}", 
                        trade.getTradeId(), trade.getInstrument(), trade.getPrice(), trade.getQuantity());
                    return null;
                }, "saveTrade-" + trade.getTradeId());
                
                // Broadcast trade event (non-blocking, doesn't need retry)
                TradeEvent tradeEvent = new TradeEvent();
                tradeEvent.setTradeId(trade.getTradeId());
                tradeEvent.setBuyOrderId(trade.getBuyOrderId());
                tradeEvent.setSellOrderId(trade.getSellOrderId());
                tradeEvent.setInstrument(trade.getInstrument());
                tradeEvent.setPrice(trade.getPrice());
                tradeEvent.setQuantity(trade.getQuantity());
                tradeEvent.setExecutedAt(trade.getExecutedAt().toString());
                broadcastService.broadcastTrade(tradeEvent);
                
            } catch (Exception e) {
                // If trade already exists (idempotency), log and continue
                if (e.getMessage() != null && (e.getMessage().contains("duplicate") || 
                    e.getMessage().contains("unique"))) {
                    logger.debug("Trade already exists (idempotent): {}", trade.getTradeId());
                } else {
                    logger.error("Failed to persist trade {} after retries", trade.getTradeId(), e);
                    // Re-throw to trigger matching engine pause
                    throw new RuntimeException("Failed to persist trade", e);
                }
            }
        }
        
        // Update all orders and broadcast state changes
        // Use retry handler for DB resilience
        for (Order order : orders) {
            try {
                DatabaseRetryHandler.executeWithRetry(() -> {
                    orderRepository.save(order);
                    logger.info("Order state changed: order_id={} status={} filled_quantity={}", 
                        order.getOrderId(), order.getStatus(), order.getFilledQuantity());
                    return null;
                }, "saveOrder-" + order.getOrderId());
                
                // Broadcast order state change (non-blocking)
                OrderStateChangeEvent stateEvent = new OrderStateChangeEvent();
                stateEvent.setOrderId(order.getOrderId());
                stateEvent.setClientId(order.getClientId());
                stateEvent.setInstrument(order.getInstrument());
                stateEvent.setSide(order.getSide());
                stateEvent.setType(order.getType());
                stateEvent.setPrice(order.getPrice());
                stateEvent.setQuantity(order.getQuantity());
                stateEvent.setFilledQuantity(order.getFilledQuantity());
                stateEvent.setStatus(order.getStatus());
                stateEvent.setUpdatedAt(order.getUpdatedAt().toString());
                broadcastService.broadcastOrderStateChange(stateEvent);
                
                // Track metrics
                if ("filled".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                    metrics.incrementOrdersMatched();
                }
                
            } catch (Exception e) {
                logger.error("Failed to persist order {} after retries", order.getOrderId(), e);
                // Re-throw to trigger matching engine pause
                throw new RuntimeException("Failed to persist order", e);
            }
        }
        
        // Broadcast orderbook delta if trades occurred
        if (!trades.isEmpty() && !orders.isEmpty()) {
            String instrument = orders.get(0).getInstrument();
            OrderBook orderBook = orderBookManager.getOrderBook(instrument);
            if (orderBook != null) {
                broadcastOrderbookDelta(orderBook);
            }
        }
    }
    
    /**
     * Persist order cancellation
     * Uses retry handler for DB resilience
     */
    @Transactional
    private void persistCancel(UUID orderId) {
        try {
            DatabaseRetryHandler.executeWithRetry(() -> {
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    order.setStatus("cancelled");
                    orderRepository.save(order);
                    logger.info("Order cancelled: order_id={} instrument={}", orderId, order.getInstrument());
                    
                    // Broadcast order state change
                    OrderStateChangeEvent stateEvent = new OrderStateChangeEvent();
                    stateEvent.setOrderId(order.getOrderId());
                    stateEvent.setClientId(order.getClientId());
                    stateEvent.setInstrument(order.getInstrument());
                    stateEvent.setSide(order.getSide());
                    stateEvent.setType(order.getType());
                    stateEvent.setPrice(order.getPrice());
                    stateEvent.setQuantity(order.getQuantity());
                    stateEvent.setFilledQuantity(order.getFilledQuantity());
                    stateEvent.setStatus(order.getStatus());
                    stateEvent.setUpdatedAt(order.getUpdatedAt().toString());
                    broadcastService.broadcastOrderStateChange(stateEvent);
                    
                    // Broadcast orderbook delta
                    OrderBook orderBook = orderBookManager.getOrderBook(order.getInstrument());
                    if (orderBook != null) {
                        broadcastOrderbookDelta(orderBook);
                    }
                }
                return null;
            }, "cancelOrder-" + orderId);
        } catch (Exception e) {
            logger.error("Failed to persist cancellation for order {} after retries", orderId, e);
            // Don't re-throw - cancellation is already applied in orderbook
        }
    }
    
    /**
     * Broadcast orderbook delta event
     */
    private void broadcastOrderbookDelta(OrderBook orderBook) {
        OrderBookSnapshot snapshot = orderBook.getSnapshot();
        List<OrderbookDeltaEvent.PriceLevelDelta> bids = snapshot.getBids().stream()
            .limit(20) // Top 20 levels
            .map(level -> new OrderbookDeltaEvent.PriceLevelDelta(level.getPrice(), level.getTotalQuantity()))
            .collect(java.util.stream.Collectors.toList());
        
        List<OrderbookDeltaEvent.PriceLevelDelta> asks = snapshot.getAsks().stream()
            .limit(20) // Top 20 levels
            .map(level -> new OrderbookDeltaEvent.PriceLevelDelta(level.getPrice(), level.getTotalQuantity()))
            .collect(java.util.stream.Collectors.toList());
        
        OrderbookDeltaEvent deltaEvent = new OrderbookDeltaEvent(
            orderBook.getInstrument(),
            bids,
            asks,
            snapshot.getSnapshotTime().toString()
        );
        
        broadcastService.broadcastOrderbookDelta(deltaEvent);
    }
    
    /**
     * Find orderbook for an order by looking it up
     */
    private OrderBook findOrderBookForOrder(UUID orderId) {
        // This is inefficient but necessary for cancel operations
        // In a production system, you might want to maintain a mapping
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }
        return orderBookManager.getOrderBook(order.getInstrument());
    }
    
    /**
     * Periodically flush orderbook snapshot (for persistence to DB if needed)
     */
    private void maybeFlushSnapshotPeriodically() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshotFlushTime >= SNAPSHOT_FLUSH_INTERVAL_MS) {
            // TODO: If needed, persist orderbook snapshots to DB
            // For now, snapshots are just in memory for GET /orderbook
            lastSnapshotFlushTime = now;
        }
    }
}

