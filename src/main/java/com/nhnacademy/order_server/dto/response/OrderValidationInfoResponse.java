package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderValidationInfoResponse {

    private Long orderId;
    private Integer paymentAmount;
    private String orderKey;
    private Long userId;

    public static OrderValidationInfoResponse from(Order order) {

        if (order.getPaymentAmount() == null) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATE);
        }

        return OrderValidationInfoResponse.builder()
                .orderId(order.getId())
                .paymentAmount(order.getPaymentAmount())
                .orderKey(order.getOrderKey())
                .userId(order.getUserId())
                .build();
    }
}