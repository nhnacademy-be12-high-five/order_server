package com.nhnacademy.order_server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema
public class OrderReturnRequest {

    @NotNull
    @Schema(description = "반품 사유 (Enum)", example = "PRODUCT_DEFECT")
    private String returnReason;

    @Schema(description = "상세 사유", example = "인쇄 상태 불량.")
    private String detail;
}
