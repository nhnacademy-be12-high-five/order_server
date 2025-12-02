package com.nhnacademy.order_server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum OrderErrorCode {


    // 404 NOT_FOUND (리소스 없음)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    DELIVERY_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 정책을 찾을 수 없습니다."),
    WRAPPER_NOT_FOUND(HttpStatus.NOT_FOUND, "포장지를 찾을 수 없습니다."),

    // 400 BAD_REQUEST (비즈니스 로직 위반)
    CANNOT_CANCEL_ORDER(HttpStatus.BAD_REQUEST, "이미 배송이 시작되었거나 완료된 주문은 취소할 수 없습니다."),
    RETURN_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "반품이 불가능한 상태입니다."),
    RETURN_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "반품 가능 기한이 지났습니다."),
    ALREADY_RETURN_REQUESTED(HttpStatus.BAD_REQUEST, "이미 반품 처리가 진행 중인 주문입니다."),
    INVALID_RETURN_REASON(HttpStatus.BAD_REQUEST, "단순 변심 반품은 출고 후 10일 이내만 가능합니다.");

    private final HttpStatus status;
    private final String message;
}