package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.request.PaymentConfirmRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "TEAM5-PAYMENT-SERVER")
public interface PaymentClient {

    @PostMapping("/api/payments/{paymentKey}/cancel")
    void cancelPayment(@PathVariable("paymentKey") String paymentKey, @RequestBody PaymentCancelRequest request);

    @PostMapping("/api/payments/confirm")
    void confirmPayment(@RequestBody PaymentConfirmRequest request);
}
