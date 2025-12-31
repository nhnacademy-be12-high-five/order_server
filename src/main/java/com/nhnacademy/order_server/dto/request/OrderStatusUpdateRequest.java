package com.nhnacademy.order_server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "주문 상태 변경 요청")
public class OrderStatusUpdateRequest {

    @NotNull
    @Schema(description = "변경할 상태 (WAITING, DELIVERING, COMPLETED ...)", example = "DELIVERING")
    private String status;

    @Schema(description = "운송장 번호 (배송 시작 시 필수)", example = "1234567890")
    private String trackingNumber;
}
