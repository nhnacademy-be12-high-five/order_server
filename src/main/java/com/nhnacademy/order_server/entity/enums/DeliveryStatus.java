package com.nhnacademy.order_server.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {
    PAYMENT_WAITING("결제 대기"),
    PREPARING("배송 준비 중"),
    DELIVERING("배송 중"),
    DELIVERY_COMPLETED("배송 완료"),
    PURCHASE_CONFIRMED("구매 확정"),

    CANCELED("주문 취소"),

    RETURN_REQUESTED("반품 요청"),
    RETURN_COMPLETED("반품 완료");

    private final String description;
}