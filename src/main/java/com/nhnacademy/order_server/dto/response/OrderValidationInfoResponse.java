package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderValidationInfoResponse {

    private Long orderId;
    private Integer paymentAmount;
    private String orderKey;
    private Long userId;
    private Integer usedPoint;

    public static OrderValidationInfoResponse from(Order order) {
        return OrderValidationInfoResponse.builder()
                .orderId(order.getId())
                .paymentAmount(order.getPaymentAmount())
                .orderKey(order.getOrderKey())
                .userId(order.getUserId())
                .usedPoint(order.getPointDiscount() != null ? order.getPointDiscount() : 0)
                .build();
    }
}