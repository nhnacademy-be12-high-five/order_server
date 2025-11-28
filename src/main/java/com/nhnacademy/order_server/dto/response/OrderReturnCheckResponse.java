package com.nhnacademy.order_server.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "반품 가능 여부 확인 응답")
public class OrderReturnCheckResponse {

    @Schema(description = "반품 가능 여부")
    private boolean isEligible;

    @Schema(description = "예상 반품 배송비 (차감액)")
    private Integer estimatedReturnFee;

    @Schema(description = "예상 환불 금액")
    private Integer estimatedRefundAmount;

    @Schema(description = "불가 사유 (가능하면 null)", example = "반품 기한 30일 경과")
    private String message;
}