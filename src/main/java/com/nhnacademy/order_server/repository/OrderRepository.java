package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order,Long> {

    @Query(value = "SELECT o FROM Order o " +
            "JOIN FETCH o.delivery " +
            "LEFT JOIN FETCH o.orderReturn " +
            "WHERE o.userId = :userId AND o.deliveryStatus != 'PENDING'",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId AND o.deliveryStatus != 'PENDING'")
    Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId AND o.orderPassword = :password")
    Optional<Order> findByIdAndOrderPassword(@Param("orderId") Long orderId, @Param("password") Integer password);

    Optional<Order> findByOrderKey(String orderKey);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.deliveryStatus = :status")
    Page<Order> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);

    List<Order> findByDeliveryStatusAndOrderDateBefore(DeliveryStatus status, LocalDateTime time);
}
