package com.nhnacademy.order_server.dto.request;

import com.nhnacademy.order_server.entity.enums.ReturnReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema
public class OrderReturnRequest {

    @NotNull
    @Schema(description = "반품 사유 (Enum)", example = "PRODUCT_DEFECT")
    private ReturnReason returnReason;

    @Size(max = 500, message = "상세 사유는 500자를 초과할 수 없습니다.")
    @Schema(description = "상세 사유", example = "사이즈가 안 맞아요.")
    private String description;
}
