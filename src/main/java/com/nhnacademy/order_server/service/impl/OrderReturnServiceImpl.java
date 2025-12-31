package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.entity.enums.ReturnReason;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import com.nhnacademy.order_server.service.OrderReturnService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 중복 신청 검증
        if (order.getOrderReturn() != null) {
            return OrderReturnCheckResponse.ofIneligible("이미 반품 접수된 주문입니다.");
        }

        // 배송 정보 검증
        if (order.getDelivery() == null || order.getDelivery().getActualShipDate() == null) {
            return OrderReturnCheckResponse.ofIneligible("배송 정보를 확인할 수 없습니다.");
        }

        // 기간 검증
        LocalDateTime shipmentDate = order.getDelivery().getActualShipDate();
        long daysPassed = ChronoUnit.DAYS.between(shipmentDate, LocalDateTime.now());

        int allowedDays = 30; // 기본 30일
        int estimatedFee = 0;

        // 단순 변심: 10일 이내, 반품비 발생
        if (returnReason == ReturnReason.SIMPLE_CHANGE) {
            allowedDays = 10;
            estimatedFee = RETURN_SHIPPING_FEE;
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

        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERY_COMPLETED
                && order.getDeliveryStatus() != DeliveryStatus.DELIVERING
                && order.getDeliveryStatus() != DeliveryStatus.PURCHASE_CONFIRMED) {
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
        // 상태 체크 (배송 완료 or 구매 확정)
        if (!EnumSet.of(DeliveryStatus.DELIVERY_COMPLETED, DeliveryStatus.PURCHASE_CONFIRMED)
                .contains(order.getDeliveryStatus())) {
            throw new OrderException(OrderErrorCode.INVALID_DELIVERY_STATE);
        }

        // 기준일: 배송 완료일 (없으면 출고일)
        Delivery delivery = order.getDelivery();
        if (delivery == null) {
            throw new OrderException(OrderErrorCode.INVALID_DELIVERY_STATE);
        }

        LocalDateTime baseDate = delivery.getActualCompletionDate();
        if (baseDate == null) {
            baseDate = delivery.getActualShipDate();
        }
        if (baseDate == null) {
            throw new OrderException(OrderErrorCode.INVALID_DELIVERY_STATE);
        }

        // 경과일 계산 (배송 완료일 ~ 오늘)
        long daysFromDelivery = java.time.temporal.ChronoUnit.DAYS.between(baseDate, LocalDateTime.now());

        // 단순변심
        if (reason == ReturnReason.SIMPLE_CHANGE) {
            // 구매 확정 시점을 모르니 그냥 배송 완료 후 10일 이내면 다 받기
            long limitDays = 10;

            if (daysFromDelivery > limitDays) {
                throw new OrderException(OrderErrorCode.RETURN_PERIOD_EXPIRED);
            }
        }
        // 그 외 사유 (상품 불량 등)
        else {
            // 배송 완료 후 30일 이내
            if (daysFromDelivery > 30) {
                throw new OrderException(OrderErrorCode.RETURN_PERIOD_EXPIRED);
            }
        }
    }
}