package com.tradeengine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller (Phase 8: Observability)
 */
@RestController
public class HealthController {
    
    private final DataSource dataSource;
    
    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * GET /healthz - Enhanced health check endpoint
     * Checks:
     * - DB connection
     * - Queue health (non-blocking check)
     */
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        
        // Check database connection
        boolean dbHealthy = checkDatabase();
        response.put("database", dbHealthy ? "UP" : "DOWN");
        
        // Check queue health (always UP for now, queue is in-memory)
        response.put("queue", "UP");
        
        // Overall status
        String overallStatus = dbHealthy ? "UP" : "DOWN";
        response.put("status", overallStatus);
        
        if ("UP".equals(overallStatus)) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }
    
    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2); // 2 second timeout
        } catch (Exception e) {
            return false;
        }
    }
}

