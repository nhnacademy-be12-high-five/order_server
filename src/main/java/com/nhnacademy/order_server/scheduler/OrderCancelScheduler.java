package com.nhnacademy.order_server.scheduler;

import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelScheduler {

    private final OrderService orderService;


    @Scheduled(cron = "0 0/10 * * * *")
    public void runOrderAutoCancel() {
        log.info("[Scheduler] 결제 대기 만료 주문 정리 시작");
        orderService.cancelExpiredOrders();
        log.info("[Scheduler] 결제 대기 만료 주문 정리 종료");
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void runDailyOrderStatusUpdate() {
        log.info("[Scheduler] 자동 상태 변경 작업 시작");
        orderService.autoCompleteDelivery(); // 배송중 -> 배송완료
        orderService.autoConfirmPurchase();  // 배송완료 -> 구매확정
        log.info("[Scheduler] 자동 상태 변경 작업 종료");
    }
}