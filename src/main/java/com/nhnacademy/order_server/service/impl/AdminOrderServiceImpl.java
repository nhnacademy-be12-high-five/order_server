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

        // 배송 시작(DELIVERING) 시점에만 송장 번호 필수 체크
        if (newStatus == DeliveryStatus.DELIVERING) {
            if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                throw new OrderException(OrderErrorCode.INVALID_REQUEST);
            }

            if (order.getDelivery() != null) {
                order.getDelivery().startDelivery(request.getTrackingNumber());
            }
        }
        else if (newStatus == DeliveryStatus.COMPLETED) {
            if (order.getDelivery() != null) {
                order.getDelivery().completeDelivery();
            }
        }

        order.updateStatus(newStatus);
    }

    @Override
    public void processReturn(Long returnId, boolean isApproved) {
        // OrderReturn ID는 Order ID와 동일하게 매핑됨 (@MapsId)
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
        // 1. 주문 상태 변경 (반품 완료)
        order.updateStatus(DeliveryStatus.RETURN);

        // 2. 포인트 환불 처리 (결제 시 사용했던 포인트 돌려주기)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                // [수정 완료] cancelPoint 호출 시 orderId 전달
                // MemberClient의 cancelPoint 메서드 시그니처가 (userId, amount, orderId)로 수정되어 있어야 함
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 환불 연동 실패: userId={}, amount={}", order.getUserId(), order.getPointDiscount());
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 적립된 포인트 회수 (구매 확정으로 지급된 포인트 차감)
        if (order.getEarnedPoint() != null && order.getEarnedPoint() > 0) {
            try {
                // deductPoint: 지급된 포인트 회수
                // 관리자용 단순 차감이라면 orderId가 필요 없을 수 있으나, 만약 필요하다면 추가해야 함.
                // 현재 deductPoint는 (userId, amount)만 받는 것으로 가정
                memberClient.deductPoint(order.getUserId(), order.getEarnedPoint());
            } catch (Exception e) {
                // 이미 사용해서 잔액이 부족한 경우 등 실패할 수 있음.
                log.error("적립 포인트 회수 실패: userId={}, amount={}", order.getUserId(), order.getEarnedPoint());
            }
        }

        // 4. 결제 금액(PG) 환불 로직
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
        // 반품 거절 시, 상태를 다시 '배송 완료' 상태로 원복하여 정상 주문으로 처리
        order.updateStatus(DeliveryStatus.COMPLETED);
    }
}