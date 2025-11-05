package com.tradeengine.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Handles database retry logic with exponential backoff
 * Used by matching engine when DB operations fail
 */
public class DatabaseRetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseRetryHandler.class);
    
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final long MAX_BACKOFF_MS = 5000;
    
    /**
     * Execute a database operation with retry logic
     * 
     * @param operation The operation to execute
     * @param operationName Name of operation for logging
     * @return Result of the operation
     * @throws RuntimeException if operation fails after all retries
     */
    public static <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;
        
        while (attempt < MAX_RETRIES) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                
                if (attempt >= MAX_RETRIES) {
                    logger.error("Database operation '{}' failed after {} attempts", operationName, MAX_RETRIES, e);
                    throw new RuntimeException("Database operation failed after retries: " + operationName, e);
                }
                
                logger.warn("Database operation '{}' failed (attempt {}/{}), retrying in {}ms", 
                    operationName, attempt, MAX_RETRIES, backoffMs, e);
                
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
                
                // Exponential backoff with cap
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
        
        throw new RuntimeException("Should not reach here");
    }
    
    /**
     * Execute a void database operation with retry logic
     */
    public static void executeWithRetry(Runnable operation, String operationName) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, operationName);
    }
}

