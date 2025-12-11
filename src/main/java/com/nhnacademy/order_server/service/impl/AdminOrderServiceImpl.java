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
    private final PaymentClient paymentClient; // [추가] 결제 서비스 클라이언트 주입

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

        // [핵심] 배송 시작(DELIVERING) 시점에만 송장 번호 필수 체크
        if (newStatus == DeliveryStatus.DELIVERING) {
            // 송장 번호 유효성 검증 (빈 문자열 체크)
            if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                throw new OrderException(OrderErrorCode.INVALID_REQUEST); // "운송장 번호는 필수입니다" 등의 메시지 필요
            }

            // 배송 정보 업데이트 (Entity 메서드 호출)
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
                // reservePoint: 사용했던 포인트를 유저에게 다시 적립(환불)
                memberClient.reservePoint(order.getUserId(), order.getPointDiscount());
            } catch (Exception e) {
                log.error("포인트 환불 연동 실패: userId={}, amount={}", order.getUserId(), order.getPointDiscount());
                // 포인트 서버 오류 시 전체 로직 롤백을 위해 예외 발생
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 적립된 포인트 회수 (구매 확정으로 지급된 포인트 차감)
        if (order.getEarnedPoint() != null && order.getEarnedPoint() > 0) {
            try {
                // deductPoint: 지급된 포인트 회수
                memberClient.deductPoint(order.getUserId(), order.getEarnedPoint());
            } catch (Exception e) {
                // 이미 사용해서 잔액이 부족한 경우 등 실패할 수 있음.
                // 정책에 따라 예외를 던지지 않고 로그만 남기고 진행 (고객 귀책이 아니거나, 마이너스 포인트 허용 정책 등에 따라 다름)
                log.error("적립 포인트 회수 실패: userId={}, amount={}", order.getUserId(), order.getEarnedPoint());
            }
        }

        // 4. [구현됨] 결제 금액(PG) 환불 로직
        int refundAmount = orderReturn.getRefundAmount();

        // 환불할 금액이 있고, PG사 결제 키가 존재하는 경우 실행
        if (refundAmount > 0 && order.getPaymentKey() != null) {
            try {
                PaymentCancelRequest cancelRequest = new PaymentCancelRequest("관리자 반품 승인", refundAmount);
                paymentClient.cancelPayment(order.getPaymentKey(), cancelRequest);
            } catch (Exception e) {
                log.error("PG 결제 취소 연동 실패: paymentKey={}, amount={}, error={}", order.getPaymentKey(), refundAmount, e.getMessage());
                // PG 환불 실패는 심각한 문제이므로 예외를 발생시켜 트랜잭션을 롤백해야 함
                throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
            }
        }
    }

    private void rejectReturn(Order order) {
        // 반품 거절 시, 상태를 다시 '배송 완료' 상태로 원복하여 정상 주문으로 처리
        order.updateStatus(DeliveryStatus.COMPLETED);
    }
}