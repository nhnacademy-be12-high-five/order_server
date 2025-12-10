package com.nhnacademy.order_server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum OrderErrorCode {

    // 400 BAD_REQUEST (비즈니스 로직 위반, 클라이언트의 요청이 유효하지 않음)
    ORDER_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "비회원 주문 시 비밀번호는 필수입니다."),
    CANNOT_CANCEL_ORDER(HttpStatus.BAD_REQUEST, "이미 배송이 시작되었거나 완료된 주문은 취소할 수 없습니다."),
    RETURN_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "반품이 불가능한 상태입니다."),
    RETURN_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "반품 가능 기한이 지났습니다."),
    ALREADY_RETURN_REQUESTED(HttpStatus.BAD_REQUEST, "이미 반품 처리가 진행 중인 주문입니다."),
    INVALID_RETURN_REASON(HttpStatus.BAD_REQUEST, "단순 변심 반품은 출고 후 10일 이내만 가능합니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "상품의 재고가 부족합니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 요청입니다. 입력값을 확인해주세요."),
    NOT_ENOUGH_POINT(HttpStatus.BAD_REQUEST, "포인트가 부족합니다."),
    INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "유효하지 않은 주문 상태입니다."),

    // 404 NOT_FOUND (리소스 없음)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    DELIVERY_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 정책을 찾을 수 없습니다."),
    WRAPPER_NOT_FOUND(HttpStatus.NOT_FOUND, "포장지를 찾을 수 없습니다."),

    // 409 (리소스의 현재 상태와 충돌 (중복/상태오류))
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리가 완료된 주문입니다."),

    // 500 INTERNAL_SERVER_ERROR (서버 내부 오류 (외부 연동 실패 등)
    COUPON_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "쿠폰 서비스 연동 중 오류가 발생했습니다."),
    MEMBER_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "회원 서비스 연동 중 오류가 발생했습니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "외부 서비스 연동 중 오류가 발생했습니다."),
    DELIVERY_FEE_CALCULATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "배송비 계산 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}