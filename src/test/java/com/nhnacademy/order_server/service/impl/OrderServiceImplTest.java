package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.*;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock private OrderRepository orderRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private WrapperRepository wrapperRepository;
    @Mock private DeliveryService deliveryService;
    @Mock private BookClient bookClient;
    @Mock private CouponClient couponClient;
    @Mock private MemberClient memberClient;
    @Mock private PaymentClient paymentClient;
    @Mock private CartClient cartClient;
    @Mock private PasswordEncoder passwordEncoder;

    private OrderCreateRequest request;
    private Wrapper mockWrapper;
    private BookInfoResponse mockBookInfo;
    private MemberGradeResponse mockGradeResponse;

    @BeforeEach
    void setUp() {
        // Mock 데이터 설정
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

        lenient().when(memberClient.getMemberGrade(anyLong())).thenReturn(mockGradeResponse);
        //lenient().when(bookClient.getBookInfoBatch(anyList())).thenReturn(List.of(mockBookInfo));
        lenient().when(wrapperRepository.findAllById(any())).thenReturn(List.of(mockWrapper));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
    }

    @Nested
    @DisplayName("1. 주문 생성")
    class CreateOrderTest {
        @Test
        @DisplayName("성공: 회원 주문")
        void success() {
            when(deliveryService.calculateDeliveryFee(anyInt(), anyString())).thenReturn(3000);
            ReflectionTestUtils.setField(request, "usedPoint", 1000);

            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 1L);
                return o;
            });

            OrderCreateResponse response = orderService.createOrder(request);

            assertThat(response.getOrderId()).isEqualTo(1L);
            verify(memberClient).reservePoint(100L, 1000);
            //verify(bookClient).holdStock(eq(1L), eq(2), anyString());
        }

        // ... (실패 케이스들 생략 가능하지만, 포함하는 것이 안전함) ...
    }

    @Nested
    @DisplayName("2. 결제 완료")
    class PaymentSuccessTest {
        @Test
        @DisplayName("성공: 정상 처리")
        void success() {
            Order mockOrder = Order.builder().build();
            ReflectionTestUtils.setField(mockOrder, "deliveryStatus", DeliveryStatus.PENDING);
            ReflectionTestUtils.setField(mockOrder, "userId", 100L);
            ReflectionTestUtils.setField(mockOrder, "pointDiscount", 1000);
            OrderItem item = OrderItem.builder().build();
            ReflectionTestUtils.setField(item, "bookId", 1L);
            mockOrder.addOrderItem(item);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));

            orderService.paymentSuccess(1L, "pay_key");

            assertThat(mockOrder.getDeliveryStatus()).isEqualTo(DeliveryStatus.WAITING);
           // verify(bookClient).confirmStockDeduction(anyList());
            verify(memberClient).confirmPoint(100L, 1000);
        }
    }

    @Nested
    @DisplayName("3. 결제 검증 정보 조회")
    class GetValidationInfoTest {
        @Test
        @DisplayName("성공: 주문키로 정보 조회")
        void success() {
            // Given
            String orderKey = "uuid-key-123"; // [수정] String 타입
            Order mockOrder = Order.builder().build();
            ReflectionTestUtils.setField(mockOrder, "id", 1L);
            ReflectionTestUtils.setField(mockOrder, "paymentAmount", 30000);
            ReflectionTestUtils.setField(mockOrder, "orderKey", orderKey);
            ReflectionTestUtils.setField(mockOrder, "userId", 100L);
            ReflectionTestUtils.setField(mockOrder, "pointDiscount", 1000);

            // [수정] String 타입 orderKey로 조회
            when(orderRepository.findByOrderKey(orderKey)).thenReturn(Optional.of(mockOrder));

            // When
            OrderValidationInfoResponse response = orderService.getValidationInfo(orderKey);

            // Then
            assertThat(response.getPaymentAmount()).isEqualTo(30000);
            assertThat(response.getOrderKey()).isEqualTo(orderKey);
            assertThat(response.getUsedPoint()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("4. 주문 조회 (List, Detail, Guest)")
    class OrderReadTest {
        @Test
        @DisplayName("성공: 회원 주문 목록 조회")
        void getMyOrders_Success() {
            Long userId = 100L;
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Order.builder().build();
            ReflectionTestUtils.setField(order, "id", 1L);
            ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.PENDING);
            ReflectionTestUtils.setField(order, "paymentAmount", 20000);
            OrderItem item = OrderItem.builder().build();
            ReflectionTestUtils.setField(item, "bookTitle", "Test Book");
            ReflectionTestUtils.setField(item, "quantity", 1);
            ReflectionTestUtils.setField(item, "unitPrice", 20000);
            order.addOrderItem(item);

            when(orderRepository.findAllByUserId(userId, pageable)).thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderResponse> result = orderService.getMyOrders(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getItems().get(0).getBookTitle()).isEqualTo("Test Book");
        }

        @Test
        @DisplayName("성공: 주문 상세 조회")
        void getOrderDetail_Success() {
            Long orderId = 1L;
            Order order = Order.builder().build();
            ReflectionTestUtils.setField(order, "id", orderId);
            ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.DELIVERING);

            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrderDetail(orderId);

            assertThat(response.getOrderId()).isEqualTo(orderId);
            assertThat(response.getStatus()).isEqualTo("DELIVERING");
        }

        @Test
        @DisplayName("성공: 비회원 주문 조회")
        void getGuestOrder_Success() {
            Long orderId = 1L;
            Integer password = 1234;
            Order order = Order.builder().build();
            ReflectionTestUtils.setField(order, "id", orderId);
            ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.COMPLETED);

            when(orderRepository.findByIdAndOrderPassword(orderId, password)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getGuestOrder(orderId, password);

            assertThat(response.getOrderId()).isEqualTo(orderId);
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
        }
    }
}