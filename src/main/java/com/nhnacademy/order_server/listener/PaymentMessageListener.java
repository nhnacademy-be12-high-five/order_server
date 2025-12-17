package com.nhnacademy.order_server.listener;

import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMessageListener {

    private final OrderService orderService;

    @RabbitListener(queues = "payment-success-queue")
    public void handlePaymentSuccess(PaymentSuccessMessage message) {
        log.info("[RabbitMQ] 결제 성공 메시지 수신: orderId={}, paymentKey={}", message.getOrderId(), message.getPaymentKey());

        try {
            orderService.processPaymentSuccessMessage(message);

            log.info("[RabbitMQ] 주문 후처리 성공: orderId={}", message.getOrderId());

        } catch (Exception e) {
            log.error("[RabbitMQ] 주문 후처리 중 오류 발생. 재시도 예정. orderId={}", message.getOrderId(), e);
            throw e;
        }
    }
}