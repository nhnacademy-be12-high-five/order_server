package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCreateResponse {
    private Long orderId;
    private String orderKey;
    private String orderName;
    private Integer totalAmount;

    public static OrderCreateResponse from(Order order, String firstBookTitle, int totalItems) {
        String name = firstBookTitle;
        if (totalItems > 1) {
            name += " 외 " + (totalItems - 1) + "건";
        }

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderKey(order.getOrderKey())
                .orderName(name)
                .totalAmount(order.getPaymentAmount())
                .build();
    }
}