package com.nhnacademy.order_server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransactionRequest {
    @NotNull(message = "회원 아이디는 필수입니다")
    @Schema(description = "포인트 사용/환불 될 회원 아이디", example = "1")
    private Long memberId;

    @NotNull(message = "액수는 필수입니다")
    @Schema(description = "포인트 사용/환불 액수", example = "100")
    private Long amount;

    @NotNull(message = "주문 번호는 필수입니다")
    @Schema(description = "포인트 사용/환불 사유에 들어갈 주문 번호", example = "1")
    private Long orderId;
}
