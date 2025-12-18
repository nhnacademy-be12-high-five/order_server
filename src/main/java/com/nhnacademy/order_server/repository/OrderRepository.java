package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {

    // o.id (주문번호) -> o.userId (유저아이디) 로 변경
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.delivery WHERE o.userId = :userId",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId")
    Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId AND o.orderPassword = :password")
    Optional<Order> findByIdAndOrderPassword(@Param("orderId") Long orderId, @Param("password") Integer password);

    Optional<Order> findByOrderKey(String orderKey);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.deliveryStatus = :status")
    Page<Order> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);
}
