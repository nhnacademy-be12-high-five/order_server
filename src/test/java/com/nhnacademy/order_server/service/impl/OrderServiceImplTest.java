package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.OrderCalculationData;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.CouponCalculationRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.PointTransactionRequest;
import com.nhnacademy.order_server.dto.response.CouponCalculationResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock private OrderCreateService orderCreateService;
    @Mock private OrderCancelService orderCancelService;
    @Mock private OrderRepository orderRepository;
    @Mock private WrapperRepository wrapperRepository;
    @Mock private DeliveryService deliveryService;
    @Mock private BookClient bookClient;
    @Mock private CouponClient couponClient;
    @Mock private MemberClient memberClient;
    @Mock private PaymentClient paymentClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PasswordEncoder passwordEncoder;

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
    }

    @Nested
    @DisplayName("1. 주문 생성 (CreateOrder)")
    class CreateOrderTest {

        @Test
        @DisplayName("성공: 데이터 준비 후 OrderCreateService 호출")
        void success_Member() {
            ReflectionTestUtils.setField(request, "couponId", 10L);

            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));
            given(deliveryService.calculateDeliveryFee(anyInt(), anyString())).willReturn(3000);

            CouponCalculationResponse couponRes = new CouponCalculationResponse(2000L, 28000L);
            given(couponClient.calculateCoupon(anyLong(), any(CouponCalculationRequest.class))).willReturn(couponRes);

            OrderCreateResponse expectedResponse = OrderCreateResponse.builder()
                    .orderId(1L).orderName("자바의 정석 외 1권").totalAmount(30000).build();

            given(orderCreateService.createOrderInTransaction(
                    eq(request), anyString(), any(OrderCalculationData.class), any(OrderCreateRequest.OrderCalculationResult.class)))
                    .willReturn(expectedResponse);

            OrderCreateResponse response = orderService.createOrder(request);

            assertThat(response).isEqualTo(expectedResponse);
            verify(bookClient).holdStockBatch(anyList(), anyString());
            verify(orderCreateService).createOrderInTransaction(any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("실패: OrderCreateService 실패 시 보상 트랜잭션 실행")
        void fail_CompensateTransaction() {
            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));

            willThrow(new RuntimeException("DB Error"))
                    .given(orderCreateService).createOrderInTransaction(any(), anyString(), any(), any());

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class);

            verify(bookClient).releaseHeldStock(anyList(), anyString());
            verify(memberClient).cancelPoint(argThat(req ->
                    req.getMemberId().equals(100L) &&
                            req.getAmount() == 1000 &&
                            req.getOrderId() == 0L
            ));
        }

        @Test
        @DisplayName("분기 커버: 보상 트랜잭션 중 예외 발생 시 무시하고 진행 (try-catch)")
        void fail_CompensateTransaction_ExceptionIgnored() {
            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));

            willThrow(new RuntimeException("Main Logic Error"))
                    .given(orderCreateService).createOrderInTransaction(any(), anyString(), any(), any());

            // 보상 트랜잭션 메서드들도 에러를 던지도록 설정 (catch 블록 테스트)
            willThrow(new RuntimeException("Compensate Error"))
                    .given(memberClient).cancelPoint(any(PointTransactionRequest.class));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Main Logic Error"); // 메인 에러가 던져져야 함
        }

        @Test
        @DisplayName("예외: 쿠폰 서버 오류 시 OrderException 발생")
        void fail_CouponServiceError() {
            ReflectionTestUtils.setField(request, "couponId", 10L);
            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));

            willThrow(new RuntimeException("Coupon Error"))
                    .given(couponClient).calculateCoupon(anyLong(), any());

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.COUPON_SERVICE_ERROR);
        }

        @Test
        @DisplayName("분기 커버: 회원 등급 조회 실패 시 기본 등급(0.0) 적용")
        void fail_MemberGradeServiceError() {
            given(memberClient.getMemberGrade(anyLong())).willThrow(new RuntimeException("Member Error"));
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            given(wrapperRepository.findAllById(any())).willReturn(List.of(mockWrapper));
            // 나머지 Mock 설정...
            given(orderCreateService.createOrderInTransaction(any(), anyString(), any(), any()))
                    .willReturn(OrderCreateResponse.builder().build());

            assertThatCode(() -> orderService.createOrder(request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("분기 커버: 포장지 정보 없음 (Empty Wrapper Map)")
        void success_NoWrapper() {
            OrderCreateRequest.OrderItemRequest noWrapItem = new OrderCreateRequest.OrderItemRequest();
            ReflectionTestUtils.setField(noWrapItem, "bookId", 1L);
            ReflectionTestUtils.setField(noWrapItem, "quantity", 1);
            // wrapperId null

            ReflectionTestUtils.setField(request, "orderItems", List.of(noWrapItem));

            given(memberClient.getMemberGrade(anyLong())).willReturn(mockGradeResponse);
            given(bookClient.getBooksBulk(anyList())).willReturn(ResponseEntity.ok(List.of(mockBookInfo)));
            // WrapperRepo 호출 안됨 검증

            given(orderCreateService.createOrderInTransaction(any(), anyString(), any(), any()))
                    .willReturn(OrderCreateResponse.builder().build());

            orderService.createOrder(request);

            verify(wrapperRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("2. 결제 완료 메시지 처리 (ProcessPaymentSuccess)")
    class ProcessPaymentSuccessMessageTest {
        @Test
        @DisplayName("성공: PAYMENT_WAITING -> PREPARING 변경")
        void success() {
            Long orderId = 1L;
            Order order = Order.builder()
                    .id(orderId).userId(100L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .paymentAmount(30000).orderKey("key").build();
            order.addOrderItem(OrderItem.builder().bookId(101L).quantity(1).build());
            ReflectionTestUtils.setField(order, "couponId", 10L);
            ReflectionTestUtils.setField(order, "pointDiscount", 1000);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            PaymentSuccessMessage message = PaymentSuccessMessage.builder().orderId(orderId).paymentKey("pg").totalAmount(30000L).build();
            orderService.processPaymentSuccessMessage(message);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PREPARING);
            verify(couponClient).useCoupon(eq(100L), any());
        }

        @Test
        @DisplayName("무시: 결제 대기 상태가 아니면 무시")
        void ignore_NotWaiting() {
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.CANCELED).build();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            PaymentSuccessMessage msg = PaymentSuccessMessage.builder().orderId(1L).build();
            orderService.processPaymentSuccessMessage(msg);

            verify(couponClient, never()).useCoupon(any(), any());
        }

        @Test
        @DisplayName("실패: 주문 금액 불일치")
        void fail_AmountMismatch() {
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).paymentAmount(50000).build();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            PaymentSuccessMessage msg = PaymentSuccessMessage.builder().orderId(1L).totalAmount(10000L).build();

            assertThatThrownBy(() -> orderService.processPaymentSuccessMessage(msg))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("3. 단순 위임 메서드 & 조회 (Read)")
    class ReadAndDelegateTest {
        @Test
        @DisplayName("cancelOrder 위임 확인")
        void cancelOrder() {
            orderService.cancelOrder(1L);
            verify(orderCancelService).cancelOrderTransactional(1L);
        }

        @Test
        @DisplayName("getMyOrders 조회")
        void getMyOrders() {
            given(orderRepository.findAllByUserId(anyLong(), any())).willReturn(new PageImpl<>(List.of()));
            orderService.getMyOrders(1L, PageRequest.of(0, 10));
            verify(orderRepository).findAllByUserId(anyLong(), any());
        }

        @Test
        @DisplayName("getOrderDetail 조회")
        void getOrderDetail() {
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).build();
            order.addOrderItem(OrderItem.builder().bookId(1L).quantity(1).build());
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            orderService.getOrderDetail(1L);
            verify(orderRepository).findByIdWithItems(1L);
        }

        @Test
        @DisplayName("getGuestOrder 성공")
        void getGuestOrder_Success() {
            Order order = Order.builder()
                    .id(1L)
                    .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                    .build();

            ReflectionTestUtils.setField(order, "orderPassword", "encodedPwd");

            order.addOrderItem(OrderItem.builder()
                    .bookId(1L)
                    .quantity(1)
                    .unitPrice(15000)
                    .build());

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));
            given(passwordEncoder.matches("rawPwd", "encodedPwd")).willReturn(true);

            assertThatCode(() -> orderService.getGuestOrder(1L, "rawPwd")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getGuestOrder 실패: 비밀번호 불일치")
        void getGuestOrder_Fail_Password() {
            Order order = Order.builder().id(1L).build();
            ReflectionTestUtils.setField(order, "orderPassword", "encodedPwd");
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));
            given(passwordEncoder.matches("wrong", "encodedPwd")).willReturn(false);

            assertThatThrownBy(() -> orderService.getGuestOrder(1L, "wrong"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("getOrderAggregations 조회")
        void getOrderAggregations() {
            orderService.getOrderAggregations(LocalDateTime.now(), LocalDateTime.now());
            verify(orderRepository).findOrderAggregations(any(), any());
        }

        @Test
        @DisplayName("getTotalPaymentAmount 조회")
        void getTotalPaymentAmount() {
            given(orderRepository.sumPaymentAmountByUserId(anyLong(), any())).willReturn(10000L);
            Long total = orderService.getTotalPaymentAmount(1L, LocalDateTime.now());
            assertThat(total).isEqualTo(10000L);
        }

        @Test
        @DisplayName("getTotalPaymentAmount 조회 - 결과 null일 때 0 반환")
        void getTotalPaymentAmount_Null() {
            given(orderRepository.sumPaymentAmountByUserId(anyLong(), any())).willReturn(null);
            Long total = orderService.getTotalPaymentAmount(1L, LocalDateTime.now());
            assertThat(total).isZero();
        }

        @Test
        @DisplayName("getMyOrdersLast3Months 조회")
        void getMyOrdersLast3Months() {
            given(orderRepository.findByUserIdAndOrderDateAfter(anyLong(), any(), any())).willReturn(new PageImpl<>(List.of()));
            orderService.getMyOrdersLast3Months(1L, PageRequest.of(0, 10));
            verify(orderRepository).findByUserIdAndOrderDateAfter(anyLong(), any(), any());
        }

        @Test
        @DisplayName("getValidationInfo 조회")
        void getValidationInfo() {
            Order order = Order.builder().id(1L).paymentAmount(1000).orderKey("key").build();
            given(orderRepository.findByOrderKey("key")).willReturn(Optional.of(order));

            OrderValidationInfoResponse info = orderService.getValidationInfo("key");
            assertThat(info.getOrderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("hasPurchasedBook 조회")
        void hasPurchasedBook() {
            orderService.hasPurchasedBook(1L, 10L);
            verify(orderRepository).hasPurchasedBook(1L, 10L);
        }
    }

    @Nested
    @DisplayName("4. 스케줄러 & 상태 변경 로직 (Batch/Status)")
    class BatchAndStatusTest {
        @Test
        @DisplayName("autoCompleteDelivery: 배송 중 -> 배송 완료")
        void autoCompleteDelivery() {
            Order order = Order.builder().deliveryStatus(DeliveryStatus.DELIVERING).build();
            Delivery delivery = mock(Delivery.class);
            ReflectionTestUtils.setField(order, "delivery", delivery);

            given(orderRepository.findByDeliveryStatusAndDelivery_ActualShipDateBefore(eq(DeliveryStatus.DELIVERING), any()))
                    .willReturn(List.of(order));

            orderService.autoCompleteDelivery();

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
            verify(delivery).completeDelivery();
        }

        @Test
        @DisplayName("autoConfirmPurchase: 배송 완료 -> 구매 확정")
        void autoConfirmPurchase() {
            Long orderId = 1L;
            Order order = Order.builder().id(orderId).userId(100L).paymentAmount(1000)
                    .deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED).build();
            Delivery delivery = Delivery.builder().order(order).build();
            delivery.completeDelivery();
            ReflectionTestUtils.setField(order, "delivery", delivery);

            given(orderRepository.findByDeliveryStatusAndDelivery_ActualCompletionDateBefore(eq(DeliveryStatus.DELIVERY_COMPLETED), any()))
                    .willReturn(List.of(order));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            orderService.autoConfirmPurchase();

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
            verify(rabbitTemplate).convertAndSend(eq("point-queue"), any(com.nhnacademy.order_server.dto.request.PointEarnRequest.class));
        }

        @Test
        @DisplayName("cancelExpiredOrders: 결제 대기 만료 -> 취소")
        void cancelExpiredOrders() {
            Order order = Order.builder().id(1L).userId(100L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).orderKey("key").build();
            order.addOrderItem(OrderItem.builder().bookId(1L).quantity(1).build());

            given(orderRepository.findByDeliveryStatusAndOrderDateBefore(eq(DeliveryStatus.PAYMENT_WAITING), any()))
                    .willReturn(List.of(order));

            orderService.cancelExpiredOrders();

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            verify(bookClient).releaseHeldStock(anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("5. 구매 확정 (PurchaseConfirm) 엣지 케이스")
    class PurchaseConfirmEdgeTest {
        @Test
        @DisplayName("실패: 이미 취소/반품된 주문")
        void fail_AlreadyCanceled() {
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.CANCELED).build();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.purchaseConfirm(1L))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ALREADY_PROCESSED);
        }

        @Test
        @DisplayName("성공: 배송 정보가 없어도 확정 가능 (null safety)")
        void success_NoDeliveryInfo() {
            Order order = Order.builder().id(1L).userId(100L).paymentAmount(1000).deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED).build();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            orderService.purchaseConfirm(1L);
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
        }
    }

    @Nested
    @DisplayName("6. 대량 조회 (Bulk Query)")
    class BulkQueryTest {

        @Test
        @DisplayName("성공: 여러 회원의 주문 총액 조회")
        void getBulkTotalAmounts_Success() {
            // Given
            List<Long> userIds = List.of(1L, 2L, 3L);
            LocalDateTime since = LocalDateTime.now().minusMonths(3);

            List<Object[]> mockResults = List.of(
                    new Object[]{1L, 150000L},
                    new Object[]{2L, 200000L},
                    new Object[]{3L, 50000L}
            );

            given(orderRepository.sumPaymentAmountByUserIds((userIds), (since)))
                    .willReturn(mockResults);

            // When
            Map<Long, Long> result = orderService.getBulkTotalAmounts(userIds, since);

            // Then
            assertThat(result)
                    .hasSize(3)
                    .containsEntry(1L, 150000L)
                    .containsEntry(2L, 200000L)
                    .containsEntry(3L, 50000L);
        }

        @Test
        @DisplayName("성공: 빈 리스트 요청 시 빈 맵 반환 (Repository 호출 안 함)")
        void getBulkTotalAmounts_EmptyInput() {
            // When
            Map<Long, Long> result = orderService.getBulkTotalAmounts(Collections.emptyList(), LocalDateTime.now());

            // Then
            assertThat(result).isEmpty();
            // 빈 리스트일 때 리포지토리를 호출하지 않는지 검증
            verify(orderRepository, never()).sumPaymentAmountByUserIds(any(), any());
        }

        @Test
        @DisplayName("성공: 결과가 없는 경우 빈 맵 반환")
        void getBulkTotalAmounts_NoResult() {
            // Given
            List<Long> userIds = List.of(99L);
            given(orderRepository.sumPaymentAmountByUserIds(anyList(), any()))
                    .willReturn(Collections.emptyList());

            // When
            Map<Long, Long> result = orderService.getBulkTotalAmounts(userIds, LocalDateTime.now());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("안전성: 결과에 NULL이 포함된 경우 필터링")
        void getBulkTotalAmounts_FilterNulls() {
            // Given
            List<Long> userIds = List.of(1L, 2L);
            List<Object[]> mockResults = List.of(
                    new Object[]{1L, 10000L},
                    new Object[]{null, 20000L},
                    new Object[]{2L, null}
            );

            given(orderRepository.sumPaymentAmountByUserIds(anyList(), any()))
                    .willReturn(mockResults);

            Map<Long, Long> result = orderService.getBulkTotalAmounts(userIds, LocalDateTime.now());

            assertThat(result)
                    .hasSize(1)
                    .containsEntry(1L, 10000L);

        }

        @Test
        @DisplayName("안전성: 타입 캐스팅 (Integer -> Long 변환 지원)")
        void getBulkTotalAmounts_TypeCasting() {
            // DB 드라이버에 따라 숫자가 Integer로 올 수도 있음

            List<Long> userIds = List.of(1L);

            List<Object[]> mockResults = Collections.singletonList(
                    new Object[]{1L, 100}
            );

            given(orderRepository.sumPaymentAmountByUserIds(anyList(), any()))
                    .willReturn(mockResults);

            // When
            Map<Long, Long> result = orderService.getBulkTotalAmounts(userIds, LocalDateTime.now());

            // Then
            assertThat(result)
                    .containsEntry(1L, 100L);

        }
    }
}