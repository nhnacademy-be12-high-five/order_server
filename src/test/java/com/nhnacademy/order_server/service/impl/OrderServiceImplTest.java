package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock private CartClient cartClient;

    private OrderCreateRequest request;
    private Wrapper mockWrapper;
    private BookInfoResponse mockBookInfo;
    private MemberGradeResponse mockGradeResponse;

    @BeforeEach
    void setUp() {
        // 1. Wrapper Mock
        mockWrapper = new Wrapper("선물 포장", 1000, true);
        ReflectionTestUtils.setField(mockWrapper, "id", 1L);

        // 2. BookInfo Mock
        mockBookInfo = new BookInfoResponse();
        ReflectionTestUtils.setField(mockBookInfo, "bookId", 1L);
        ReflectionTestUtils.setField(mockBookInfo, "price", 15000);
        ReflectionTestUtils.setField(mockBookInfo, "title", "자바의 정석");

        // 3. MemberGrade Mock (중요: earnRate 설정 필수!)
        mockGradeResponse = new MemberGradeResponse();
        ReflectionTestUtils.setField(mockGradeResponse, "earnRate", 0.05);

        // 4. Request 생성
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

        // [핵심] 공통 Mocking 설정 (모든 테스트에 기본 적용됨)
        // lenient()는 해당 Stub이 사용되지 않는 테스트 케이스가 있어도 에러를 내지 않게 함
        lenient().when(memberClient.getMemberGrade(anyLong())).thenReturn(mockGradeResponse);
        lenient().when(bookClient.getBookInfo(anyLong())).thenReturn(mockBookInfo);
        lenient().when(wrapperRepository.findAllById(any())).thenReturn(List.of(mockWrapper));
    }

    @Test
    @DisplayName("[성공] 회원 주문 생성 - 정상 흐름")
    void createOrder_Success() {
        // Given
        // 추가 Mocking (배송비, 쿠폰, 포인트)
        when(deliveryService.calculateDeliveryFee(anyInt(), anyString())).thenReturn(3000);

        ReflectionTestUtils.setField(request, "couponId", 10L);
        when(couponClient.calculateDiscount(anyLong(), anyInt())).thenReturn(5000);

        ReflectionTestUtils.setField(request, "usedPoint", 1000);
        when(memberClient.getPointBalance(anyLong())).thenReturn(10000);

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 1L);
            return o;
        });

        // When
        OrderCreateResponse response = orderService.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualTo(29000); // (15000*2 + 1000*2 + 3000) - 5000 - 1000
    }

    @Test
    @DisplayName("[실패] 비회원 주문 시 비밀번호 누락")
    void createOrder_Fail_GuestPasswordMissing() {
        ReflectionTestUtils.setField(request, "userId", null);
        ReflectionTestUtils.setField(request, "orderPassword", null);

        OrderException ex = assertThrows(OrderException.class, () -> orderService.createOrder(request));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
    }

    @Test
    @DisplayName("[실패] 존재하지 않는 포장지 ID 요청")
    void createOrder_Fail_WrapperNotFound() {
        // Given
        // DB에서 포장지를 못 찾음 (setUp의 설정을 덮어씌움)
        when(wrapperRepository.findAllById(any())).thenReturn(Collections.emptyList());

        // When & Then
        OrderException ex = assertThrows(OrderException.class, () -> orderService.createOrder(request));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.WRAPPER_NOT_FOUND);
    }

    @Test
    @DisplayName("[실패] 포인트 잔액 부족")
    void createOrder_Fail_NotEnoughPoint() {
        // Given
        ReflectionTestUtils.setField(request, "usedPoint", 10000);
        // 잔액 부족 설정
        when(memberClient.getPointBalance(anyLong())).thenReturn(5000);

        // When & Then
        OrderException ex = assertThrows(OrderException.class, () -> orderService.createOrder(request));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.NOT_ENOUGH_POINT);
    }

    @Test
    @DisplayName("[실패] 도서 재고 부족")
    void createOrder_Fail_OutOfStock() {
        // Given
        // 재고 가차감 시 예외 발생 설정
        doThrow(new OrderException(OrderErrorCode.OUT_OF_STOCK)).when(bookClient).holdStock(anyLong(), anyInt());

        // When & Then
        OrderException ex = assertThrows(OrderException.class, () -> orderService.createOrder(request));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("[성공] 장바구니 비우기 실패해도 주문은 성공")
    void createOrder_Success_EvenIfCartFails() {
        // Given
        when(orderRepository.save(any(Order.class))).thenReturn(Order.builder().id(1L).build());
        // 장바구니 에러 설정
        doThrow(new RuntimeException("Cart Error")).when(cartClient).clearCartByUserId(anyLong());

        // When
        OrderCreateResponse response = orderService.createOrder(request);

        // Then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("[성공] 결제 완료 처리")
    void paymentSuccess_Success() {
        // Given
        Order mockOrder = Order.builder().build();
        ReflectionTestUtils.setField(mockOrder, "deliveryStatus", DeliveryStatus.PENDING);
        ReflectionTestUtils.setField(mockOrder, "couponId", 10L);
        ReflectionTestUtils.setField(mockOrder, "couponDiscount", 5000);
        ReflectionTestUtils.setField(mockOrder, "pointDiscount", 1000);
        ReflectionTestUtils.setField(mockOrder, "userId", 100L);

        OrderItem item = OrderItem.builder().build();
        ReflectionTestUtils.setField(item, "bookId", 1L);
        mockOrder.addOrderItem(item);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));

        // When
        orderService.paymentSuccess(1L, "key");

        // Then
        assertThat(mockOrder.getDeliveryStatus()).isEqualTo(DeliveryStatus.WAITING);
        verify(bookClient).confirmStockDeduction(anyList());
    }
}