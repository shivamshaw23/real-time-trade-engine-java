package com.tradeengine.controller;

import com.tradeengine.dto.ErrorResponse;
import com.tradeengine.dto.OrderBookResponse;
import com.tradeengine.service.OrderBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for orderbook operations
 */
@RestController
@RequestMapping("/orderbook")
public class OrderBookController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderBookController.class);
    
    private final OrderBookService orderBookService;
    
    @Autowired
    public OrderBookController(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }
    
    /**
     * GET /orderbook?instrument=BTC-USD&levels=20 - Get orderbook snapshot
     */
    @GetMapping
    public ResponseEntity<?> getOrderBook(
            @RequestParam String instrument,
            @RequestParam(defaultValue = "20") int levels) {
        
        try {
            if (instrument == null || instrument.isBlank()) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "instrument parameter is required", 
                    "VALIDATION_ERROR"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (levels < 1 || levels > 1000) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "levels must be between 1 and 1000", 
                    "VALIDATION_ERROR"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            OrderBookResponse response = orderBookService.getOrderBook(instrument, levels);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting orderbook", e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to get orderbook: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

