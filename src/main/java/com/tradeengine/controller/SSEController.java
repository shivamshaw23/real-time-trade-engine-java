package com.tradeengine.controller;

import com.tradeengine.broadcast.BroadcastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE (Server-Sent Events) controller for real-time event streaming
 */
@RestController
@RequestMapping("/events")
public class SSEController {
    
    private final BroadcastService broadcastService;
    
    @Autowired
    public SSEController(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }
    
    /**
     * GET /events/trades - Subscribe to trade events
     */
    @GetMapping(value = "/trades", produces = "text/event-stream")
    public SseEmitter subscribeToTrades() {
        return broadcastService.subscribe("trades");
    }
    
    /**
     * GET /events/orderbook?instrument=BTC-USD - Subscribe to orderbook delta events
     */
    @GetMapping(value = "/orderbook", produces = "text/event-stream")
    public SseEmitter subscribeToOrderbook(@RequestParam(required = false) String instrument) {
        // For now, subscribe to all orderbook events
        // In production, you might want to filter by instrument
        return broadcastService.subscribe("orderbook");
    }
    
    /**
     * GET /events/orders - Subscribe to order state change events
     */
    @GetMapping(value = "/orders", produces = "text/event-stream")
    public SseEmitter subscribeToOrders() {
        return broadcastService.subscribe("orders");
    }
}

