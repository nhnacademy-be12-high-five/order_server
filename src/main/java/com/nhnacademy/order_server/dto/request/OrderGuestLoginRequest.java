package com.nhnacademy.order_server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "비회원 주문 조회(로그인) 요청")
public class OrderGuestLoginRequest {

    @NotNull
    @Schema(description = "주문 번호 (ID)", example = "20250501")
    private Long orderId;

    @NotNull
    @Schema(description = "주문 비밀번호", example = "1234")
    private String password;
}