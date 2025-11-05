package com.tradeengine.service;

import com.tradeengine.entity.Trade;
import com.tradeengine.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeService {
    
    private final TradeRepository tradeRepository;
    
    @Autowired
    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }
    
    /**
     * Get recent trades
     * 
     * @param limit Maximum number of trades to return
     * @return List of recent trades
     */
    public List<Trade> getRecentTrades(int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 1000)); // Cap at 1000
        Page<Trade> trades = tradeRepository.findAllByOrderByExecutedAtDesc(pageable);
        return trades.getContent();
    }
}

