package com.nhnacademy.order_server.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberCouponCancelRequest {
    private Long couponId; // 취소(복구)할 사용자 쿠폰 ID
    private Long orderId;
}