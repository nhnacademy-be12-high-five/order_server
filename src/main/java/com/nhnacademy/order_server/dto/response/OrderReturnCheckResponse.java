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

    @Schema(description = "불가 사유 (가능하면 null)", example = "반품 기한 10일 경과")
    private String message;

    public static OrderReturnCheckResponse ofEligible(Integer estimatedRefundAmount, Integer estimatedReturnFee) {
        if (estimatedRefundAmount != null && estimatedRefundAmount < 0){
            throw new IllegalArgumentException("환불 금액은 음수일 수 없습니다.");
        }

        if (estimatedReturnFee != null && estimatedReturnFee < 0){
            throw new IllegalArgumentException("반품 배송비는 음수일 수 없습니다.");
        }

        return OrderReturnCheckResponse.builder()
                .isEligible(true)
                .estimatedRefundAmount(estimatedRefundAmount)
                .estimatedReturnFee(estimatedReturnFee)
                .message(null)
                .build();
    }

    public static OrderReturnCheckResponse ofIneligible(String message) {
        return OrderReturnCheckResponse.builder()
                .isEligible(false)
                .estimatedReturnFee(null)
                .estimatedRefundAmount(null)
                .message(message)
                .build();
    }
}