package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.request.CouponCalculationRequest;
import com.nhnacademy.order_server.dto.request.MemberCouponCancelRequest;
import com.nhnacademy.order_server.dto.request.MemberCouponUseRequest;
import com.nhnacademy.order_server.dto.response.CouponCalculationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "TEAM5-COUPON-SERVER", path = "/api/coupons")
public interface CouponClient {

    // 1. 쿠폰 할인 금액 계산
    @PostMapping("/calculate")
    CouponCalculationResponse calculateCoupon(
            @RequestHeader("X-USER-ID") Long memberId,
            @RequestBody CouponCalculationRequest requestDto
    );

    // 2. 쿠폰 사용 처리 (결제 성공 시 호출)
    @PostMapping("/use")
    void useCoupon(
            @RequestHeader("X-USER-ID") Long memberId,
            @RequestBody MemberCouponUseRequest requestDto
    );

    // 3. 쿠폰 사용 취소 (주문 취소/환불 시 호출)
    @PostMapping("/cancel")
    void cancelCouponUsage(
            @RequestHeader("X-USER-ID") Long memberId,
            @RequestBody MemberCouponCancelRequest requestDto
    );
}