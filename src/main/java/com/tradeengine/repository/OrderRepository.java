package com.tradeengine.repository;

import com.tradeengine.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    
    /**
     * Find order by idempotency key
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Find orders by instrument and status
     */
    @Query("SELECT o FROM Order o WHERE o.instrument = :instrument AND o.status = :status")
    List<Order> findByInstrumentAndStatus(@Param("instrument") String instrument, 
                                         @Param("status") String status);
    
    /**
     * Find all open and partially filled orders, ordered by created_at
     * Used for recovery/reconstruction of orderbook on startup
     */
    @Query("SELECT o FROM Order o WHERE o.status IN ('open', 'partially_filled') ORDER BY o.createdAt ASC")
    List<Order> findOpenAndPartiallyFilledOrders();
}
