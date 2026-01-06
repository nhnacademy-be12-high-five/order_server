package com.nhnacademy.order_server.dto.response.external;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointBalanceResponse {
    @Schema(description = "포인트 잔액 조회할 유저 아이디", example = "1")
    private Long memberId;
    @Schema(description = "포인트 잔액", example = "2000")
    private Long currentPoint;
    @Schema(description = "누적 적립 포인트", example = "150000")
    private Long totalEarnedPoint;
}
