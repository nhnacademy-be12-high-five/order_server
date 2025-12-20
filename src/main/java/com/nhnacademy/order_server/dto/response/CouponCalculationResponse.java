package com.nhnacademy.order_server.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CouponCalculationResponse {

    private Long discountAmount;
    private Long finalPrice;
}