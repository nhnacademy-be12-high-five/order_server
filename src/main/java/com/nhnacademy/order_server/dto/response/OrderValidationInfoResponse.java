package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderValidationInfoResponse {
    private Long orderId;
    private Integer realAmount;
    private String orderKey;
    private Long memberId;

    public static OrderValidationInfoResponse from(Order order) {
        return OrderValidationInfoResponse.builder()
                .orderId(order.getId())
                .realAmount(order.getPaymentAmount())
                .orderKey(order.getOrderKey())
                .memberId(order.getUserId())
                .build();
    }
}