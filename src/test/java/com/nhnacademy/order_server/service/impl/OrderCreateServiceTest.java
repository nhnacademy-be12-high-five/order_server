package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.OrderCalculationData;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {

    @InjectMocks
    private OrderCreateService orderCreateService;

    @Mock private OrderRepository orderRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private MemberClient memberClient;
    @Mock private CartClient cartClient;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("성공: 주문 생성 및 저장, 장바구니 삭제")
    void createOrderInTransaction_Success() {
        // Given
        OrderCreateRequest request = new OrderCreateRequest();
        ReflectionTestUtils.setField(request, "userId", 100L);
        ReflectionTestUtils.setField(request, "usedPoint", 1000);
        ReflectionTestUtils.setField(request, "orderItems", List.of(new OrderCreateRequest.OrderItemRequest()));

        // 계산 결과 DTO 준비
        OrderCalculationData orderData = OrderCalculationData.builder()
                .firstBookTitle("Java Book")
                .tempOrderItems(List.of(OrderItem.builder().bookId(1L).quantity(1).build()))
                .build();

        OrderCreateRequest.OrderCalculationResult result = OrderCreateRequest.OrderCalculationResult.builder()
                .paymentAmount(29000).build();

        given(orderRepository.save(any(Order.class))).willAnswer(i -> {
            Order o = i.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 1L); // ID 생성 시뮬레이션
            return o;
        });

        // When
        OrderCreateResponse response = orderCreateService.createOrderInTransaction(request, "key", orderData, result);

        // Then
        assertThat(response.getOrderId()).isEqualTo(1L);

        // 1. 주문 저장 검증
        verify(orderRepository).save(any(Order.class));
        // 2. 배송 정보 저장 검증
        verify(deliveryRepository).save(any());
        // 3. 포인트 가승인 요청 검증
        verify(memberClient).reservePoint(argThat(req ->
                req.getMemberId().equals(100L) &&
                        req.getAmount() == 1000 &&
                        req.getOrderId() == 1L
        ));
        // 4. 장바구니 비우기 검증
        verify(cartClient).clearCart((100L));
    }
}