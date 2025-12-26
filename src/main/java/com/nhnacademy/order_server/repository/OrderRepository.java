package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.dto.response.OrderAggregationDto;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 1. [조회] 회원 주문 목록 (N+1 방지를 위한 FETCH JOIN)
    @Query(value = "SELECT o FROM Order o " +
            "JOIN FETCH o.delivery " +
            "LEFT JOIN FETCH o.orderReturn " +
            "WHERE o.userId = :userId " +
            "AND o.deliveryStatus NOT IN (com.nhnacademy.order_server.entity.enums.DeliveryStatus.PAYMENT_WAITING) " +
            "ORDER BY o.orderDate DESC",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId")
    Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    // 2. [조회] 최근 3개월 등 특정 기간 주문 조회 (N+1 방지 적용)
    @Query(value = "SELECT o FROM Order o " +
            "JOIN FETCH o.delivery " +
            "LEFT JOIN FETCH o.orderReturn " +
            "WHERE o.userId = :userId " +
            "AND o.orderDate >= :startDate " +
            "ORDER BY o.orderDate DESC",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId AND o.orderDate >= :startDate")
    Page<Order> findByUserIdAndOrderDateAfter(@Param("userId") Long userId,
                                              @Param("startDate") LocalDateTime startDate,
                                              Pageable pageable);

    // 3. [조회] 주문 상세 (아이템 리스트 FETCH JOIN)
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    Optional<Order> findByOrderKey(String orderKey);

    // 4. [집계/배치] 전 회원 대상 등급 산정용 (Network N+1 해결)
    @Query("SELECT new com.nhnacademy.order_server.dto.response.OrderAggregationDto(" +
            "o.userId, SUM(CAST(o.paymentAmount AS long))) " +
            "FROM Order o " +
            "WHERE o.orderDate BETWEEN :startDate AND :endDate " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED " +
            "AND o.userId IS NOT NULL " +
            "GROUP BY o.userId")
    List<OrderAggregationDto> findOrderAggregations(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    // 5. [집계] 특정 회원의 구매 확정 총액 조회
    @Query("SELECT SUM(CAST(o.paymentAmount AS long)) " +
            "FROM Order o " +
            "WHERE o.userId = :userId " +
            "AND o.orderDate >= :since " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED")
    Long sumPaymentAmountByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    List<Order> findByDeliveryStatusAndOrderDateBefore(DeliveryStatus status, LocalDateTime threshold);

    List<Order> findByDeliveryStatusAndDelivery_ActualShipDateBefore(DeliveryStatus status, LocalDateTime threshold);

    List<Order> findByDeliveryStatusAndDelivery_ActualCompletionDateBefore(DeliveryStatus status, LocalDateTime threshold);

    // 9. [기타] 도서 구매 여부 확인
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM Order o JOIN o.orderItems oi " +
            "WHERE o.userId = :userId AND oi.bookId = :bookId " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED")
    boolean hasPurchasedBook(@Param("userId") Long userId, @Param("bookId") Long bookId);
}