package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.CouponCalculationRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.response.CouponCalculationResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock private OrderRepository orderRepository;
    @Mock private WrapperRepository wrapperRepository;
    @Mock private DeliveryService deliveryService;
    @Mock private BookClient bookClient;
    @Mock private CouponClient couponClient;
    @Mock private MemberClient memberClient;
    @Mock private PaymentClient paymentClient;
    @Mock private CartClient cartClient;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DeliveryRepository deliveryRepository;

    private OrderCreateRequest request;
    private Wrapper mockWrapper;
    private BookInfoResponse mockBookInfo;
    private MemberGradeResponse mockGradeResponse;

    @BeforeEach
    void setUp() {
        mockWrapper = new Wrapper("선물 포장", 1000, true);
        ReflectionTestUtils.setField(mockWrapper, "id", 1L);

        mockBookInfo = new BookInfoResponse();
        ReflectionTestUtils.setField(mockBookInfo, "bookId", 1L);
        ReflectionTestUtils.setField(mockBookInfo, "price", 15000);
        ReflectionTestUtils.setField(mockBookInfo, "title", "자바의 정석");

        mockGradeResponse = new MemberGradeResponse();
        ReflectionTestUtils.setField(mockGradeResponse, "earnRate", 0.05);

        OrderItemRequest itemReq = new OrderItemRequest();
        ReflectionTestUtils.setField(itemReq, "bookId", 1L);
        ReflectionTestUtils.setField(itemReq, "quantity", 2);
        ReflectionTestUtils.setField(itemReq, "wrapperId", 1L);

        request = new OrderCreateRequest();
        ReflectionTestUtils.setField(request, "userId", 100L);
        ReflectionTestUtils.setField(request, "receiverName", "김철수");
        ReflectionTestUtils.setField(request, "receiverAddress", "서울시");
        ReflectionTestUtils.setField(request, "requestDeliveryDate", LocalDate.now().plusDays(2));
        ReflectionTestUtils.setField(request, "orderItems", List.of(itemReq));
        ReflectionTestUtils.setField(request, "usedPoint", 1000);
    }

    @Nested
    @DisplayName("1. 주문 생성 (CreateOrder)")
    class CreateOrderTest {

        @Test
        @DisplayName("성공: 회원 주문 (포인트 사용, 쿠폰 사용)")
        void success_Member() {
            ReflectionTestUtils.setField(request, "couponId", 10L);

            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));
            given(deliveryService.calculateDeliveryFee(anyInt(), anyString())).willReturn(3000);

            CouponCalculationResponse couponRes = new CouponCalculationResponse(2000L, 28000L);
            given(couponClient.calculateCoupon(anyLong(), any(CouponCalculationRequest.class))).willReturn(couponRes);

            given(orderRepository.save(any(Order.class))).willAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 1L);
                return o;
            });

            OrderCreateResponse response = orderService.createOrder(request);

            assertThat(response.getOrderId()).isEqualTo(1L);
            verify(memberClient).reservePoint(eq(100L), eq(1000), eq(1L));
            verify(bookClient).holdStockBatch(anyList(), anyString());
            verify(cartClient).clearCart(100L);
        }

        @Test
        @DisplayName("성공: 비회원 주문")
        void success_Guest() {
            ReflectionTestUtils.setField(request, "userId", null);
            ReflectionTestUtils.setField(request, "orderPassword", "1234");
            ReflectionTestUtils.setField(request, "usedPoint", null);

            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));
            given(passwordEncoder.encode("1234")).willReturn("encodedPwd");

            given(orderRepository.save(any(Order.class))).willAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 2L);
                return o;
            });

            OrderCreateResponse response = orderService.createOrder(request);

            assertThat(response.getOrderId()).isEqualTo(2L);
            verify(memberClient, never()).reservePoint(anyLong(), anyInt(), anyLong());
            verify(passwordEncoder).encode("1234");
        }

        @Test
        @DisplayName("실패: 포인트 예약 실패 시 보상 트랜잭션(재고 롤백) 실행")
        void fail_CompensateTransaction() {
            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));

            given(orderRepository.save(any(Order.class))).willAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 1L);
                return o;
            });

            willThrow(new RuntimeException("Point Error"))
                    .given(memberClient).reservePoint(anyLong(), anyInt(), anyLong());

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class);

            verify(bookClient).releaseHeldStock(anyList(), anyString());
            verify(memberClient).cancelPoint(eq(100L), eq(1000), anyLong());
        }
    }

    @Nested
    @DisplayName("2. 결제 완료 메시지 처리 (ProcessPaymentSuccess)")
    class ProcessPaymentSuccessMessageTest {

        @Test
        @DisplayName("성공: PAYMENT_WAITING -> PREPARING 변경 및 리소스 확정")
        void success() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .paymentAmount(30000)
                    .orderKey("key-123") // [수정] orderKey 추가
                    .build();
            OrderItem item = OrderItem.builder().bookId(101L).quantity(1).build();
            order.addOrderItem(item);

            ReflectionTestUtils.setField(request, "usedPoint", 1000);
            ReflectionTestUtils.setField(order, "pointDiscount", 1000);
            ReflectionTestUtils.setField(order, "couponId", 10L);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                    .orderId(orderId)
                    .paymentKey("pg_key")
                    .totalAmount(30000L)
                    .build();

            orderService.processPaymentSuccessMessage(message);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PREPARING);
            assertThat(order.getPaymentKey()).isEqualTo("pg_key");

            verify(bookClient).confirmStockDeduction(anyList(), anyString()); // [수정] any() -> anyString() 가능
            verify(couponClient).useCoupon(any(), any());
            verify(memberClient).confirmPoint(any(), eq(1000), eq(orderId));
        }

        @Test
        @DisplayName("실패: 주문 금액 불일치")
        void fail_AmountMismatch() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .paymentAmount(50000)
                    .orderKey("key-123")
                    .build();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                    .orderId(orderId)
                    .totalAmount(30000L)
                    .build();

            assertThatThrownBy(() -> orderService.processPaymentSuccessMessage(message))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("성공: 멱등성 체크 (이미 PREPARING 이면 무시)")
        void success_Idempotency() {
            Long orderId = 1L;
            Order order = Order.builder().id(orderId).deliveryStatus(DeliveryStatus.PREPARING).build();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder().orderId(orderId).build();

            orderService.processPaymentSuccessMessage(message);

            verify(bookClient, never()).confirmStockDeduction(anyList(), any());
        }
    }

    @Nested
    @DisplayName("3. 주문 취소 (CancelOrder)")
    class CancelOrderTest {

        @Test
        @DisplayName("성공: 배송 준비 중(PREPARING) 취소 -> PG/포인트/쿠폰/재고 환불")
        void cancel_Preparing() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .userId(100L)
                    .deliveryStatus(DeliveryStatus.PREPARING)
                    .paymentKey("pg_key")
                    .paymentAmount(30000)
                    .pointDiscount(1000)
                    .couponId(10L)
                    .orderKey("order-key") // [수정] Key 추가
                    .build();
            order.addOrderItem(OrderItem.builder().bookId(101L).quantity(2).build());

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            orderService.cancelOrder(orderId);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);

            verify(paymentClient).cancelPayment(eq("pg_key"), any(PaymentCancelRequest.class));
            verify(memberClient).cancelPoint(eq(100L), eq(1000), eq(orderId));
            verify(couponClient).cancelCouponUsage(eq(100L), any());
            verify(bookClient).restoreStock(anyList(), anyString());
        }

        @Test
        @DisplayName("성공: 결제 대기 중(PAYMENT_WAITING) 취소 -> 포인트/재고 선점 해제")
        void cancel_PaymentWaiting() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .userId(100L)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .pointDiscount(1000)
                    .orderKey("order-key") // [중요 수정] null 방지
                    .build();
            order.addOrderItem(OrderItem.builder().bookId(101L).quantity(1).build());

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            orderService.cancelOrder(orderId);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);

            verify(paymentClient, never()).cancelPayment(anyString(), any());
            verify(memberClient).cancelPoint(eq(100L), eq(1000), eq(orderId));

            // 이제 orderKey가 null이 아니므로 anyString() 매처가 통과함
            verify(bookClient).releaseHeldStock(anyList(), anyString());
        }

        @Test
        @DisplayName("실패: 취소 불가능한 상태 (DELIVERING)")
        void fail_NotCancelable() {
            Long orderId = 1L;
            Order order = Order.builder().id(orderId).deliveryStatus(DeliveryStatus.DELIVERING).build();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CANNOT_CANCEL_ORDER);
        }
    }

    @Nested
    @DisplayName("4. 스케줄러: 만료 주문 취소 (CancelExpiredOrders)")
    class CancelExpiredOrdersTest {

        @Test
        @DisplayName("성공: 만료된 주문 조회 후 일괄 취소")
        void success() {
            // given
            Order order1 = Order.builder().id(1L)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .userId(100L).pointDiscount(100)
                    .orderKey("key-1") // [중요 수정] Key 추가
                    .build();
            order1.addOrderItem(OrderItem.builder().bookId(101L).quantity(1).build());

            Order order2 = Order.builder().id(2L)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .userId(101L)
                    .orderKey("key-2") // [중요 수정] Key 추가
                    .build();
            order2.addOrderItem(OrderItem.builder().bookId(102L).quantity(1).build());

            given(orderRepository.findByDeliveryStatusAndOrderDateBefore(eq(DeliveryStatus.PAYMENT_WAITING), any(LocalDateTime.class)))
                    .willReturn(List.of(order1, order2));

            // when
            orderService.cancelExpiredOrders();

            // then
            assertThat(order1.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            assertThat(order2.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);

            // [검증] orderKey가 null이 아니므로 anyString() 통과
            verify(bookClient, times(2)).releaseHeldStock(anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("5. 단순 조회 (Getters)")
    class GettersTest {
        @Test
        @DisplayName("내 주문 목록 조회")
        void getMyOrders() {
            Long userId = 100L;
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).build();
            given(orderRepository.findAllByUserId(userId, pageable)).willReturn(new PageImpl<>(List.of(order)));

            var result = orderService.getMyOrders(userId, pageable);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("결제 검증 정보 조회")
        void getValidationInfo() {
            String orderKey = "key";
            Order order = Order.builder().id(1L).orderKey(orderKey).paymentAmount(100).build();
            given(orderRepository.findByOrderKey(orderKey)).willReturn(Optional.of(order));

            OrderValidationInfoResponse res = orderService.getValidationInfo(orderKey);
            assertThat(res.getPaymentAmount()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("6. 기간별 주문 조회")
    class GetPeriodOrdersTest {
        @Test
        @DisplayName("최근 3개월 주문 조회 성공")
        void getMyOrdersLast3Months() {
            Long userId = 100L;
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED).build();

            given(orderRepository.findByUserIdAndOrderDateAfter(eq(userId), any(LocalDateTime.class), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(order)));

            var result = orderService.getMyOrdersLast3Months(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findByUserIdAndOrderDateAfter(eq(userId), any(LocalDateTime.class), eq(pageable));
        }
    }
}