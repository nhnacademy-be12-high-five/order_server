package com.nhnacademy.order_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import com.nhnacademy.order_server.service.OrderService;
import com.nhnacademy.order_server.service.WrapperService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false) // 시큐리티 필터 비활성화
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private WrapperService wrapperService;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private DeliveryPolicyService deliveryPolicyService;

    // 1. 주문 생성 테스트
    @Test
    @DisplayName("[POST] 주문 생성 성공 (201 Created)")
    void createOrder() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest();
        ReflectionTestUtils.setField(request, "userId", 1L);
        ReflectionTestUtils.setField(request, "receiverName", "홍길동");
        ReflectionTestUtils.setField(request, "receiverAddress", "서울시 강남구");

        OrderItemRequest item = new OrderItemRequest();
        ReflectionTestUtils.setField(item, "bookId", 101L);
        ReflectionTestUtils.setField(item, "quantity", 2);
        ReflectionTestUtils.setField(request, "orderItems", List.of(item));

        OrderCreateResponse response = OrderCreateResponse.builder()
                .orderId(1L)
                .orderKey("test-uuid-1234")
                .totalAmount(30000)
                .build();

        given(orderService.createOrder(any(OrderCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andDo(print());
    }

    // 2. 결제 검증 정보 조회 테스트
    @Test
    @DisplayName("[GET] 결제 검증 정보 조회 성공 (200 OK)")
    void getPaymentInfo() throws Exception {
        String orderKey = "test-uuid-1234";
        OrderValidationInfoResponse response = OrderValidationInfoResponse.builder()
                .orderId(1L)
                .paymentAmount(30000)
                .orderKey(orderKey)
                .userId(100L)
                .usedPoint(1000)
                .build();

        given(orderService.getValidationInfo(orderKey)).willReturn(response);

        mockMvc.perform(get("/api/orders/{orderKey}/payments", orderKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderKey").value(orderKey))
                .andDo(print());
    }

    // 3. 회원 주문 목록 조회 테스트
    @Test
    @DisplayName("[GET] 내 주문 목록 조회 (200 OK)")
    void getMyOrders() throws Exception {
        Long userId = 100L;
        OrderResponse orderRes = OrderResponse.builder()
                .id(1L)
                .orderDate(LocalDateTime.now())
                .status(DeliveryStatus.PAYMENT_WAITING.name())
                .totalPrice(15000)
                .build();

        Page<OrderResponse> pageResponse = new PageImpl<>(List.of(orderRes));

        given(orderService.getMyOrders(eq(userId), any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/orders")
                        .header("X-USER-ID", userId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andDo(print());
    }

    // 4. 주문 상세 조회 테스트
    @Test
    @DisplayName("[GET] 주문 상세 조회 (200 OK)")
    void getOrderDetail() throws Exception {
        Long orderId = 1L;
        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .status(DeliveryStatus.DELIVERING.name())
                .totalPrice(20000)
                .build();

        given(orderService.getOrderDetail(orderId)).willReturn(response);


        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andDo(print());
    }

    // 5. [추가] 최근 3개월 주문 조회 테스트
    @Test
    @DisplayName("[GET] 최근 3개월 주문 조회 (200 OK)")
    void getRecentOrders() throws Exception {
        Long userId = 100L;
        OrderResponse orderRes = OrderResponse.builder()
                .id(2L)
                .status(DeliveryStatus.DELIVERY_COMPLETED.name())
                .totalPrice(50000)
                .build();

        Page<OrderResponse> pageResponse = new PageImpl<>(List.of(orderRes));

        given(orderService.getMyOrdersLast3Months(eq(userId), any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/orders/recent")
                        .header("X-USER-ID", userId)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2L))
                .andDo(print());
    }

    // 6. 비회원 주문 조회 테스트
    @Test
    @DisplayName("[POST] 비회원 주문 조회 (200 OK)")
    void getGuestOrder() throws Exception {
        OrderGuestLoginRequest request = new OrderGuestLoginRequest();
        ReflectionTestUtils.setField(request, "orderId", 1L);
        ReflectionTestUtils.setField(request, "password", "1234");

        GuestOrderDetailResponse response = GuestOrderDetailResponse.builder()
                .orderId(1L)
                .orderNumber("20241225-0001")
                .statusName(DeliveryStatus.DELIVERY_COMPLETED.name())
                .receiverName("홍길동")
                .totalAmount(10000L)
                .build();

        given(orderService.getGuestOrder(1L, "1234")).willReturn(response);


        mockMvc.perform(post("/api/orders/guests/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andDo(print());
    }

    // 7. [추가] 포장지 목록 조회 테스트
    @Test
    @DisplayName("[GET] 포장지 목록 조회 (200 OK)")
    void getWrappers() throws Exception {
        WrapperResponse wrapper = WrapperResponse.builder()
                .id(1L)
                .name("Red Paper")
                .price(1000)
                .build();

        given(wrapperService.getAvailableWrappers()).willReturn(List.of(wrapper));

        mockMvc.perform(get("/api/orders/wrappers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Red Paper"))
                .andDo(print());
    }

    // 8. [추가] 현재 배송 정책 조회 테스트
    @Test
    @DisplayName("[GET] 현재 배송 정책 조회 (200 OK)")
    void getCurrentDeliveryPolicy() throws Exception {
        DeliveryPolicyResponse response = DeliveryPolicyResponse.builder()
                .id(1L)
                .standardShippingFee(3000)      // 기본 배송비
                .minOrderAmount(50000)          // 무료 배송 기준
                .isActive(true)
                .effectiveDate(LocalDateTime.now())
                .remoteAreaSurcharge(5000)      // 도서산간 추가 비용
                .build();

        given(deliveryPolicyService.getActivePolicy()).willReturn(response);

        mockMvc.perform(get("/api/orders/policy/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standardShippingFee").value(3000))
                .andExpect(jsonPath("$.minOrderAmount").value(50000))
                .andExpect(jsonPath("$.remoteAreaSurcharge").value(5000))
                .andDo(print());
    }

    // 9. [추가] 주문 취소 테스트
    @Test
    @DisplayName("[POST] 주문 취소 (200 OK)")
    void cancelOrder() throws Exception {
        Long orderId = 1L;
        doNothing().when(orderService).cancelOrder(orderId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId))
                .andExpect(status().isOk())
                .andDo(print());
    }

    // 10. [추가] 주문 확정 테스트
    @Test
    @DisplayName("[POST] 주문 구매 확정 (200 OK)")
    void confirmOrder() throws Exception {
        Long orderId = 1L;
        doNothing().when(orderService).purchaseConfirm(orderId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId))
                .andExpect(status().isOk())
                .andDo(print());
    }

    // 11. [추가] 도서 구매 여부 확인 테스트
    @Test
    @DisplayName("[GET] 도서 구매 여부 확인 (200 OK)")
    void hasPurchasedBook() throws Exception {
        Long memberId = 100L;
        Long bookId = 50L;

        given(orderService.hasPurchasedBook(memberId, bookId)).willReturn(true);

        mockMvc.perform(get("/api/orders/check-purchase")
                        .param("memberId", String.valueOf(memberId))
                        .param("bookId", String.valueOf(bookId)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"))
                .andDo(print());
    }
}