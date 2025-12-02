package com.nhnacademy.order_server.dto.request;

import com.nhnacademy.order_server.entity.Wrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "포장지 등록/수정 요청")
public class WrapperRegisterRequest {

    @NotBlank
    @Schema(description = "포장지 이름", example = "크리스마스 선물 포장")
    private String wrapperName;

    @NotNull
    @Schema(description = "포장지 가격", example = "1000")
    private Integer wrapperPrice;

    public Wrapper toEntity() {
        return new Wrapper(
                this.wrapperName,
                this.wrapperPrice,
                true
        );
    }
}
