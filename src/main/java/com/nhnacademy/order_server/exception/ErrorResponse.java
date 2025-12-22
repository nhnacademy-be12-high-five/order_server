package com.nhnacademy.order_server.exception;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();
    private final int status;
    private final String error;
    private final String message;

    public static ErrorResponse of(OrderErrorCode errorCode) {
        return ErrorResponse.builder()
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .message(errorCode.getMessage())
                .build();
    }
}