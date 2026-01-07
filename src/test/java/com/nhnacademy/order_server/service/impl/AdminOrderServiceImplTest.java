package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @InjectMocks
    private AdminOrderServiceImpl adminOrderService;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderReturnRepository orderReturnRepository;
    @Mock private MemberClient memberClient;
    @Mock private CouponClient couponClient;
    @Mock private BookClient bookClient;

    private Order order;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(1L)
                .userId(100L)
                .paymentAmount(50000)
                .pointDiscount(1000)
                .earnedPoint(500)
                .couponId(10L)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();

        delivery = Delivery.builder().order(order).build();
        ReflectionTestUtils.setField(order, "delivery", delivery);

        // OrderItem 추가 (재고 복구 테스트용)
        OrderItem item = OrderItem.builder().bookId(1L).quantity(2).build();
        order.addOrderItem(item);
    }

    @Nested
    @DisplayName("1. 주문 목록 조회 테스트 (getOrders)")
    class GetOrdersTest {
        @Test
        @DisplayName("성공: 상태값이 없을 때 전체 조회")
        void getOrders_All() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));

            Page<OrderResponse> result = adminOrderService.getOrders(pageable, null);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findAll(pageable);
        }

        @Test
        @DisplayName("성공: 유효한 상태값으로 필터링 조회")
        void getOrders_WithStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findByDeliveryStatus(any(), any())).willReturn(new PageImpl<>(List.of(order)));

            Page<OrderResponse> result = adminOrderService.getOrders(pageable, "DELIVERING");

            assertThat(result).isNotNull();
            verify(orderRepository).findByDeliveryStatus((DeliveryStatus.DELIVERING), (pageable));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상태값 입력 시 예외 발생")
        void getOrders_InvalidStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            assertThatThrownBy(() -> adminOrderService.getOrders(pageable, "INVALID_STATUS"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("2. 주문 상태 변경 테스트 (updateOrderStatus)")
    class UpdateOrderStatusTest {

        @Test
        @DisplayName("성공: DELIVERING으로 변경 시 송장번호 등록 확인")
        void updateToDelivering() {
            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(req, "status", "DELIVERING");
            ReflectionTestUtils.setField(req, "trackingNumber", "12345");

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            adminOrderService.updateOrderStatus(1L, req);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERING);
            assertThat(delivery.getTrackingNumber()).isEqualTo("12345");
        }

        @Test
        @DisplayName("실패: DELIVERING 변경 시 송장번호 누락 예외")
        void updateToDelivering_Fail() {
            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(req, "status", "DELIVERING");
            ReflectionTestUtils.setField(req, "trackingNumber", ""); // 빈 송장번호

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> adminOrderService.updateOrderStatus(1L, req))
                    .isInstanceOf(OrderException.class);
        }

        @Test
        @DisplayName("성공: PURCHASE_CONFIRMED 변경 (DELIVERY_COMPLETED 상태일 때)")
        void updateToPurchaseConfirmed() {
            order.updateStatus(DeliveryStatus.DELIVERY_COMPLETED);
            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(req, "status", "PURCHASE_CONFIRMED");

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            adminOrderService.updateOrderStatus(1L, req);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
        }

        @Test
        @DisplayName("실패: DELIVERY_COMPLETED, DELIVERING, PREPARING이 아닌 상태에서 구매 확정 시도")
        void updateToPurchaseConfirmed_Fail() {
            order.updateStatus(DeliveryStatus.PAYMENT_WAITING);

            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(req, "status", "PURCHASE_CONFIRMED");

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> adminOrderService.updateOrderStatus(1L, req))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("3. 반품 승인 로직 테스트 (approveReturn)")
    class ApproveReturnTest {

        private OrderReturn orderReturn;

        @BeforeEach
        void setUp() {
            orderReturn = OrderReturn.builder()
                    .order(order)
                    .refundAmount(45000)
                    .build();
        }

        @Test
        @DisplayName("성공: 반품 승인 시 포인트 환불, 쿠폰 복구, 재고 복구 모두 실행")
        void approveReturn_Success() {
            order.updateStatus(DeliveryStatus.PURCHASE_CONFIRMED);

            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            adminOrderService.processReturn(1L, true);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_COMPLETED);

            // 1. 환불금 적립 (EARN_REFUND)
            verify(memberClient).createTransaction(argThat(req ->
                    "EARN_REFUND".equals(req.getPointEventType()) &&
                            req.getMemberId().equals(order.getUserId())
            ));

            // 2. 사용 포인트 복구 (USE_CANCEL_RETURN) - 이름 수정됨
            verify(memberClient).createTransaction(argThat(req ->
                    "USE_CANCEL_RETURN".equals(req.getPointEventType()) &&
                            req.getAmount() == 1000
            ));

            // 3. 적립 포인트 회수 (EARN_CANCEL_RETURN) - 이름 수정됨
            verify(memberClient).createTransaction(argThat(req ->
                    "EARN_CANCEL_RETURN".equals(req.getPointEventType()) &&
                            req.getAmount() == 0L // 서비스 코드에서 0L로 보내고 있음
            ));
            // 4. 쿠폰 복구 호출 확인
            verify(couponClient).cancelCouponUsage(eq(100L), any());
            // 5. 재고 복구 호출 확인
            verify(bookClient).restoreStock(anyList(), contains("-return"));
        }

        @Test
        @DisplayName("실패: 재고 서버 장애 시 반품 승인 실패 (예외 전파)")
        void approveReturn_Fail_BookClient() {
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));
            willThrow(new RuntimeException("API Error")).given(bookClient).restoreStock(any(), any());

            assertThatThrownBy(() -> adminOrderService.processReturn(1L, true))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    @Nested
    @DisplayName("4. 기타 행정 서비스 테스트")
    class AdminServiceMiscTest {

        @Test
        @DisplayName("반품 거절: 상태가 DELIVERY_COMPLETED로 원복됨")
        void rejectReturn() {
            OrderReturn orderReturn = OrderReturn.builder().order(order).build();
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            adminOrderService.processReturn(1L, false);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
        }

        @Test
        @DisplayName("스케줄러: 오래된 배송 중 주문 일괄 완료 처리")
        void completeOldDeliveries() {
            given(orderRepository.findAllByDeliveryStatusAndOrderDateBefore(any(), any()))
                    .willReturn(List.of(order));

            adminOrderService.completeOldDeliveries();

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
        }
    }
}