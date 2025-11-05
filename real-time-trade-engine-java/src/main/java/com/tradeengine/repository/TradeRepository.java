package com.tradeengine.repository;

import com.tradeengine.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    
    /**
     * Find recent trades ordered by execution time descending
     */
    Page<Trade> findAllByOrderByExecutedAtDesc(Pageable pageable);
}

