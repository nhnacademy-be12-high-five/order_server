package com.nhnacademy.order_server.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class DeliveryPolicyResponse {
    private Integer id;
    private Integer standardShippingFee;
    private Integer minOrderAmount;
    private Boolean isActive;
    private LocalDateTime effectiveDate;
}
