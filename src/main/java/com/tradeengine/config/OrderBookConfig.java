package com.tradeengine.config;

import com.tradeengine.orderbook.OrderBookManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for orderbook components
 */
@Configuration
public class OrderBookConfig {
    
    @Bean
    public OrderBookManager orderBookManager() {
        return new OrderBookManager();
    }
}

