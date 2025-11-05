package com.tradeengine.controller;

import com.tradeengine.dto.ErrorResponse;
import com.tradeengine.dto.TradeResponse;
import com.tradeengine.entity.Trade;
import com.tradeengine.service.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for trade operations
 */
@RestController
@RequestMapping("/trades")
public class TradeController {
    
    private static final Logger logger = LoggerFactory.getLogger(TradeController.class);
    
    private final TradeService tradeService;
    
    @Autowired
    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }
    
    /**
     * GET /trades?limit=50 - Get recent trades
     */
    @GetMapping
    public ResponseEntity<?> getRecentTrades(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<Trade> trades = tradeService.getRecentTrades(limit);
            List<TradeResponse> responses = trades.stream()
                    .map(this::toTradeResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            logger.error("Error getting recent trades", e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to get trades: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Convert Trade entity to TradeResponse DTO
     */
    private TradeResponse toTradeResponse(Trade trade) {
        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getTradeId());
        response.setBuyOrderId(trade.getBuyOrderId());
        response.setSellOrderId(trade.getSellOrderId());
        response.setInstrument(trade.getInstrument());
        response.setPrice(trade.getPrice());
        response.setQuantity(trade.getQuantity());
        response.setExecutedAt(trade.getExecutedAt());
        return response;
    }
}

