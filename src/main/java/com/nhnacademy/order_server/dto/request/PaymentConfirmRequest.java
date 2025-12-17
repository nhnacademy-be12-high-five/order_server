package com.nhnacademy.order_server.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    private String paymentKey;
    private String orderKey;
    private Integer amount;
    private String paymentMethod;
}