package com.nhnacademy.order_server.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "TEAM5-MEMBER-SERVER", contextId = "cartClient")
public interface CartClient {

    @DeleteMapping("/api/cart/items")
    void clearCart(@RequestHeader("X-USER-ID") Long memberId);
}