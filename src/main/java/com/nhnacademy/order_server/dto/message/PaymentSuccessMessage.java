package com.nhnacademy.order_server.dto.message;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessMessage {
    private Long orderId;
    private String paymentKey;
    private Long totalAmount;
    private LocalDateTime approvedAt;
}