package com.nhnacademy.order_server.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCouponResponse {
    private Long id;
    private Long userId;
    private Long couponId;
    private String couponName;

    private String status;

    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiredAt;
    private Long orderId;
    private Long discountValue;
    private String discountType;
    private String condition;
}