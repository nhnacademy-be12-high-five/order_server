package com.nhnacademy.order_server.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "coupon-service", url = "${coupon.service.url}")
public interface CouponClient {

    @PostMapping("/api/coupons/{couponId}/calculate-discount")
    Integer calculateDiscount(@PathVariable("couponId") Long couponId, @RequestParam("amount") Integer totalAmount);

    @PostMapping("/api/coupons/{couponId}/use-confirm")
    void useCoupon(@PathVariable("couponId") Long couponId);

    @PostMapping("/api/coupons/{couponId}/cancel-use")
    void cancelCouponUsage(@PathVariable("couponId") Long couponId);
}