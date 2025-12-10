package com.nhnacademy.order_server.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "cart-service", url = "${cart.service.url}")
public interface CartClient {

    @DeleteMapping("/api/carts/users/{userId}")
    void clearCartByUserId(@PathVariable("userId") Long userId);
}