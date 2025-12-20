package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.entity.enums.ReturnReason;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import com.nhnacademy.order_server.service.OrderReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReturnServiceImpl implements OrderReturnService {

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;

    private static final int RETURN_SHIPPING_FEE = 5000;

    @Override
    public OrderReturnCheckResponse checkReturnEligibility(Long orderId, ReturnReason returnReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // 1. 상태 검증: 배송 완료 상태여야 함
        if (order.getDeliveryStatus() == DeliveryStatus.PURCHASE_CONFIRMED) {
            return OrderReturnCheckResponse.ofIneligible("이미 구매 확정된 주문은 반품할 수 없습니다.");
        }
        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERY_COMPLETED) {
            return OrderReturnCheckResponse.ofIneligible("배송 완료 상태의 주문만 반품 신청이 가능합니다.");
        }

        // 2. 중복 신청 검증
        if (order.getOrderReturn() != null) {
            return OrderReturnCheckResponse.ofIneligible("이미 반품 접수된 주문입니다.");
        }

        // 3. 배송 정보 검증
        if (order.getDelivery() == null || order.getDelivery().getActualShipDate() == null) {
            return OrderReturnCheckResponse.ofIneligible("배송 정보를 확인할 수 없습니다.");
        }

        // 4. 기간 검증
        LocalDateTime shipmentDate = order.getDelivery().getActualShipDate();
        long daysPassed = ChronoUnit.DAYS.between(shipmentDate, LocalDateTime.now());

        int allowedDays = 30; // 기본 30일
        int estimatedFee = 0;

        // 단순 변심: 10일 이내, 반품비 발생
        if (returnReason == ReturnReason.SIMPLE_CHANGE) {
            allowedDays = 10;
            estimatedFee = RETURN_SHIPPING_FEE;
        } else if (returnReason != null) {
            // 귀책 사유 등: 30일 이내, 무료
            allowedDays = 30;
            estimatedFee = 0;
        }

        if (daysPassed > allowedDays) {
            return OrderReturnCheckResponse.ofIneligible("반품 가능 기한(" + allowedDays + "일)이 지났습니다.");
        }

        // 5. 환불 예정 금액 계산
        int paymentAmount = order.getPaymentAmount();
        int estimatedRefund = Math.max(paymentAmount - estimatedFee, 0);

        return OrderReturnCheckResponse.ofEligible(estimatedRefund, estimatedFee);
    }

    @Override
    @Transactional
    public void requestReturn(Long orderId, OrderReturnRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // [변경] 구매 확정 상태 체크 추가
        if (order.getDeliveryStatus() == DeliveryStatus.PURCHASE_CONFIRMED) {
            throw new OrderException(OrderErrorCode.ALREADY_PURCHASE_CONFIRMED); // 구매확정된 건 반품 불가
        }
        // [변경] COMPLETED -> DELIVERY_COMPLETED
        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERY_COMPLETED) {
            throw new OrderException(OrderErrorCode.RETURN_NOT_ELIGIBLE);
        }

        if (order.getOrderReturn() != null) {
            throw new OrderException(OrderErrorCode.ALREADY_RETURN_REQUESTED);
        }

        validateReturnPeriod(order, request.getReturnReason());

        int refundAmount = order.getPaymentAmount();
        int appliedReturnFee = 0;

        // 단순 변심일 경우 반품비 차감
        if (request.getReturnReason() == ReturnReason.SIMPLE_CHANGE) {
            appliedReturnFee = RETURN_SHIPPING_FEE;
            refundAmount -= appliedReturnFee;
            if (refundAmount < 0) refundAmount = 0;
        }

        OrderReturn orderReturn = OrderReturn.builder()
                .order(order)
                .returnReason(request.getReturnReason())
                .description(request.getDescription())
                .refundAmount(refundAmount)
                .returnShippingFee(appliedReturnFee)
                .build();

        orderReturnRepository.save(orderReturn);

        // 상태 변경: DELIVERY_COMPLETED -> RETURN_REQUESTED
        order.updateStatus(DeliveryStatus.RETURN_REQUESTED);

        log.info("반품 신청 완료: OrderID={}, Reason={}, RefundAmount={}", orderId, request.getReturnReason(), refundAmount);
    }

    private void validateReturnPeriod(Order order, ReturnReason reason) {
        if (order.getDelivery() == null || order.getDelivery().getActualShipDate() == null) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATE);
        }

        LocalDateTime shipmentDate = order.getDelivery().getActualShipDate();
        long daysPassed = ChronoUnit.DAYS.between(shipmentDate, LocalDateTime.now());

        if (reason == ReturnReason.SIMPLE_CHANGE) {
            if (daysPassed > 10) {
                throw new OrderException(OrderErrorCode.RETURN_PERIOD_EXPIRED);
            }
        } else {
            if (daysPassed > 30) {
                throw new OrderException(OrderErrorCode.RETURN_PERIOD_EXPIRED);
            }
        }
    }
}