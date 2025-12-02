package com.nhnacademy.order_server.dto.request;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "배송비 정책 등록/수정 요청")
public class DeliveryPolicyRequest {

    @NotNull
    @Schema(description = "기본 배송비", example ="5000")
    private Integer standardShippingFee;

    @NotNull
    @Schema(description = "무료 배송 최소 주문 금액",example = "30000")
    private Integer minOrderAmount;

    public DeliveryPolicy toEntity() {
        return new DeliveryPolicy(
                this.minOrderAmount,
                this.standardShippingFee
        );
    }
}
