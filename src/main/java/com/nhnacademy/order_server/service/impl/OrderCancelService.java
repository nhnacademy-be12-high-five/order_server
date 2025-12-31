package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.request.MemberCouponCancelRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.request.StockRequest;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final MemberClient memberClient;
    private final CouponClient couponClient;
    private final BookClient bookClient;

    /**
     * 주문 취소 트랜잭션 (기존 cancelOrderTransactional 로직)
     * REQUIRES_NEW: 기존 트랜잭션과 무관하게 항상 새로운 트랜잭션으로 실행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelOrderTransactional(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!isCancelable(order.getDeliveryStatus())) {
            throw new OrderException(OrderErrorCode.CANNOT_CANCEL_ORDER);
        }

        if (order.getDeliveryStatus() == DeliveryStatus.PREPARING) {
            processPreparingOrderCancellation(order);
        } else {
            processPaymentWaitingOrderCancellation(order);
        }

        order.updateStatus(DeliveryStatus.CANCELED);
    }

    private boolean isCancelable(DeliveryStatus status) {
        return status == DeliveryStatus.PREPARING || status == DeliveryStatus.PAYMENT_WAITING;
    }

    private void processPreparingOrderCancellation(Order o) {
        if (o.getPaymentKey() != null) {
            paymentClient.cancelPayment(o.getPaymentKey(), new PaymentCancelRequest("취소", o.getPaymentAmount()));
        }
        if (o.getPointDiscount() != null && o.getPointDiscount() > 0) {
            memberClient.cancelPoint(o.getUserId(), o.getPointDiscount(), o.getId());
        }
        if (o.getCouponId() != null) {
            couponClient.cancelCouponUsage(o.getUserId(), new MemberCouponCancelRequest(o.getCouponId(), o.getId()));
        }
        // 재고 복구 (Restore)
        bookClient.restoreStock(
                o.getOrderItems().stream()
                        .map(i -> new StockRequest(i.getBookId(), i.getQuantity()))
                        .toList(),
                o.getId() + "-restore"
        );
    }

    private void processPaymentWaitingOrderCancellation(Order o) {
        if (o.getPointDiscount() != null && o.getPointDiscount() > 0) {
            memberClient.cancelPoint(o.getUserId(), o.getPointDiscount(), o.getId());
        }
        // 재고 선점 해제 (Release)
        bookClient.releaseHeldStock(
                o.getOrderItems().stream().map(OrderItem::getBookId).toList(),
                o.getOrderKey()
        );
    }
}