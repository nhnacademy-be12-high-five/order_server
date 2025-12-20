package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import com.nhnacademy.order_server.service.AdminOrderService;
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
    private final PaymentClient paymentClient;

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

    private void approveReturn(Order order, OrderReturn orderReturn) {
        // 1. 주문 상태 변경 (반품 완료: RETURN -> RETURN_COMPLETED) [변경됨]
        order.updateStatus(DeliveryStatus.RETURN_COMPLETED);

        // 2. 포인트 환불 처리 (결제 시 사용했던 포인트 돌려주기)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                // cancelPoint 호출 (orderId 포함)
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 환불 연동 실패: userId={}, amount={}", order.getUserId(), order.getPointDiscount());
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 적립된 포인트 회수 (구매 확정으로 지급된 포인트 차감)
        // 반품은 보통 구매 확정 전에 일어나지만, 확정 후 반품일 경우 포인트 회수 필요
        if (order.getEarnedPoint() != null && order.getEarnedPoint() > 0) {
            try {
                memberClient.deductPoint(order.getUserId(), order.getEarnedPoint());
            } catch (Exception e) {
                log.error("적립 포인트 회수 실패: userId={}, amount={}", order.getUserId(), order.getEarnedPoint());
            }
        }

        int refundAmount = orderReturn.getRefundAmount();

        if (refundAmount > 0 && order.getPaymentKey() != null) {
            try {
                PaymentCancelRequest cancelRequest = new PaymentCancelRequest("관리자 반품 승인", refundAmount);
                paymentClient.cancelPayment(order.getPaymentKey(), cancelRequest);
            } catch (Exception e) {
                log.error("PG 결제 취소 연동 실패: paymentKey={}, amount={}, error={}", order.getPaymentKey(), refundAmount, e.getMessage());
                throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
            }
        }
    }

    private void rejectReturn(Order order) {
        // 반품 거절 시: 배송 완료 상태로 원복 (COMPLETED -> DELIVERY_COMPLETED) [변경됨]
        // 상황에 따라 구매 확정(PURCHASE_CONFIRMED)으로 돌려야 할 수도 있음 (정책 결정 필요)

        order.updateStatus(DeliveryStatus.DELIVERY_COMPLETED);
    }
}