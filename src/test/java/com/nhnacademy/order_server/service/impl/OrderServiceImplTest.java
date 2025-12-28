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
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.CouponCalculationRequest;
import com.nhnacademy.order_server.dto.request.MemberCouponUseRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.response.CouponCalculationResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.Delivery;
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
import java.util.ArrayList;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private RabbitTemplate rabbitTemplate;

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

        OrderCreateRequest.OrderItemRequest itemReq = new OrderCreateRequest.OrderItemRequest();
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

        // Self-invocation 모킹
        ReflectionTestUtils.setField(orderService, "self", orderService);
    }

    @Nested
    @DisplayName("1. 주문 생성 (CreateOrder)")
    class CreateOrderTest {

        @Test
        @DisplayName("성공: 회원 주문")
        void success_Member() {
            ReflectionTestUtils.setField(request, "couponId", 10L);

            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));
            given(deliveryService.calculateDeliveryFee(anyInt(), anyString())).willReturn(3000);

            // 쿠폰 계산 Mock
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
            // 쿠폰 사용은 결제 성공 시점으로 옮겼으므로 여기서는 verify 하지 않음
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
        @DisplayName("성공: PAYMENT_WAITING -> PREPARING 변경 및 쿠폰 사용")
        void success() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .userId(100L) // userId 세팅 필수
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .paymentAmount(30000)
                    .orderKey("key-123")
                    .build();
            order.addOrderItem(OrderItem.builder().bookId(101L).quantity(1).build());

            ReflectionTestUtils.setField(order, "couponId", 10L);
            ReflectionTestUtils.setField(order, "pointDiscount", 1000);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                    .orderId(orderId)
                    .paymentKey("pg_key")
                    .totalAmount(30000L) // 주문 금액과 일치하게 세팅
                    .build();

            orderService.processPaymentSuccessMessage(message);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PREPARING);
            assertThat(order.getPaymentKey()).isEqualTo("pg_key");

            // 리팩토링된 시점에 맞게 호출 검증
            verify(couponClient).useCoupon(eq(100L), any(MemberCouponUseRequest.class));
            verify(bookClient).confirmStockDeduction(anyList(), eq("key-123"));
            verify(memberClient).confirmPoint(eq(100L), eq(1000), eq(orderId));
        }

        @Test
        @DisplayName("실패: 주문 금액 불일치")
        void fail_AmountMismatch() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .paymentAmount(50000) // DB에는 5만원
                    .build();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                    .orderId(orderId)
                    .totalAmount(30000L) // 실제 결제는 3만원 -> 에러 발생해야 함
                    .build();

            assertThatThrownBy(() -> orderService.processPaymentSuccessMessage(message))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }
        @Test
        @DisplayName("무시: 이미 처리된 주문(결제 대기 상태가 아님)은 메시지를 무시한다")
        void ignore_AlreadyProcessed() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.PREPARING) // 이미 결제 됨
                    .build();

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder().orderId(orderId).build();

            // When
            orderService.processPaymentSuccessMessage(message);

            // Then
            // 상태가 변하지 않았어야 함
            verify(couponClient, org.mockito.Mockito.never()).useCoupon(anyLong(), any());
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
                    .orderKey("order-key")
                    .build();
            order.addOrderItem(OrderItem.builder().bookId(101L).quantity(2).build());

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            orderService.cancelOrder(orderId);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            verify(paymentClient).cancelPayment(eq("pg_key"), any(PaymentCancelRequest.class));
            verify(bookClient).restoreStock(anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("4. 스케줄러 및 자동화")
    class SchedulerTest {

        @Test
        @DisplayName("성공: 만료된 결제 대기 주문 취소")
        void cancelExpiredOrders_Success() {
            Order order = Order.builder()
                    .id(1L).userId(100L).orderKey("key")
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .build();
            order.addOrderItem(OrderItem.builder().bookId(1L).quantity(1).build());

            given(orderRepository.findByDeliveryStatusAndOrderDateBefore(any(), any()))
                    .willReturn(List.of(order));

            orderService.cancelExpiredOrders();

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            verify(bookClient).releaseHeldStock(anyList(), eq("key"));
        }
    }

    @Nested
    @DisplayName("5. 조회 기능")
    class GettersTest {
        @Test
        @DisplayName("회원 주문 목록 페이징 조회")
        void getMyOrders() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAllByUserId(anyLong(), any()))
                    .willReturn(new PageImpl<>(new ArrayList<>()));

            orderService.getMyOrders(100L, pageable);
            verify(orderRepository).findAllByUserId(eq(100L), eq(pageable));
        }
    }
    @Nested
    @DisplayName("6. 구매 확정 (PurchaseConfirm)")
    class PurchaseConfirmTest {

        @Test
        @DisplayName("성공: 구매 확정 시 포인트 적립 메시지 발행 및 상태 변경")
        void success_Confirm() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .userId(100L)
                    .paymentAmount(10000)
                    .deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED)
                    .build();

            // 배송 정보가 있지만 완료일이 없는 경우 테스트
            Delivery delivery = Delivery.builder().order(order).build();
            ReflectionTestUtils.setField(order, "delivery", delivery);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // When
            orderService.purchaseConfirm(orderId);

            // Then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
            assertThat(delivery.getActualCompletionDate()).isNotNull(); // 배송 완료일 자동 기입 확인

            // 포인트 적립 큐 메시지 전송 확인
            verify(rabbitTemplate).convertAndSend(eq("point-queue"), any(com.nhnacademy.order_server.dto.request.PointEarnRequest.class));
        }

        @Test
        @DisplayName("실패: 이미 구매 확정된 주문은 예외 발생")
        void fail_AlreadyConfirmed() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.PURCHASE_CONFIRMED)
                    .build();

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // When & Then
            assertThatThrownBy(() -> orderService.purchaseConfirm(orderId))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ALREADY_PROCESSED);
        }

        @Test
        @DisplayName("실패: 취소/반품된 주문은 구매 확정 불가")
        void fail_CanceledOrReturned() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.RETURN_COMPLETED)
                    .build();

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // When & Then
            assertThatThrownBy(() -> orderService.purchaseConfirm(orderId))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ALREADY_PROCESSED);
        }
    }

    @Nested
    @DisplayName("7. 주문 취소 (CancelOrder) - 추가 케이스")
    class CancelOrderExtendedTest {

        @Test
        @DisplayName("성공: 결제 대기(PAYMENT_WAITING) 상태 취소 - 단순 재고 선점 해제")
        void cancel_PaymentWaiting() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .userId(100L)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .orderKey("key-123")
                    .pointDiscount(1000)
                    .build();
            // 아이템 세팅
            order.addOrderItem(OrderItem.builder().bookId(1L).quantity(1).build());

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // When
            orderService.cancelOrder(orderId);

            // Then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            // 선점 해제(release)가 호출되어야 함 (restore 아님)
            verify(bookClient).releaseHeldStock(anyList(), eq("key-123"));
            // 포인트 취소 호출 확인
            verify(memberClient).cancelPoint(eq(100L), eq(1000), eq(orderId));
        }

        @Test
        @DisplayName("실패: 배송 시작(DELIVERING)된 주문은 취소 불가")
        void fail_CannotCancel() {
            // Given
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId)
                    .deliveryStatus(DeliveryStatus.DELIVERING)
                    .build();

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // When & Then
            assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CANNOT_CANCEL_ORDER);
        }
    }
}