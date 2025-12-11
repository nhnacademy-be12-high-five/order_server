package com.nhnacademy.order_server.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelRequest {
    private String cancelReason;
    private Integer cancelAmount;
}