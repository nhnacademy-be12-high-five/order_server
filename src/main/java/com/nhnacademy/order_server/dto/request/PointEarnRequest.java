package com.nhnacademy.order_server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PointEarnRequest { //
    @NotNull(message = "회원 아이디는 필수입니다")
    @Schema(description = "포인트 적립 될 회원 아이디", example = "1")
    private Long memberId;

    @NotNull(message = "적립 타입은 필수입니다")
    @Schema(description = "적립 타입", example = "EARN_ORDER, EARN_REVIEW, EARN_PHOTO_REVIEW, EARN_SIGNUP, EARN_REFUND, EARN_ADMIN, USE_ORDER, USE_ADMIN, REVERT_ORDER")
    private String eventType;

    @Schema(description = "적립 기준 금액 (순수 금액. 리뷰 적립일시 null)", example = "45000")
    private Integer pureAmount;

    @Schema(description = "적립 사유에 들어갈 주문 번호 (리뷰 적립일시 null)", example = "1")
    private Long orderId;
}