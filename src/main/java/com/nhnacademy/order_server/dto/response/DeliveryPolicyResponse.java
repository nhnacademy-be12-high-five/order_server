package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPolicyResponse {
    private Long id;
    private Integer standardShippingFee;
    private Integer minOrderAmount;
    private Boolean isActive;
    private LocalDateTime effectiveDate;
    private Integer remoteAreaSurcharge;

    public static DeliveryPolicyResponse from(DeliveryPolicy policy) {
        return DeliveryPolicyResponse.builder()
                .id(policy.getId())
                .standardShippingFee(policy.getStandardShippingFee())
                .minOrderAmount(policy.getMinOrderAmount())
                .isActive(policy.getIsActive())
                .effectiveDate(policy.getEffectiveDate())
                .remoteAreaSurcharge(policy.getRemoteAreaSurcharge())
                .build();
    }
}
