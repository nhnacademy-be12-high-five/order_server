package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {

    Page<Order> findAllByUserId(Long userId, Pageable pageable);


    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);


    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId AND o.orderPassword = :password")
    Optional<Order> findByIdAndOrderPassword(@Param("orderId") Long orderId, @Param("password") Integer password);

    Optional<Order> findByOrderKey(String orderKey);
}
