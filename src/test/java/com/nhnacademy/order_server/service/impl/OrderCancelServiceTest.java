package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    @InjectMocks
    private OrderCancelService orderCancelService;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentClient paymentClient;
    @Mock private MemberClient memberClient;
    @Mock private CouponClient couponClient;
    @Mock private BookClient bookClient;

    @Test
    @DisplayName("성공: 배송 준비 중(PREPARING) 취소 시 재고 복구(restore) 및 결제 취소")
    void cancel_Preparing() {
        // Given
        Long orderId = 1L;
        Order order = Order.builder()
                .id(orderId)
                .userId(100L)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .paymentKey("pg_key")
                .paymentAmount(10000)
                .build();
        order.addOrderItem(OrderItem.builder().bookId(10L).quantity(1).build());

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // When
        orderCancelService.cancelOrderTransactional(orderId);

        // Then
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);

        // 결제 취소 호출 확인
        verify(paymentClient).cancelPayment(eq("pg_key"), any());
        // 재고 '복구(restore)' 호출 확인 (준비 중일 땐 아예 뺐던걸 다시 채워야 함)
        verify(bookClient).restoreStock(anyList(), any());
    }

    @Test
    @DisplayName("성공: 결제 대기(PAYMENT_WAITING) 취소 시 재고 선점 해제(release)")
    void cancel_PaymentWaiting() {
        // Given
        Long orderId = 1L;
        Order order = Order.builder()
                .id(orderId)
                .userId(100L)
                .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                .orderKey("key")
                .build();
        order.addOrderItem(OrderItem.builder().bookId(10L).quantity(1).build());

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // When
        orderCancelService.cancelOrderTransactional(orderId);

        // Then
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);

        // 재고 '해제(release)' 호출 확인 (아직 안 뺐으니 잡고 있던 것만 놓음)
        verify(bookClient).releaseHeldStock(anyList(), eq("key"));
    }
}