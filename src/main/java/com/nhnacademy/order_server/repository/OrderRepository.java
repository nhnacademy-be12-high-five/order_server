package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.dto.response.OrderAggregationDto;
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

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 1. [조회] 회원 주문 목록 (N+1 방지)
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.delivery LEFT JOIN FETCH o.orderReturn " +
            "WHERE o.userId = :userId AND o.deliveryStatus NOT IN (com.nhnacademy.order_server.entity.enums.DeliveryStatus.PAYMENT_WAITING) " +
            "ORDER BY o.orderDate DESC",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId")
    Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    // 2. [조회] 주문 상세 (아이템 FETCH JOIN)
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    Optional<Order> findByOrderKey(String orderKey);

    // 3. [에러 해결 1] 관리자용 상태별 주문 조회
    // AdminOrderServiceImpl line 42에서 호출하는 메서드
    Page<Order> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);

    // 4. [에러 해결 2] 배송 상태 및 날짜 기준 조회
    // AdminOrderServiceImpl line 124에서 호출하는 메서드
    List<Order> findAllByDeliveryStatusAndOrderDateBefore(DeliveryStatus status, LocalDateTime threshold);

    // 5. [배치/집계] 전 회원 대상 등급 산정용 (Network N+1 해결)
    @Query("SELECT new com.nhnacademy.order_server.dto.response.OrderAggregationDto(" +
            "o.userId, SUM(CAST(o.paymentAmount AS long))) " +
            "FROM Order o " +
            "WHERE o.orderDate BETWEEN :startDate AND :endDate " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED " +
            "AND o.userId IS NOT NULL " +
            "GROUP BY o.userId")
    List<OrderAggregationDto> findOrderAggregations(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    // 6. [집계] 특정 회원의 구매 확정 총액 조회
    @Query("SELECT SUM(CAST(o.paymentAmount AS long)) FROM Order o " +
            "WHERE o.userId = :userId AND o.orderDate >= :since " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED")
    Long sumPaymentAmountByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // 7. [기타 조회] 기간별/상태별 상세 조회들 (스케줄러용)
    Page<Order> findByUserIdAndOrderDateAfter(Long userId, LocalDateTime startDate, Pageable pageable);
    List<Order> findByDeliveryStatusAndOrderDateBefore(DeliveryStatus status, LocalDateTime threshold);
    List<Order> findByDeliveryStatusAndDelivery_ActualShipDateBefore(DeliveryStatus status, LocalDateTime threshold);
    List<Order> findByDeliveryStatusAndDelivery_ActualCompletionDateBefore(DeliveryStatus status, LocalDateTime threshold);

    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM Order o JOIN o.orderItems oi " +
            "WHERE o.userId = :userId AND oi.bookId = :bookId AND o.deliveryStatus = 'PURCHASE_CONFIRMED'")
    boolean hasPurchasedBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    @Query("SELECT o.userId, " +
            "SUM(CAST(o.paymentAmount AS long) - COALESCE(o.deliveryFee, 0) - COALESCE(o.wrappingFee, 0)) " +
            "FROM Order o " +
            "WHERE o.userId IN :userIds " +
            "AND o.orderDate >= :since " +
            "AND o.deliveryStatus = com.nhnacademy.order_server.entity.enums.DeliveryStatus.PURCHASE_CONFIRMED " +
            "GROUP BY o.userId")
    List<Object[]> sumPaymentAmountByUserIds(@Param("userIds") List<Long> userIds,
                                             @Param("since") LocalDateTime since);

}