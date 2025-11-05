package com.tradeengine.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeengine.broadcast.event.OrderStateChangeEvent;
import com.tradeengine.broadcast.event.OrderbookDeltaEvent;
import com.tradeengine.broadcast.event.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for broadcasting events to connected SSE clients
 */
@Service
public class BroadcastService {
    
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private static final long SSE_TIMEOUT_MS = 300_000; // 5 minutes
    
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersByChannel;
    
    @Autowired
    public BroadcastService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.emittersByChannel = new ConcurrentHashMap<>();
    }
    
    /**
     * Subscribe to a channel (e.g., "trades", "orderbook", "orders")
     */
    public SseEmitter subscribe(String channel) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        
        emitter.onCompletion(() -> {
            logger.debug("SSE client disconnected from channel: {}", channel);
            removeEmitter(channel, emitter);
        });
        
        emitter.onTimeout(() -> {
            logger.debug("SSE client timeout on channel: {}", channel);
            removeEmitter(channel, emitter);
        });
        
        emitter.onError((ex) -> {
            logger.warn("SSE error on channel {}: {}", channel, ex.getMessage());
            removeEmitter(channel, emitter);
        });
        
        emittersByChannel.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.info("SSE client subscribed to channel: {}", channel);
        
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"channel\":\"" + channel + "\"}"));
        } catch (IOException e) {
            logger.error("Failed to send initial connection message", e);
            removeEmitter(channel, emitter);
        }
        
        return emitter;
    }
    
    /**
     * Broadcast orderbook delta
     */
    public void broadcastOrderbookDelta(OrderbookDeltaEvent event) {
        broadcast("orderbook", event);
    }
    
    /**
     * Broadcast trade event
     */
    public void broadcastTrade(TradeEvent event) {
        broadcast("trades", event);
    }
    
    /**
     * Broadcast order state change
     */
    public void broadcastOrderStateChange(OrderStateChangeEvent event) {
        broadcast("orders", event);
    }
    
    /**
     * Broadcast to a specific channel
     */
    private void broadcast(String channel, Object event) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByChannel.get(channel);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(event);
            
            emitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.getClass().getSimpleName())
                            .data(json));
                    return false;
                } catch (IOException e) {
                    logger.debug("Failed to send event to SSE client: {}", e.getMessage());
                    return true; // Remove failed emitter
                }
            });
        } catch (Exception e) {
            logger.error("Failed to serialize event for broadcast", e);
        }
    }
    
    private void removeEmitter(String channel, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByChannel.get(channel);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByChannel.remove(channel);
            }
        }
    }
    
    /**
     * Get number of connected clients for a channel
     */
    public int getConnectedCount(String channel) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByChannel.get(channel);
        return emitters != null ? emitters.size() : 0;
    }
}

