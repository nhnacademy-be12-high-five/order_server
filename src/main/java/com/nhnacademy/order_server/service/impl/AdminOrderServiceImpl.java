package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.MemberCouponCancelRequest;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.request.PointEarnRequest;
import com.nhnacademy.order_server.dto.request.PointTransactionRequest;
import com.nhnacademy.order_server.dto.request.StockRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import com.nhnacademy.order_server.service.AdminOrderService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final MemberClient memberClient;
    private final CouponClient couponClient;
    private final BookClient bookClient;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Pageable pageable, String status) {
        if (status != null && !status.isEmpty()) {
            try {
                DeliveryStatus deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
                return orderRepository.findByDeliveryStatus(deliveryStatus, pageable)
                        .map(OrderResponse::from);
            } catch (IllegalArgumentException e) {
                throw new OrderException(OrderErrorCode.INVALID_REQUEST);
            }
        }

        return orderRepository.findAll(pageable)
                .map(OrderResponse::from);
    }

    @Override
    public void updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        DeliveryStatus newStatus;
        try {
            newStatus = DeliveryStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new OrderException(OrderErrorCode.INVALID_REQUEST);
        }

        if (newStatus == DeliveryStatus.RETURN_COMPLETED) {
            // 해당 주문의 반품 요청 정보를 찾음
            OrderReturn orderReturn = orderReturnRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new OrderException(OrderErrorCode.RETURN_NOT_FOUND));

            // 기존에 만들어둔 환불 승인 로직(approveReturn)을 호출
            approveReturn(order, orderReturn);

            // approveReturn 내부에서 status 업데이트를 하므로 여기서 리턴
            return;
        }

        // 1. 배송 중 (DELIVERING)
        if (newStatus == DeliveryStatus.DELIVERING) {
            if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                throw new OrderException(OrderErrorCode.INVALID_REQUEST);
            }

            if (order.getDelivery() != null) {
                order.getDelivery().startDelivery(request.getTrackingNumber());
            }
        }
        // 2. 배송 완료 (DELIVERY_COMPLETED) [변경됨]
        else if (newStatus == DeliveryStatus.DELIVERY_COMPLETED) {
            if (order.getDelivery() != null) {
                order.getDelivery().completeDelivery();
            }
        }
        // 3. 구매 확정 (PURCHASE_CONFIRMED) [추가됨]
        else if (newStatus == DeliveryStatus.PURCHASE_CONFIRMED) {
            // 배송 완료 상태에서만 구매 확정 가능하도록 제약
            if (order.getDeliveryStatus() != DeliveryStatus.DELIVERY_COMPLETED) {
                throw new OrderException(OrderErrorCode.INVALID_REQUEST); // "배송 완료된 주문만 구매 확정할 수 있습니다."
            }
            // (옵션) 여기서 포인트 적립 로직을 호출하거나, OrderServiceImpl의 confirmPurchase 로직을 재사용할 수 있음
        }

        order.updateStatus(newStatus);
    }

    @Override
    public void processReturn(Long returnId, boolean isApproved) {
        OrderReturn orderReturn = orderReturnRepository.findByIdWithOrder(returnId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.RETURN_NOT_FOUND));

        Order order = orderReturn.getOrder();

        if (isApproved) {
            approveReturn(order, orderReturn);
        } else {
            rejectReturn(order);
        }
    }

    @Override
    @Transactional
    public void completeOldDeliveries() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);

        List<Order> deliveringOrders = orderRepository.findAllByDeliveryStatusAndOrderDateBefore(
                DeliveryStatus.DELIVERING, threshold);

        for (Order order : deliveringOrders) {
            order.updateStatus(DeliveryStatus.DELIVERY_COMPLETED);
        }
    }

    private void approveReturn(Order order, OrderReturn orderReturn) {
        // 1. 결제 금액(현금/카드)을 포인트로 환불 (PG 취소 X -> 포인트 적립 O)
        int refundAmount = orderReturn.getRefundAmount(); // 반품비 제외된 최종 환불액

        if (refundAmount > 0) {
            try {
                PointEarnRequest earnRequest = PointEarnRequest.builder()
                        .memberId(order.getUserId())
                        .eventType("EARN_REFUND")
                        .pureAmount(refundAmount)
                        .orderId(order.getId())
                        .build();

                memberClient.earnPoint(earnRequest);

                log.info("반품 환불금 포인트 적립 완료: userId={}, amount={}", order.getUserId(), refundAmount);

            } catch (Exception e) {
                log.error("반품 포인트 적립 실패: userId={}, amount={}", order.getUserId(), refundAmount);
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 2. 사용했던 포인트 복구 (주문 시 포인트를 썼다면)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                PointTransactionRequest revertRequest = new PointTransactionRequest(
                        order.getUserId(),
                        (long) order.getPointDiscount(),
                        order.getId()
                );
                // [수정] revertPoint 대신 새로 만든 revertPointForReturn 호출!
                memberClient.revertPointForReturn(revertRequest);
            } catch (Exception e) {
                log.error("사용 포인트 복구 실패: userId={}", order.getUserId());
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 적립된 포인트 회수 (구매 확정으로 받은 포인트가 있다면)
        if (order.getEarnedPoint() != null && order.getEarnedPoint() > 0) {
            try {
                memberClient.deductPoint(order.getUserId(), order.getEarnedPoint(), order.getId());
            } catch (Exception e) {
                log.error("적립 포인트 회수 실패: userId={}", order.getUserId());
            }
        }

        // 쿠폰 복구
        if (order.getCouponId() != null) {
            try {
                MemberCouponCancelRequest cancelReq =
                        new MemberCouponCancelRequest(order.getCouponId(), order.getId());

                couponClient.cancelCouponUsage(order.getUserId(), cancelReq);
                log.info("반품으로 인한 쿠폰 복구 완료: couponId={}", order.getCouponId());

            } catch (Exception e) {
                // 쿠폰 복구 실패는 환불 전체를 막을 정도는 아님 -> 로그 남기고 진행
                log.error("쿠폰 복구 실패 (수동 복구 필요): couponId={}, error={}", order.getCouponId(), e.getMessage());
            }
        }

        try {
            List<StockRequest> restoreRequests = order.getOrderItems().stream()
                    .map(item -> new StockRequest(item.getBookId(), item.getQuantity()))
                    .toList();

            // 키 충돌 방지 및 구분을 위해 "-return" 접미사 사용 추천
            String idempotencyKey = order.getId() + "-return";

            bookClient.restoreStock(restoreRequests, idempotencyKey);
            log.info("반품 재고 복구 완료: orderId={}", order.getId());

        } catch (Exception e) {
            // 재고 서버가 죽어서 복구가 안 되면, 반품 승인 자체를 롤백해야 데이터가 꼬이지 않음
            log.error("재고 복구 실패 (반품 승인 중): OrderID={}", order.getId(), e);
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        // 4. 상태 변경
        order.updateStatus(DeliveryStatus.RETURN_COMPLETED);
    }

    private void rejectReturn(Order order) {
        // 반품 거절 시: 배송 완료 상태로 원복 (COMPLETED -> DELIVERY_COMPLETED) [변경됨]
        // 상황에 따라 구매 확정(PURCHASE_CONFIRMED)으로 돌려야 할 수도 있음 (정책 결정 필요)

        order.updateStatus(DeliveryStatus.DELIVERY_COMPLETED);
    }


}