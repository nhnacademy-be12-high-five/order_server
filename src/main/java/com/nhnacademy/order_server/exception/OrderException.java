package com.nhnacademy.order_server.exception;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {

    public OrderException(OrderErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    private final OrderErrorCode errorCode;

}