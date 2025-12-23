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
            "WHERE o.userId = :userId " +
            "AND o.deliveryStatus NOT IN ('PAYMENT_WAITING') " +
            "ORDER BY o.orderDate DESC",
            countQuery = "SELECT count(o) FROM Order o " +
                    "WHERE o.userId = :userId " +
                    "AND o.deliveryStatus NOT IN ('PAYMENT_WAITING')")
    Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    Optional<Order> findByOrderKey(String orderKey);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.deliveryStatus = :status")
    Page<Order> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);

    List<Order> findByDeliveryStatusAndOrderDateBefore(DeliveryStatus status, LocalDateTime time);


    @Query(value = "SELECT o FROM Order o " +
            "JOIN FETCH o.delivery " +
            "LEFT JOIN FETCH o.orderReturn " +
            "WHERE o.userId = :userId " +
            "AND o.orderDate >= :startDate " +
            "AND o.deliveryStatus = 'PURCHASE_CONFIRMED'",
            countQuery = "SELECT count(o) FROM Order o " +
                    "WHERE o.userId = :userId " +
                    "AND o.orderDate >= :startDate " +
                    "AND o.deliveryStatus = 'PURCHASE_CONFIRMED'")
    Page<Order> findByUserIdAndOrderDateAfter(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );


    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM Order o JOIN o.orderItems oi " +
            "WHERE o.userId = :userId AND oi.bookId = :bookId " +
            "AND o.deliveryStatus = 'PURCHASE_CONFIRMED'")
    boolean hasPurchasedBook(@Param("userId") Long userId,
                             @Param("bookId") Long bookId);

}
