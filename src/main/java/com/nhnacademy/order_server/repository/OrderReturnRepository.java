package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.OrderReturn;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    @Query("SELECT r FROM OrderReturn r JOIN FETCH r.order WHERE r.id = :id")
    Optional<OrderReturn> findByIdWithOrder(@Param("id") Long id);

    Optional<OrderReturn> findByOrderId(Long orderId);
}
