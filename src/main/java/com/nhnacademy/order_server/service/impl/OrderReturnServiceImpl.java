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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReturnServiceImpl implements OrderReturnService {

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;

    private static final int RETURN_SHIPPING_FEE = 5000;

    @Override
    public OrderReturnCheckResponse checkReturnEligibility(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getDeliveryStatus() != DeliveryStatus.COMPLETED) {
            return OrderReturnCheckResponse.ofIneligible("배송이 완료되지 않은 주문입니다.");
        }

        if (order.getOrderReturn() != null) {
            return OrderReturnCheckResponse.ofIneligible("이미 반품 접수된 주문입니다.");
        }

        if (order.getDelivery() == null || order.getDelivery().getActualShipDate() == null) {
            return OrderReturnCheckResponse.ofIneligible("배송 정보를 확인할 수 없습니다.");
        }

        LocalDateTime shipmentDate = order.getDelivery().getActualShipDate();
        long daysPassed = ChronoUnit.DAYS.between(shipmentDate, LocalDateTime.now());

        if (daysPassed > 10) {
            return OrderReturnCheckResponse.ofIneligible("반품 가능 기한(10일)이 지났습니다.");
        }

        int returnFee = RETURN_SHIPPING_FEE;
        int paymentAmount = order.getPaymentAmount();
        int estimatedRefund = Math.max(paymentAmount - returnFee, 0);

        return OrderReturnCheckResponse.ofEligible(estimatedRefund, returnFee);
    }

    @Override
    @Transactional
    public void requestReturn(Long orderId, OrderReturnRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getDeliveryStatus() != DeliveryStatus.COMPLETED) {
            throw new OrderException(OrderErrorCode.RETURN_NOT_ELIGIBLE);
        }
        if (order.getOrderReturn() != null) {
            throw new OrderException(OrderErrorCode.ALREADY_RETURN_REQUESTED);
        }

        validateReturnPeriod(order, request.getReturnReason());

        int refundAmount = order.getPaymentAmount();
        if (request.getReturnReason() == ReturnReason.SIMPLE_CHANGE) {
            refundAmount -= RETURN_SHIPPING_FEE;
            if (refundAmount < 0) refundAmount = 0;
        }

        OrderReturn orderReturn = OrderReturn.builder()
                .order(order)
                .returnReason(request.getReturnReason())
                .description(request.getDescription())
                .refundAmount(refundAmount)
                .build();

        orderReturnRepository.save(orderReturn);
        order.updateStatus(DeliveryStatus.RETURN_REQUESTED);
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