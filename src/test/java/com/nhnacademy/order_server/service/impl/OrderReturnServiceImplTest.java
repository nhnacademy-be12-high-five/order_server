package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.entity.enums.ReturnReason;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderReturnServiceImplTest {

    @InjectMocks
    private OrderReturnServiceImpl orderReturnService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    private Order order;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(1L)
                .userId(100L)
                .paymentAmount(50000)
                .deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED)
                .build();

        delivery = Delivery.builder().order(order).build();
        // 배송 완료일: 5일 전으로 설정
        ReflectionTestUtils.setField(delivery, "actualCompletionDate", LocalDateTime.now().minusDays(5));
        ReflectionTestUtils.setField(delivery, "actualShipDate", LocalDateTime.now().minusDays(7));
        ReflectionTestUtils.setField(order, "delivery", delivery);
    }

    @Nested
    @DisplayName("1. 반품 가능 여부 확인 (checkReturnEligibility)")
    class CheckReturnEligibilityTest {

        @Test
        @DisplayName("성공: 단순 변심 (10일 이내) - 반품비 차감")
        void success_SimpleChange() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnCheckResponse response = orderReturnService.checkReturnEligibility(1L, ReturnReason.SIMPLE_CHANGE);

            assertThat(response.isEligible()).isTrue();
            assertThat(response.getEstimatedReturnFee()).isEqualTo(5000); // 반품비
            assertThat(response.getEstimatedRefundAmount()).isEqualTo(45000); // 50000 - 5000
        }

        @Test
        @DisplayName("성공: 상품 불량 (30일 이내) - 전액 환불")
        void success_ProductDefect() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnCheckResponse response = orderReturnService.checkReturnEligibility(1L, ReturnReason.PRODUCT_DEFECT);

            assertThat(response.isEligible()).isTrue();
            assertThat(response.getEstimatedReturnFee()).isZero();
            assertThat(response.getEstimatedRefundAmount()).isEqualTo(50000);
        }

        @Test
        @DisplayName("불가: 이미 반품 접수된 경우")
        void fail_AlreadyReturned() {
            OrderReturn existingReturn = OrderReturn.builder().build();
            ReflectionTestUtils.setField(order, "orderReturn", existingReturn);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnCheckResponse response = orderReturnService.checkReturnEligibility(1L, ReturnReason.SIMPLE_CHANGE);

            assertThat(response.isEligible()).isFalse();
            assertThat(response.getMessage()).contains("이미 반품 접수된 주문");
        }

        @Test
        @DisplayName("불가: 반품 기한 초과 (단순 변심 10일)")
        void fail_PeriodExpired() {
            // 배송 완료 15일 경과
            ReflectionTestUtils.setField(delivery, "actualShipDate", LocalDateTime.now().minusDays(15));
            // 로직상 shipmentDate를 기준으로 체크하는 부분이 있어 실제 출고일도 조정

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnCheckResponse response = orderReturnService.checkReturnEligibility(1L, ReturnReason.SIMPLE_CHANGE);

            assertThat(response.isEligible()).isFalse();
            assertThat(response.getMessage()).contains("반품 가능 기한");
        }
    }

    @Nested
    @DisplayName("2. 반품 신청 (requestReturn)")
    class RequestReturnTest {

        @Test
        @DisplayName("성공: 반품 신청 완료 및 상태 변경")
        void success_Request() {
            OrderReturnRequest request = new OrderReturnRequest();
            ReflectionTestUtils.setField(request, "returnReason", ReturnReason.SIMPLE_CHANGE);
            ReflectionTestUtils.setField(request, "description", "마음에 안 들어요");

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            orderReturnService.requestReturn(1L, request);

            verify(orderReturnRepository).save(any(OrderReturn.class));
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_REQUESTED);
        }

        @Test
        @DisplayName("실패: 반품 불가능한 상태 (예: 배송 준비 중)")
        void fail_InvalidStatus() {
            order.updateStatus(DeliveryStatus.PREPARING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnRequest request = new OrderReturnRequest();

            assertThatThrownBy(() -> orderReturnService.requestReturn(1L, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.RETURN_NOT_ELIGIBLE);
        }

        @Test
        @DisplayName("실패: 반품 기한 만료 검증")
        void fail_PeriodValidation() {
            // 40일 경과
            ReflectionTestUtils.setField(delivery, "actualCompletionDate", LocalDateTime.now().minusDays(40));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            OrderReturnRequest request = new OrderReturnRequest();
            ReflectionTestUtils.setField(request, "returnReason", ReturnReason.PRODUCT_DEFECT);

            assertThatThrownBy(() -> orderReturnService.requestReturn(1L, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.RETURN_PERIOD_EXPIRED);
        }
    }
}